'use strict';
(() => {
  const form=document.querySelector('#slides-form'),list=document.querySelector('#slides-list');
  const info=document.querySelector('#slides-message');
  let draft=[],loaded=false,busy=false;
  function lock(value) {
    busy=value;
    form.querySelectorAll('input,textarea,select,button').forEach(e=>e.disabled=value);
    if(!value) {
      document.querySelector('#add-slide').disabled=draft.length>=10;
      list.querySelectorAll('[data-slide-action]').forEach(b=>{
        const i=Number(b.closest('[data-index]').dataset.index);
        b.disabled=(b.dataset.slideAction==='up'&&i===0)||(b.dataset.slideAction==='down'&&i===draft.length-1)||(b.dataset.slideAction==='remove'&&draft.length===1);
      });
    }
  }
  function render() {
    list.innerHTML=draft.map((s,i)=>`<section class="slide-editor-card" data-index="${i}" aria-label="Редактор слайда ${i+1}"><div class="slide-editor-heading"><h3>Слайд ${i+1}</h3><div class="review-actions"><button class="book-button" type="button" data-slide-action="up" aria-label="Слайд ${i+1}: выше">↑</button><button class="book-button" type="button" data-slide-action="down" aria-label="Слайд ${i+1}: ниже">↓</button><button class="book-button" type="button" data-slide-action="remove">Убрать слайд</button></div></div><div class="slide-editor-grid"><div><img class="slide-editor-preview" src="${HV.escape(s.imageUrl)}" alt="Изображение слайда ${i+1}"><label>Фото слайда ${i+1}<input type="file" accept="image/png,image/jpeg" data-slide-photo></label><p class="muted">JPEG / PNG, до 5 МБ. Рекомендуется горизонтальное фото.</p></div><div><label>Заголовок<input data-field="title" maxlength="100" required value="${HV.escape(s.title)}"></label><label>Описание<textarea data-field="description" rows="3" maxlength="350" required>${HV.escape(s.description)}</textarea></label><label>Подпись фотографии<input data-field="caption" maxlength="80" required value="${HV.escape(s.caption)}"></label><label>Кнопка ведёт к дому<select data-field="houseId"><option value="" ${s.houseId?'':'selected'}>Весь каталог</option>${homes.map(h=>`<option value="${HV.escape(h.id)}" ${h.id===s.houseId?'selected':''}>${HV.escape(h.name)}</option>`).join('')}</select></label></div></div></section>`).join('');
    lock(false);
  }
  document.querySelector('#slides-tab').onclick=async()=>{
    for(const [tab,panel] of [['bookings-tab','reservations-panel'],['houses-tab','houses-panel'],['reviews-tab','reviews-panel'],['slides-tab','slides-panel']]) {
      const active=tab==='slides-tab';document.querySelector('#'+panel).hidden=!active;
      document.querySelector('#'+tab).classList.toggle('active',active);document.querySelector('#'+tab).setAttribute('aria-pressed',String(active));
    }
    if(busy)return;
    if(loaded){render();return;}
    lock(true);info.textContent='Загружаем слайды…';
    try {draft=(await HV.api('/api/slides')).slides.map(s=>({...s}));loaded=true;render();info.textContent='';}
    catch(error){info.textContent=error.message;}finally{lock(false);}
  };
  list.addEventListener('input',e=>{
    if(busy||!e.target.dataset.field)return;
    const i=Number(e.target.closest('[data-index]').dataset.index);
    draft[i][e.target.dataset.field]=e.target.value;
    info.textContent='Есть несохранённые изменения.';
  });
  list.addEventListener('change',async e=>{
    if(!e.target.matches('[data-slide-photo]')||busy)return;
    const file=e.target.files[0];if(!file)return;
    if(file.size>5*1024*1024){info.textContent='Размер фото — до 5 МБ.';e.target.value='';return;}
    const i=Number(e.target.closest('[data-index]').dataset.index),body=new FormData();body.append('file',file);lock(true);
    info.textContent='Загружаем фотографию…';
    try {draft[i].imageUrl=(await HV.api('/api/admin/images','POST',body)).url;render();info.textContent='Фото загружено. Сохраните слайдер, чтобы опубликовать его.';}
    catch(error){info.textContent=error.message;}finally{lock(false);}
  });
  list.onclick=e=>{
    const b=e.target.closest('[data-slide-action]');if(!b||busy)return;
    const i=Number(b.closest('[data-index]').dataset.index),action=b.dataset.slideAction;
    if(action==='remove'&&draft.length>1)draft.splice(i,1);
    else if(action==='up'&&i>0)[draft[i-1],draft[i]]=[draft[i],draft[i-1]];
    else if(action==='down'&&i<draft.length-1)[draft[i+1],draft[i]]=[draft[i],draft[i+1]];
    render();info.textContent='Есть несохранённые изменения.';
  };
  document.querySelector('#add-slide').onclick=()=>{
    if(busy||!loaded||draft.length>=10)return;
    draft.push({title:'',description:'',caption:'',imageUrl:'/cabin.svg',houseId:''});render();
    list.lastElementChild.querySelector('input[data-field]').focus();info.textContent='Заполните новый слайд и сохраните изменения.';
  };
  form.onsubmit=async e=>{
    e.preventDefault();if(busy||!loaded)return;lock(true);info.textContent='Сохраняем…';
    try {draft=(await HV.api('/api/admin/slides','PUT',{slides:draft})).slides;render();info.textContent='Слайдер сохранён. Изменения уже доступны на главной странице.';}
    catch(error){info.textContent=error.message;}finally{lock(false);}
  };
})();
