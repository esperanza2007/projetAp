const updateConversationBadges = async () => {
    const response = await fetch('/messages/api/notifications', { headers: { 'Accept': 'application/json' } });
    if (!response.ok) return;
    const payload = await response.json();
    const unreadMap = new Map((payload.byUser || []).map(item => [String(item.userId), Number(item.count)]));
    document.querySelectorAll('[data-user-id]').forEach((row) => {
        const userId = row.getAttribute('data-user-id');
        const count = unreadMap.get(userId) || 0;
        const badge = row.querySelector('.unread-badge');
        if (!badge) return;
        badge.style.display = count > 0 ? 'inline-block' : 'none';
    });
};

updateConversationBadges();
setInterval(updateConversationBadges, 4000);

document.querySelectorAll('.role-tab').forEach((tab) => {
    tab.addEventListener('click', () => {
        const filter = tab.getAttribute('data-filter');
        document.querySelectorAll('.role-tab').forEach((t) => t.classList.remove('active'));
        tab.classList.add('active');
        document.querySelectorAll('.contact-pill').forEach((card) => {
            const role = card.getAttribute('data-role');
            card.style.display = (filter === 'ALL' || role === filter) ? 'flex' : 'none';
        });
    });
});
