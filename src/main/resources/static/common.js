'use strict';
const HV = {
  escape(value) { return String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); },
  money(value) { return new Intl.NumberFormat('ru-RU').format(value) + ' ₽'; },
  date(value) { return new Date(value+'T12:00:00').toLocaleDateString('ru-RU'); },
  status: {pending:'Ожидает подтверждения',confirmed:'Подтверждено',cancelled:'Отменено',completed:'Проживание завершено'},
  async api(url, method='GET', data) {
    const headers={}; let body;
    if (method !== 'GET') {
      const response=await fetch('/api/csrf');
      if(!response.ok) throw new Error('Не удалось защитить запрос. Обновите страницу.');
      const csrf=await response.json();headers[csrf.headerName]=csrf.token;
    }
    if(url==='/api/login') {
      headers['Content-Type']='application/x-www-form-urlencoded';body=new URLSearchParams(data).toString();
    } else if(data instanceof FormData) { body=data; }
    else if(data) { headers['Content-Type']='application/json';body=JSON.stringify(data); }
    let response;
    try { response=await fetch(url,{method,headers,body}); }
    catch { throw new Error('Нет связи с сервером. Попробуйте ещё раз.'); }
    const result=await response.json().catch(()=>({error:'Не удалось обработать ответ сервера.'}));
    if(!response.ok) { const error=new Error(result.error||'Не удалось выполнить запрос.');error.status=response.status;throw error; }
    return result;
  },
  async login(username,password) {
    await this.api('/api/login','POST',{username:username.trim().toLowerCase(),password});
    return this.api('/api/session');
  },
  bookingCard(b,house,admin=false) {
    const e=this.escape;
    return `<article class="booking-row"><div><h3>${e(house?.name||b.homeId)}</h3><p>№ ${e(b.id.slice(0,8))}</p>${admin?`<p>${e(b.name)} · ${e(b.phone)}</p>`:''}${b.note?`<p class="muted">${e(b.note)}</p>`:''}</div><div><h3>${this.date(b.start)} — ${this.date(b.end)}</h3><p>${Math.round((Date.parse(b.end)-Date.parse(b.start))/86400000)} ноч. · Гостей: ${b.guests}</p><p>${this.money(b.total)} за весь срок</p></div><div><span class="status ${b.status}">${this.status[b.status]}</span>${admin?`<div class="booking-actions">${b.status==='confirmed'?`<button data-action="completed" data-id="${e(b.id)}">Завершить проживание</button>`:''}${b.status==='pending'?`<button data-action="confirmed" data-id="${e(b.id)}">Подтвердить</button>`:''}${!['cancelled','completed'].includes(b.status)?`<button data-action="cancelled" data-id="${e(b.id)}">Отменить заявку</button>`:''}</div>`:`<p class="muted">${b.status==='pending'?'Владелец проверяет вашу заявку.':b.status==='confirmed'?'Даты закреплены за вами.':b.status==='completed'?'Спасибо за поездку! Вы можете оставить отзыв.':'Бронирование отменено.'}</p>`}</div></article>`;
  }
};


// Содержимое остаётся доступным без JavaScript и при отключённой анимации.
(() => {
  const preference = matchMedia('(prefers-reduced-motion: reduce)');
  if (preference.matches || !('IntersectionObserver' in window)) return;
  const seen = new WeakSet();
  const observer = new IntersectionObserver(entries => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue;
      entry.target.classList.add('motion-enter');
      observer.unobserve(entry.target);
    }
  }, { threshold: .06 });
  const watch = element => {
    if (seen.has(element)) return;
    seen.add(element); observer.observe(element);
  };
  document.querySelectorAll('.section-heading,.about,.features article,.faq details').forEach(watch);
  const grid = document.querySelector('#house-grid');
  let changes;
  if (grid) {
    const scan = () => grid.querySelectorAll('.house-card').forEach(watch);
    scan();
    changes = new MutationObserver(records => {
      for (const record of records) {
        for (const node of record.removedNodes) {
          if (node.nodeType === 1) observer.unobserve(node);
        }
      }
      scan();
    });
    changes.observe(grid, { childList: true });
  }
  preference.addEventListener('change', event => {
    if (event.matches) { observer.disconnect(); changes?.disconnect(); }
  });
})();
