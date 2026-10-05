'use strict';
const $=s=>document.querySelector(s);
let register=false, refreshTimer, reviewBooking;
let myBookings=[], myHomes=[];
const reviewStates={pending:'На проверке',published:'Опубликован',rejected:'Не опубликован'};
function reviewBlock(b,reviews) {
  const r=reviews.find(r=>r.bookingId===b.id);
  if(r)return `<section class="review-card"><strong>Ваш отзыв · ${reviewStates[r.status]} · ${r.rating}/5</strong><p class="review-text">${HV.escape(r.text)}</p>${r.status==='published'&&r.reply?`<blockquote><strong>Ответ владельца</strong><p class="review-text">${HV.escape(r.reply)}</p></blockquote>`:''}</section>`;
  return b.status==='completed'?`<button class="button secondary" data-review="${HV.escape(b.id)}">Оставить отзыв</button>`:'';
}
const form=$('#account-form');
function mode(value) {
  register=value;$('#name-label').hidden=!value;form.elements.name.required=value;
  $('#password-hint').hidden=!value;form.elements.password.minLength=value?12:1;
  form.elements.password.autocomplete=value?'new-password':'current-password';
  $('#auth-submit').textContent=value?'Создать аккаунт':'Войти';$('#auth-error').textContent='';
  $('#show-login').classList.toggle('active',!value);$('#show-register').classList.toggle('active',value);
  $('#show-login').setAttribute('aria-pressed',String(!value));$('#show-register').setAttribute('aria-pressed',String(value));
}
$('#show-login').onclick=()=>mode(false);$('#show-register').onclick=()=>mode(true);
async function refresh() {
  try {
    const [data,catalog,feedback]=await Promise.all([HV.api('/api/account/bookings'),HV.api('/api/homes'),HV.api('/api/account/reviews')]);
    myBookings=data.bookings;myHomes=catalog.homes;
    $('#my-bookings').innerHTML=data.bookings.length?data.bookings.slice().reverse().map(b=>`<div class="booking-with-review">${HV.bookingCard(b,catalog.homes.find(h=>h.id===b.homeId))}${reviewBlock(b,feedback.reviews)}</div>`).join(''):'<div class="empty">У вас пока нет бронирований.<br><a href="/#homes">Выберите дом для следующей поездки ↗</a></div>';
    $('#account-error').textContent='';$('#updated-at').textContent='Обновлено в '+new Date().toLocaleTimeString('ru-RU');
  } catch(error) {
    $('#account-error').textContent=error.message;
    if(error.status===401||error.status===403) { clearInterval(refreshTimer);$('#my-bookings').innerHTML='';await boot(); }
  }
}
function continueBooking() {
  const params=new URLSearchParams(location.search);
  if(params.has('house')) { location.href='/?'+params.toString();return true; }
  return false;
}
async function boot() {
  try {
    const session=await HV.api('/api/session');
    $('#auth-area').hidden=session.authenticated;$('#my-area').hidden=!session.authenticated||session.role!=='CUSTOMER';
    $('#wrong-role').hidden=!session.authenticated||session.role!=='OWNER';
    clearInterval(refreshTimer);
    if(session.authenticated&&session.role==='CUSTOMER') {
      if(continueBooking())return;
      $('#greeting').textContent=session.name+', ваши бронирования';await refresh();
      refreshTimer=setInterval(()=>{if(!document.hidden)refresh();},30000);
    }
  } catch(error) { $('#auth-error').textContent=error.message; }
}
form.onsubmit=async e=>{
  e.preventDefault();const button=$('#auth-submit');button.disabled=true;$('#auth-error').textContent='';
  const username=form.elements.username.value.trim().toLowerCase(),password=form.elements.password.value;
  try {
    if(register) {
      await HV.api('/api/register','POST',{username,password,name:form.elements.name.value.trim()});
      mode(false);
    }
    await HV.login(username,password);form.reset();await boot();
  } catch(error) { $('#auth-error').textContent=error.message; }
  finally {button.disabled=false;}
};
async function logout() {
  try{await HV.api('/api/logout','POST',{});clearInterval(refreshTimer);$('#my-bookings').innerHTML='';await boot();}
  catch(error){$('#account-error').textContent=error.message;}
}
$('#account-logout').onclick=logout;$('#switch-account').onclick=logout;$('#account-refresh').onclick=refresh;
$('#my-bookings').onclick=e=>{
  const button=e.target.closest('[data-review]');if(!button)return;
  reviewBooking=button.dataset.review;const b=myBookings.find(b=>b.id===reviewBooking);
  $('#review-form').reset();$('#review-error').textContent='';
  $('#review-house').textContent=(myHomes.find(h=>h.id===b.homeId)?.name||b.homeId)+' · '+HV.date(b.start)+' — '+HV.date(b.end);
  $('#review-dialog').showModal();
};
$('#close-review').onclick=()=>$('#review-dialog').close();
$('#review-form').onsubmit=async e=>{
  e.preventDefault();const f=e.target,button=f.querySelector('[type=submit]');button.disabled=true;
  try {await HV.api('/api/account/reviews','POST',{bookingId:reviewBooking,rating:Number(f.elements.rating.value),text:f.elements.text.value.trim()});$('#review-dialog').close();await refresh();}
  catch(error){$('#review-error').textContent=error.message;}finally{button.disabled=false;}
};
boot();
