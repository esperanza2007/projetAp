package cm.agriprix.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ErrorPageController {

    @GetMapping("/acces-refuse")
    public String accesRefuse() {
        return "access-denied";
    }
}
