const zone = document.getElementById('messagesZone');
const form = document.getElementById('chatForm');
const input = document.getElementById('chatInput');
const submit = document.getElementById('chatSubmit');
const interlocuteurId = Number(window.chatConfig?.interlocuteurId || 0);
const moiId = Number(window.chatConfig?.moiId || 0);
let lastMessageId = 0;
let polling = null;

const escapeHtml = (text) => {
    const div = document.createElement('div');
    div.textContent = text || '';
    return div.innerHTML;
};

const formatDate = (isoDate) => {
    const d = new Date(isoDate);
    if (isNaN(d.getTime())) return '';
    return d.toLocaleString('fr-FR', {
        day: '2-digit',
        month: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
};

const renderMessages = (messages) => {
    if (!zone) return;
    if (!messages || messages.length === 0) {
        zone.innerHTML = '<div style="text-align:center;color:#aaa;margin:auto;font-size:14px;">Démarrez la conversation 👋</div>';
        lastMessageId = 0;
        return;
    }
    const wasNearBottom = zone.scrollHeight - zone.scrollTop - zone.clientHeight < 120;
    zone.innerHTML = messages.map((msg) => {
        const estMoi = msg.expediteurId === moiId;
        const wrapperStyle = estMoi ? 'align-self:flex-end;' : 'align-self:flex-start;';
        const bubbleStyle = estMoi
            ? 'background:#1a9e5f;color:white;'
            : 'background:white;color:#333;border:1px solid #e0e0e0;';
        return `<div style="${wrapperStyle}">
            <div style="${bubbleStyle}padding:10px 14px;border-radius:16px;max-width:280px;font-size:14px;line-height:1.5;word-wrap:break-word;">
                ${escapeHtml(msg.contenu)}
            </div>
            <div style="font-size:11px;color:#aaa;margin-top:3px;text-align:right;">
                ${formatDate(msg.dateEnvoi)}
            </div>
        </div>`;
    }).join('');
    lastMessageId = messages[messages.length - 1].id || 0;
    if (wasNearBottom) zone.scrollTop = zone.scrollHeight;
};

const fetchMessages = async () => {
    const response = await fetch(`/messages/${interlocuteurId}/api`, { headers: { 'Accept': 'application/json' } });
    if (!response.ok) return;
    const messages = await response.json();
    const newestId = messages.length ? messages[messages.length - 1].id : 0;
    if (newestId !== lastMessageId) {
        renderMessages(messages);
    }
};

const refreshSidebarUnread = async () => {
    const response = await fetch('/messages/api/notifications', { headers: { 'Accept': 'application/json' } });
    if (!response.ok) return;
    const payload = await response.json();
    const unreadMap = new Map((payload.byUser || []).map(item => [String(item.userId), Number(item.count)]));
    document.querySelectorAll('.chat-user[data-user-id]').forEach((item) => {
        const id = item.getAttribute('data-user-id');
        const count = unreadMap.get(id) || 0;
        const badge = item.querySelector('.chat-user-unread');
        if (!badge) return;
        badge.style.display = count > 0 ? 'inline-block' : 'none';
    });
};

form?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const contenu = (input.value || '').trim();
    if (!contenu) return;
    submit.disabled = true;
    try {
        const body = new URLSearchParams();
        body.append('destinataireId', String(interlocuteurId));
        body.append('contenu', contenu);
        const response = await fetch('/messages/api/envoyer', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
            body: body.toString()
        });
        if (response.ok) {
            input.value = '';
            await fetchMessages();
        }
    } finally {
        submit.disabled = false;
        input.focus();
    }
});

fetchMessages().finally(() => {
    if (zone) zone.scrollTop = zone.scrollHeight;
    polling = setInterval(fetchMessages, 3000);
    refreshSidebarUnread();
    setInterval(refreshSidebarUnread, 4000);
});
window.addEventListener('beforeunload', () => { if (polling) clearInterval(polling); });
