'use strict';
const $=s=>document.querySelector(s);
let homes=[],bookings=[],selectedId,closed=[],photoBusy=false,loadVersion=0;
const editor=$('#house-editor');
function message(text,error=false){$('#admin-message').textContent=text;$('#admin-message').classList.toggle('form-error',error);}
function panel(houses) {
  $('#reviews-panel').hidden=true;$('#reviews-tab').classList.remove('active');$('#reviews-tab').setAttribute('aria-pressed','false');
  $('#houses-panel').hidden=!houses;$('#reservations-panel').hidden=houses;
  $('#houses-tab').classList.toggle('active',houses);$('#bookings-tab').classList.toggle('active',!houses);
  $('#houses-tab').setAttribute('aria-pressed',String(houses));$('#bookings-tab').setAttribute('aria-pressed',String(!houses));
}
$('#houses-tab').onclick=()=>panel(true);$('#bookings-tab').onclick=()=>panel(false);
function renderBookings() {
  $('#stats').innerHTML=`<div class="stat">Всего заявок<strong>${bookings.length}</strong></div><div class="stat">Ожидают ответа<strong>${bookings.filter(b=>b.status==='pending').length}</strong></div><div class="stat">Подтверждено<strong>${bookings.filter(b=>b.status==='confirmed').length}</strong></div>`;
  const query=$('#admin-search').value.trim().toLowerCase(),status=$('#admin-status').value;
  const visible=bookings.filter(b=>(status==='all'||b.status===status)&&`${b.name} ${b.phone} ${homes.find(h=>h.id===b.homeId)?.name}`.toLowerCase().includes(query));
  $('#bookings-list').innerHTML=visible.length?visible.slice().reverse().map(b=>HV.bookingCard(b,homes.find(h=>h.id===b.homeId),true)).join(''):'<div class="empty">Заявок по выбранным условиям нет.</div>';
}
async function refreshBookings() {
  try {bookings=(await HV.api('/api/admin/bookings')).bookings;renderBookings();}
  catch(error){message(error.message,true);if(error.status===401||error.status===403)await boot();}
}
function renderRanges() {
  $('#closed-ranges').innerHTML=closed.length?closed.map((range,i)=>`<div class="closed-range"><span>${HV.date(range.start)} — ${HV.date(range.end)}</span><button type="button" class="book-button" data-remove="${i}">Убрать</button></div>`).join(''):'<p class="muted">Закрытых периодов нет.</p>';
}
function selectHouse(id) {
  if(photoBusy)return;
  const house=homes.find(h=>h.id===id);if(!house)return;
  selectedId=id;loadVersion++;editor.reset();$('#editor-error').textContent='';
  for(const key of ['name','category','guests','bedrooms','size','price','tag','location','description','imageUrl','availableFrom','availableUntil','checkIn','checkOut']) editor.elements.namedItem(key).value=house[key]??'';
  closed=house.blockedDates.map(r=>({...r}));$('#editor-preview').src=house.imageUrl;renderRanges();
  document.querySelectorAll('[data-house]').forEach(button=>{button.classList.toggle('active',button.dataset.house===id);button.setAttribute('aria-pressed',String(button.dataset.house===id));});
}
function renderHouseSelector() {
  $('#house-selector').innerHTML=homes.map(h=>`<button class="house-choice" data-house="${HV.escape(h.id)}"><img src="${HV.escape(h.imageUrl)}" alt=""><span>${HV.escape(h.name)}<small>${HV.money(h.price)} / ночь</small></span></button>`).join('');
}
$('#house-selector').onclick=e=>{const button=e.target.closest('[data-house]');if(button)selectHouse(button.dataset.house);};
$('#closed-ranges').onclick=e=>{const button=e.target.closest('[data-remove]');if(button){closed.splice(Number(button.dataset.remove),1);renderRanges();}};
$('#add-closed').onclick=()=>{
  const start=$('#closed-start').value,end=$('#closed-end').value;
  if(!start||!end||start>=end){$('#editor-error').textContent='Укажите начало и окончание закрытого периода.';return;}
  closed.push({start,end});closed.sort((a,b)=>a.start.localeCompare(b.start));renderRanges();$('#closed-start').value='';$('#closed-end').value='';$('#editor-error').textContent='';
};
$('#reset-image').onclick=()=>{if(photoBusy)return;editor.elements.imageUrl.value='/cabin.svg';$('#editor-preview').src='/cabin.svg';$('#house-photo').value='';};
$('#house-photo').onchange=async()=>{
  const file=$('#house-photo').files[0];if(!file)return;
  if(file.size>5*1024*1024){$('#editor-error').textContent='Максимальный размер фото — 5 МБ.';return;}
  const version=loadVersion,body=new FormData();body.append('file',file);photoBusy=true;$('#save-house').disabled=true;
  try {const result=await HV.api('/api/admin/images','POST',body);if(version===loadVersion){editor.elements.imageUrl.value=result.url;$('#editor-preview').src=result.url;$('#editor-error').textContent='';message('Фото загружено. Сохраните карточку, чтобы опубликовать изменения.');}}
  catch(error){$('#editor-error').textContent=error.message;}
  finally{photoBusy=false;$('#save-house').disabled=false;}
};
editor.onsubmit=async e=>{
  e.preventDefault();if(photoBusy)return;const payload=Object.fromEntries(new FormData(editor));
  for(const key of ['guests','bedrooms','size','price'])payload[key]=Number(payload[key]);
  for(const key of ['availableFrom','availableUntil'])payload[key]=payload[key]||null;
  payload.blockedDates=closed;$('#save-house').disabled=true;$('#editor-error').textContent='';
  const id=selectedId;
  try {const updated=await HV.api('/api/admin/houses/'+id,'PUT',payload);homes=homes.map(h=>h.id===id?updated:h);renderHouseSelector();selectHouse(id);renderBookings();message('Карточка и доступность сохранены. Изменения уже видны на сайте.');}
  catch(error){$('#editor-error').textContent=error.message;}
  finally{$('#save-house').disabled=false;}
};
$('#bookings-list').onclick=async e=>{
  const button=e.target.closest('[data-action]');if(!button)return;button.disabled=true;
  try{await HV.api('/api/admin/bookings/'+button.dataset.id,'PATCH',{status:button.dataset.action});await refreshBookings();message('Статус обновлён. Гость увидит его в личном кабинете.');}
  catch(error){message(error.message,true);}finally{button.disabled=false;}
};
$('#admin-search').oninput=renderBookings;$('#admin-status').onchange=renderBookings;$('#refresh-bookings').onclick=refreshBookings;
async function boot() {
  try {
    const session=await HV.api('/api/session'),authorized=session.authenticated&&session.role==='OWNER';
    $('#admin-auth').hidden=authorized;$('#admin-workspace').hidden=!authorized;
    if(authorized) {homes=(await HV.api('/api/homes')).homes;renderHouseSelector();selectHouse(homes[0]?.id);await refreshBookings();await refreshReviews();}
    else {bookings=[];$('#bookings-list').innerHTML='';$('#admin-auth-hint').textContent=session.configured?'Введите свой логин и пароль администратора.':'Доступ администратора пока не настроен. Задайте логин и пароль при запуске проекта.';}
  } catch(error){$('#admin-login-error').textContent=error.message;}
}
$('#admin-login-form').onsubmit=async e=>{
  e.preventDefault();const form=e.target,button=form.querySelector('button');button.disabled=true;$('#admin-login-error').textContent='';
  try{const session=await HV.login(form.elements.username.value,form.elements.password.value);form.elements.password.value='';if(session.role!=='OWNER'){throw new Error('Этот аккаунт не является администратором. Кабинет гостя находится по ссылке /account.');}await boot();}
  catch(error){$('#admin-login-error').textContent=error.message;}finally{button.disabled=false;}
};
$('#admin-logout').onclick=async()=>{try{await HV.api('/api/logout','POST',{});await boot();}catch(error){message(error.message,true);}};
boot();
const reviewStates={pending:'На проверке',published:'Опубликован',rejected:'Не опубликован'};
async function refreshReviews(render=true) {
  try {
    const {reviews}=await HV.api('/api/admin/reviews');
    $('#review-count').textContent='('+reviews.filter(r=>r.status==='pending').length+' на проверке)';
    if(!render)return;
    $('#admin-reviews').innerHTML=reviews.length?reviews.slice().sort((a,b)=>(a.status==='pending'?-1:0)-(b.status==='pending'?-1:0)||b.createdAt.localeCompare(a.createdAt)).map(r=>`<form class="review-card moderation-form" data-review-id="${HV.escape(r.id)}"><h3>${HV.escape(homes.find(h=>h.id===r.homeId)?.name||r.homeId)} · ${r.rating}/5</h3><p>${HV.escape(r.author)} · Бронь № ${HV.escape(r.bookingId.slice(0,8))} · ${reviewStates[r.status]}</p><p class="review-text">${HV.escape(r.text)}</p><label>Ответ владельца · необязательно<textarea name="reply" rows="3" maxlength="2000">${HV.escape(r.reply)}</textarea></label><div class="review-actions"><button class="button primary" type="submit" value="published">${r.status==='published'?'Сохранить ответ':'Опубликовать'}</button><button class="button secondary" type="submit" value="rejected">${r.status==='published'?'Снять с публикации':'Не публиковать'}</button></div><p class="form-error" role="alert"></p></form>`).join(''):'<p class="empty">Отзывов пока нет.</p>';
  } catch(error){message(error.message,true);}
}
$('#reviews-tab').onclick=()=>{
  $('#reviews-panel').hidden=false;$('#houses-panel').hidden=true;$('#reservations-panel').hidden=true;
  for(const id of ['reviews-tab','houses-tab','bookings-tab']) {$('#'+id).classList.toggle('active',id==='reviews-tab');$('#'+id).setAttribute('aria-pressed',String(id==='reviews-tab'));}
  refreshReviews();
};
$('#refresh-reviews').onclick=()=>refreshReviews();
$('#admin-reviews').onsubmit=async e=>{
  e.preventDefault();const f=e.target,buttons=f.querySelectorAll('button');buttons.forEach(b=>b.disabled=true);
  try {await HV.api('/api/admin/reviews/'+f.dataset.reviewId,'PATCH',{status:e.submitter.value,reply:f.elements.reply.value.trim()});await refreshReviews();message('Решение по отзыву сохранено.');}
  catch(error){f.querySelector('.form-error').textContent=error.message;}finally{buttons.forEach(b=>b.disabled=false);}
};
setInterval(()=>{if(!document.hidden&&!$('#admin-workspace').hidden)refreshReviews(false);},30000);
