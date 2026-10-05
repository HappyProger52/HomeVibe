'use strict';
let homes = [];
let publicReviews = [];
let unavailableDates = [];
let requestKey;
const $ = (selector) => document.querySelector(selector);
const money = (value) => new Intl.NumberFormat('ru-RU').format(value) + ' ₽';
const today = () => new Intl.DateTimeFormat('en-CA',{timeZone:'Europe/Moscow',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const nights = (start, end) => Math.round((Date.parse(end) - Date.parse(start)) / 86400000);
const dateText = (date) => new Date(date + 'T12:00:00').toLocaleDateString('ru-RU', { day: 'numeric', month: 'short', year: 'numeric' });
const escapeHtml = (value) => String(value).replace(/[&<>"']/g, (c) => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c]));
const statusNames = { pending:'Ожидает подтверждения', confirmed:'Подтверждено', cancelled:'Отменено' };
let bookings = [];
let filter = 'all';
let search = { start: '', end: '', guests: 1 };
let selectedHome;
let toastTimer;
function validDate(value) { return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0,10) === value; }
function nextDay(value) {
  const date=new Date(value+'T00:00:00Z');
  date.setUTCDate(date.getUTCDate()+1);
  return date.toISOString().slice(0,10);
}
function syncDeparture(startInput,endInput) {
  const arrival=validDate(startInput.value)?startInput.value:today();
  endInput.min=nextDay(arrival);
  if(startInput.value && (!endInput.value || endInput.value<endInput.min)) endInput.value=endInput.min;
}
function dateError(start,end) {
  if (!validDate(start) || !validDate(end)) return 'Укажите даты заезда и выезда.';
  if (start < today()) return 'Дата заезда не может быть в прошлом.';
  if (nights(start,end) <= 0) return 'Дата выезда должна быть позже даты заезда.';
  if (nights(start,end) > 365) return 'Максимальный срок заявки — 365 ночей.';
  return '';
}
function unavailable(homeId,start,end) {
  const house=homes.find(h=>h.id===homeId);
  return (house.availableFrom && start<house.availableFrom) || (house.availableUntil && end>house.availableUntil)
    || house.blockedDates.some(b=>start<b.end&&end>b.start)
    || unavailableDates.some(b=>b.homeId===homeId && start<b.end && end>b.start);
}
const api=(...args)=>HV.api(...args);
async function loadHomes() {const [data,feedback]=await Promise.all([api('/api/homes'),api('/api/reviews')]);homes=data.homes;publicReviews=feedback.reviews;unavailableDates=data.unavailable;renderHomes();}
function toast(message) { clearTimeout(toastTimer); $('#toast').textContent=message; $('#toast').hidden=false; toastTimer=setTimeout(()=>$('#toast').hidden=true,7000); }
function renderHomes() {
  const visible = homes.filter(h => (filter==='all' || h.category===filter) && h.guests>=search.guests && (!search.start || !unavailable(h.id,search.start,search.end)));
  $('#results-count').textContent = `Найдено: ${visible.length}`;
  $('#house-grid').innerHTML = visible.length ? visible.map(h=>`<article class="house-card"><div class="house-image"><img class="house-cover" src="${escapeHtml(h.imageUrl)}" alt="${escapeHtml(h.name)}"><span class="house-tag">${escapeHtml(h.tag)}</span></div><div class="house-body"><div class="house-location">⌖ ${escapeHtml(h.location)}</div><h3>${escapeHtml(h.name)}</h3><p class="house-description">${escapeHtml(h.description)}</p><p class="house-times">Заезд с ${escapeHtml(h.checkIn.slice(0,5))} · Выезд до ${escapeHtml(h.checkOut.slice(0,5))}</p><div class="house-info"><span>До ${h.guests} гостей</span><span>${h.bedrooms} спальн${h.bedrooms===1?'я':'и'}</span><span>${h.size} м²</span></div><div class="house-bottom"><div class="price">${money(h.price)} <small>/ ночь</small></div><button class="book-button" data-book="${h.id}">Выбрать даты ↗</button></div>${houseReviews(h.id)}</div></article>`).join('') : '<div class="empty">По выбранным условиям домов нет.<br>Попробуйте другие даты или количество гостей.</div>';
}
function houseReviews(id) {
  const reviews=publicReviews.filter(r=>r.homeId===id);
  if(!reviews.length)return '<p class="muted">Отзывов пока нет</p>';
  const average=(reviews.reduce((sum,r)=>sum+r.rating,0)/reviews.length).toFixed(1);
  return `<details class="house-reviews"><summary>★ ${average} / 5 · Отзывы (${reviews.length})</summary>${reviews.slice().reverse().map(r=>`<article class="review-card"><strong>${HV.escape(r.author)} · ${r.rating}/5</strong><p class="muted">${HV.date(r.createdAt.slice(0,10))} · Проживание подтверждено</p><p class="review-text">${HV.escape(r.text)}</p>${r.reply?`<blockquote><strong>Ответ владельца</strong><p class="review-text">${HV.escape(r.reply)}</p></blockquote>`:''}</article>`).join('')}</details>`;
}
$('#house-grid').addEventListener('click', e=>{const button=e.target.closest('[data-book]');if(button)openBooking(button.dataset.book);});
document.querySelectorAll('[data-filter]').forEach(button=>button.addEventListener('click',()=>{
  filter=button.dataset.filter;
  document.querySelectorAll('[data-filter]').forEach(b=>{b.classList.toggle('active',b===button);b.setAttribute('aria-pressed',String(b===button));});
  renderHomes();
}));
$('#search-form').addEventListener('submit',async e=>{
  e.preventDefault();const start=$('#search-start').value,end=$('#search-end').value;
  const error=(start||end)?dateError(start,end):'';$('#search-error').textContent=error;if(error)return;
  search={start,end,guests:Number($('#search-guests').value)};try{await loadHomes();$('#homes').scrollIntoView({behavior:'smooth'});}catch(error){$('#search-error').textContent=error.message;}
});
const form=$('#booking-form');
const field=name=>form.elements.namedItem(name);
async function openBooking(id) {
  let session;
  try { session=await api('/api/session'); } catch(error){toast(error.message);return;}
  if(!session.authenticated) {
    const params=new URLSearchParams({house:id,start:search.start,end:search.end,guests:String(search.guests)});
    location.href='/account?'+params.toString();return;
  }
  if(session.role!=='CUSTOMER'){toast('Для бронирования войдите в аккаунт гостя через личный кабинет.');return;}
  selectedHome=homes.find(h=>h.id===id);form.reset();requestKey=crypto.randomUUID();
  field('name').value=session.name;
  $('#booking-title').textContent=selectedHome.name;
  $('#booking-description').textContent=`${money(selectedHome.price)} за ночь · До ${selectedHome.guests} гостей · Заезд с ${selectedHome.checkIn.slice(0,5)}, выезд до ${selectedHome.checkOut.slice(0,5)}`;
  field('start').min=today();field('end').min=today();field('guests').max=selectedHome.guests;
  field('start').value=search.start;field('end').value=search.end;field('guests').value=Math.min(selectedHome.guests,Math.max(2,search.guests));
  syncDeparture(field('start'),field('end'));
  $('#booking-error').textContent='';updateTotal();$('#booking-dialog').showModal();
}
function updateTotal() {
  const start=field('start').value,end=field('end').value;
  const count=nights(start,end);
  $('#booking-total').textContent=!dateError(start,end)?`${count} ноч. × ${money(selectedHome.price)} = ${money(count*selectedHome.price)}`:'Выберите даты, чтобы рассчитать стоимость.';
}
field('start').addEventListener('input',()=>{syncDeparture(field('start'),field('end'));updateTotal();});
field('end').addEventListener('input',updateTotal);
$('#close-dialog').addEventListener('click',()=>$('#booking-dialog').close());
form.addEventListener('submit',async e=>{
  e.preventDefault();const start=field('start').value,end=field('end').value,guests=Number(field('guests').value),name=field('name').value.trim(),phone=field('phone').value.trim(),note=field('note').value.trim();
  let error=dateError(start,end);
  if(!error && (!Number.isInteger(guests)||guests<1||guests>selectedHome.guests))error=`В этом доме можно разместить от 1 до ${selectedHome.guests} гостей.`;
  if(!error && name.length<2)error='Введите имя, минимум 2 символа.';
  if(!error && (!/^[+\d\s()\-]+$/.test(phone)||phone.replace(/\D/g,'').length<10||phone.replace(/\D/g,'').length>15))error='Введите телефон: от 10 до 15 цифр.';
  if(!error && unavailable(selectedHome.id,start,end))error='На эти даты уже есть подтверждённое бронирование. Выберите другие даты.';
  if(error){$('#booking-error').textContent=error;return;}
  const booking={homeId:selectedHome.id,start,end,guests,name,phone,note,requestKey};
  const submit=form.querySelector('[type="submit"]');submit.disabled=true;
  try{const saved=await api('/api/bookings','POST',booking);$('#booking-dialog').close();toast('Заявка № '+saved.id.slice(0,8)+' принята! Следите за статусом в личном кабинете.');loadHomes().catch(()=>{});}
  catch(error){$('#booking-error').textContent=error.message;}
  finally{submit.disabled=false;}
});
$('#search-start').min=today();$('#search-end').min=nextDay(today());$('#year').textContent=new Date().getFullYear();
$('#search-start').addEventListener('input',()=>syncDeparture($('#search-start'),$('#search-end')));
$('#house-grid').innerHTML='<p class="empty">Загружаем дома…</p>';
loadHomes().then(()=>{
  const params=new URLSearchParams(location.search),id=params.get('house');
  if(id&&homes.some(h=>h.id===id)) {
    const start=params.get('start')||'',end=params.get('end')||'';
    search={start:validDate(start)?start:'',end:validDate(end)?end:'',guests:Math.max(1,Math.min(30,Number(params.get('guests'))||1))};
    history.replaceState(null,'','/');openBooking(id);
  }
}).catch(error=>{$('#house-grid').innerHTML='<p class="empty">Не удалось загрузить дома. Проверьте соединение и обновите страницу.</p>';toast(error.message);});

