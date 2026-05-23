package cm.agriprix.messages.controller;

import cm.agriprix.messages.model.Message;
import cm.agriprix.messages.repository.MessageRepository;
import cm.agriprix.model.Utilisateur;
import cm.agriprix.repository.UtilisateurRepository;
import cm.agriprix.service.UtilisateurService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/messages")
public class MessageController {

    @Autowired private MessageRepository messageRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private UtilisateurService utilisateurService;

    /** Liste des conversations */
    @GetMapping
    public String index(Model model, Principal principal) {
        Utilisateur moi = utilisateurService.getCurrentUser(principal.getName());

        // Récupérer tous les utilisateurs avec qui j'ai échangé
        List<Utilisateur> interlocuteurs = messageRepository.findInterlocuteurs(moi);

        // Nombre de messages non lus
        long nonLus = messageRepository.countByDestinataireAndLuFalse(moi);

        // Tous les utilisateurs (sauf moi) pour démarrer une conversation
        List<Utilisateur> tousUtilisateurs = utilisateurRepository.findAll()
                .stream().filter(u -> !u.getId().equals(moi.getId())).toList();
        utilisateurRepository.findAll().stream()
                .filter(u -> u.getRole() == Utilisateur.Role.ADMIN && !u.getId().equals(moi.getId()))
                .findFirst()
                .ifPresent(admin -> model.addAttribute("adminUser", admin));

        model.addAttribute("interlocuteurs", interlocuteurs);
        model.addAttribute("nonLus", nonLus);
        model.addAttribute("moi", moi);
        model.addAttribute("tousUtilisateurs", tousUtilisateurs);

        return "messages/index";
    }

    /** Conversation avec un utilisateur spécifique */
    @GetMapping("/{userId}")
    public String conversation(@PathVariable Long userId, Model model, Principal principal) {
        Utilisateur moi = utilisateurService.getCurrentUser(principal.getName());
        Utilisateur interlocuteur = utilisateurRepository.findById(userId).orElse(null);
        if (interlocuteur == null || !peutContacter(moi, interlocuteur)) return "error";

        // Charger la conversation
        List<Message> messages = messageRepository.findConversation(moi, interlocuteur);

        // Marquer comme lus les messages reçus
        messages.stream()
                .filter(m -> m.getDestinataire().getId().equals(moi.getId()) && !m.isLu())
                .forEach(m -> { m.setLu(true); messageRepository.save(m); });

        model.addAttribute("messages", messages);
        model.addAttribute("interlocuteur", interlocuteur);
        model.addAttribute("moi", moi);

        model.addAttribute("tousUtilisateurs", utilisateurRepository.findAll()
                .stream().filter(u -> !u.getId().equals(moi.getId())).toList());

        return "messages/conversation";
    }

    /** Envoyer un message */
    @PostMapping("/envoyer")
    public String envoyer(@RequestParam Long destinataireId,
                          @RequestParam String contenu,
                          Principal principal,
                          RedirectAttributes redirectAttributes) {
        Utilisateur moi = utilisateurService.getCurrentUser(principal.getName());
        Utilisateur destinataire = utilisateurRepository.findById(destinataireId).orElse(null);

        if  (destinataire == null || contenu.trim().isEmpty() || !peutContacter(moi, destinataire)) {
            redirectAttributes.addFlashAttribute("erreur", "Message invalide.");
            return "redirect:/messages";
        }

        messageRepository.save(new Message(moi, destinataire, contenu.trim()));
        return "redirect:/messages/" + destinataireId;
    }

    /** Nouveau message (démarrer une conversation) */
    @GetMapping("/nouveau/{userId}")
    public String nouveau(@PathVariable Long userId, Model model, Principal principal) {
        Utilisateur moi = utilisateurService.getCurrentUser (principal.getName());
        Utilisateur destinataire = utilisateurRepository.findById(userId).orElse(null);
        if (destinataire == null || !peutContacter(moi, destinataire)) return "error";
        return   "redirect:/messages/" + userId;
    }

    /** Endpoint AJAX: conversation JSON */
    @GetMapping("/{userId}/api")
    @ResponseBody
    public ResponseEntity<?> conversationApi(@PathVariable Long userId, Principal principal) {
        Utilisateur moi = utilisateurService.getCurrentUser(principal.getName());
        Utilisateur interlocuteur = utilisateurRepository.findById(userId).orElse(null);
        if (interlocuteur == null || !peutContacter(moi, interlocuteur)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Acces refuse"));
        }

        List<Message> messages = messageRepository.findConversation(moi, interlocuteur);
        messages.stream()
                .filter(m -> m.getDestinataire().getId().equals(moi.getId()) && !m.isLu())
                .forEach(m -> { m.setLu(true); messageRepository.save(m); });

        List<Map<String, Object>> data = messages.stream().map(msg -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", msg.getId());
            row.put("contenu", msg.getContenu());
            row.put("dateEnvoi", msg.getDateEnvoi().toString());
            row.put("expediteurId", msg.getExpediteur().getId());
            row.put("destinataireId", msg.getDestinataire().getId());
            return row;
        }).toList();

        return ResponseEntity.ok(data);
    }

    /** Endpoint AJAX: envoi de message */
    @PostMapping("/api/envoyer")
    @ResponseBody
    public ResponseEntity<?> envoyerApi(@RequestParam Long destinataireId,
                                        @RequestParam String contenu,
                                        Principal principal) {
        Utilisateur moi = utilisateurService.getCurrentUser(principal.getName());
        Utilisateur destinataire = utilisateurRepository.findById(destinataireId).orElse(null);

        if (destinataire == null || contenu == null || contenu.trim().isEmpty() || !peutContacter(moi, destinataire)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message invalide"));
        }

        Message saved = messageRepository.save(new Message(moi, destinataire, contenu.trim()));
        return ResponseEntity.ok(Map.of("id", saved.getId(), "status", "ok"));
    }

    /** Endpoint AJAX: notifications non lues */
    @GetMapping("/api/notifications")
    @ResponseBody
    public ResponseEntity<?> notificationsApi(Principal principal) {
        Utilisateur moi = utilisateurService.getCurrentUser(principal.getName());

        long totalNonLus = messageRepository.countByDestinataireAndLuFalse(moi);
        List<Object[]> rows = messageRepository.countUnreadByExpediteur(moi);
        List<Map<String, Object>> byUser = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("userId", row[0]);
            item.put("count", row[1]);
            byUser.add(item);
        }

        return ResponseEntity.ok(Map.of(
                "totalUnread", totalNonLus,
                "byUser", byUser
        ));
    }

    private boolean peutContacter(Utilisateur moi, Utilisateur autre) {
        return !moi.getId().equals(autre.getId());
    }
}
