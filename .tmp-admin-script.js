let currentTicketId = null;
let adminStatusFilter = null;
const ADMIN_API = location.pathname.replace(/\/tickets\/?$/, '') + '/tickets';

const TICKET_BADGES = {
  open:     { cls: 'ticket-badge-open',     text: 'Открыт' },
  progress: { cls: 'ticket-badge-progress', text: 'В работе' },
  closed:   { cls: 'ticket-badge-closed',   text: 'Закрыт' }
};

function csrfToken() {
  const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return m ? decodeURIComponent(m[1]) : '';
}

async function api(path, opts) {
  opts = opts || {};
  opts.headers = Object.assign({ 'X-XSRF-TOKEN': csrfToken() }, opts.headers || {});
  if (opts.body && typeof opts.body !== 'string') {
    opts.headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(opts.body);
  }
  const r = await fetch(path, opts);
  if (!r.ok) throw new Error('HTTP ' + r.status);
  return r.json();
}

function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

function fmtDate(s) {
  const d = new Date(s);
  return isNaN(d) ? String(s) : d.toLocaleDateString('ru-RU');
}

function fmtTime(s) {
  const d = new Date(s);
  return isNaN(d) ? '' : d.toLocaleString('ru-RU', { day:'2-digit', month:'2-digit', year:'numeric', hour:'2-digit', minute:'2-digit' }).replace(',', '');
}

function ticketItemHtml(t) {
  const b = TICKET_BADGES[t.status] || TICKET_BADGES.open;
  const unread = t.unread > 0 ? '<span style="margin-left:auto; background:var(--md-sys-color-primary); color:var(--md-sys-color-on-primary); border-radius:10px; padding:0 6px; font-size:0.7rem;">' + t.unread + '</span>' : '';
  return '<div class="ticket-item" data-status="' + t.status + '" data-id="' + t.id + '" onclick="selectTicket(' + t.id + ')">' +
    '<div class="ticket-item-header"><span class="ticket-badge ' + b.cls + '">' + b.text + '</span>' + unread + '</div>' +
    '<div class="ticket-item-title">' + escapeHtml(t.subject) + '</div>' +
    '<div class="ticket-item-meta"><span class="ticket-item-date">' + fmtDate(t.updatedAt || t.createdAt) + '</span><span style="opacity:0.6; margin-left:6px;">' + escapeHtml(t.user || '') + '</span></div></div>';
}

async function loadTickets() {
  const list = document.getElementById('ticket-list');
  try {
    const qs = adminStatusFilter ? '?status=' + adminStatusFilter : '';
    const data = await api(ADMIN_API + '/list' + qs);
    if (!data.items.length) {
      list.innerHTML = '<div style="opacity:0.6; padding:1rem;">Тикетов нет.</div>';
      return;
    }
    list.innerHTML = data.items.map(ticketItemHtml).join('');
  } catch (e) {
    list.innerHTML = '<div style="opacity:0.6; padding:1rem;">Не удалось загрузить тикеты.</div>';
  }
}

function selectTicket(id) {
  currentTicketId = id;
  document.querySelectorAll('.ticket-item').forEach(el => el.classList.remove('active'));
  const ticketEl = document.querySelector('.ticket-item[data-id="' + id + '"]');
  if (ticketEl) ticketEl.classList.add('active');

  api(ADMIN_API + '/' + id).then(data => {
    const b = TICKET_BADGES[data.status] || TICKET_BADGES.open;
    document.getElementById('chat-title').textContent = data.subject;
    document.getElementById('chat-meta').innerHTML = 'Тикет #' + data.id + ' · ' + escapeHtml(data.user || '') + ' · Статус: <span class="ticket-badge ' + b.cls + '">' + b.text + '</span>';

    const actions = document.getElementById('chat-actions');
    if (data.status === 'closed') {
      actions.innerHTML = '<div class="btn-wrap btn-wrap-sm" onclick="reopenTicket(' + id + ')"><button class="btn-inner"><svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><path d="M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z"/></svg>Открыть заново</button></div>';
    } else if (data.status === 'open') {
      actions.innerHTML = '<div class="btn-wrap btn-wrap-sm" onclick="takeTicket(' + id + ')"><button class="btn-inner">Взять в работу</button></div><div class="btn-wrap btn-wrap-sm" onclick="closeTicket(' + id + ')"><button class="btn-inner">Закрыть</button></div>';
    } else {
      actions.innerHTML = '<div class="btn-wrap btn-wrap-sm" onclick="closeTicket(' + id + ')"><button class="btn-inner">Закрыть</button></div>';
    }

    const adminAvatar = '<svg viewBox="0 0 24 24"><path d="M20 2H4c-1.1 0-1.99.9-1.99 2L2 22l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM9 11H7V9h2v2zm4 0h-2V9h2v2zm4 0h-2V9h2v2z"/></svg>';
    const userAvatar = '<svg viewBox="0 0 24 24"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>';
    const box = document.getElementById('messages');
    box.innerHTML = data.messages.map(m => {
      const own = m.sender === 'ADMIN';
      return '<div class="message' + (own ? ' message-own' : '') + '"><div class="message-avatar' + (own ? '' : ' support') + '">' + (own ? adminAvatar : userAvatar) + '</div><div class="message-bubble"><div class="message-text">' + escapeHtml(m.body) + '</div><div class="message-time">' + fmtTime(m.createdAt) + '</div></div></div>';
    }).join('');
    box.scrollTop = box.scrollHeight;
  }).catch(() => {});
}

function filterTickets(status) {
  document.querySelectorAll('.filter-btn').forEach(btn => btn.classList.remove('active'));
  event.target.classList.add('active');
  adminStatusFilter = status === 'all' ? null : status;
  loadTickets();
}

function sendMessage() {
  const input = document.getElementById('message-input');
  const text = input.value.trim();
  if (!text || !currentTicketId) return;
  api(ADMIN_API + '/' + currentTicketId + '/messages', { method: 'POST', body: { body: text } })
    .then(() => {
      input.value = '';
      input.rows = 1;
      loadTickets().then(() => selectTicket(currentTicketId));
    })
    .catch(() => {});
}

function takeTicket(id) {
  api(ADMIN_API + '/' + id + '/take', { method: 'POST' })
    .then(() => loadTickets().then(() => selectTicket(id)))
    .catch(() => {});
}

function closeTicket(id) {
  api(ADMIN_API + '/' + id + '/close', { method: 'POST' })
    .then(() => loadTickets().then(() => selectTicket(id)))
    .catch(() => {});
}

function reopenTicket(id) {
  api(ADMIN_API + '/' + id + '/reopen', { method: 'POST' })
    .then(() => loadTickets().then(() => selectTicket(id)))
    .catch(() => {});
}

document.getElementById('message-input').addEventListener('input', function() {
  this.rows = 1;
  const newRows = Math.min(5, Math.ceil(this.scrollHeight / 24));
  this.rows = newRows;
});

document.getElementById('message-input').addEventListener('keydown', function(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
});

loadTickets();
