'use strict';
(() => {
  let slides=[],current=0;
  const title=document.querySelector('#slide-title'),description=document.querySelector('#slide-description');
  const art=document.querySelector('#slide-art'),image=document.querySelector('#slide-image');
  const previous=document.querySelector('#slide-prev'),next=document.querySelector('#slide-next'),dots=document.querySelector('#slide-dots');
  function show(index) {
    current=(index+slides.length)%slides.length;
    const slide=slides[current];
    title.textContent=slide.title;description.textContent=slide.description;
    image.src=slide.imageUrl;image.alt=slide.caption;
    document.querySelector('#slide-caption').textContent=slide.caption;
    document.querySelector('#slide-counter').textContent=(current+1)+' / '+slides.length;
    const link=document.querySelector('#slide-link');
    link.href=slide.houseId?'/?house='+encodeURIComponent(slide.houseId):'#homes';
    link.firstChild.textContent=slide.houseId?'Выбрать этот дом ':'Выбрать свой дом ';
    dots.querySelectorAll('button').forEach((b,i)=>{b.setAttribute('aria-pressed',String(i===current));});
    // Web Animations не меняет разметку и не скрывает содержимое при ошибке.
    if(!matchMedia('(prefers-reduced-motion: reduce)').matches) {
      for(const el of [title,description,image]) {
        el.getAnimations().forEach(a=>a.cancel());
        el.animate([{opacity:.35,transform:'translateY(8px)'},{opacity:1,transform:'translateY(0)'}],{duration:350,easing:'ease-out'});
      }
    }
  }
  previous.onclick=()=>{if(slides.length)show(current-1);};next.onclick=()=>{if(slides.length)show(current+1);};
  dots.onclick=e=>{const b=e.target.closest('[data-slide]');if(b)show(Number(b.dataset.slide));};
  art.addEventListener('keydown',e=>{if(e.key==='ArrowLeft'||e.key==='ArrowRight'){e.preventDefault();if(slides.length)show(current+(e.key==='ArrowLeft'?-1:1));}});
  HV.api('/api/slides').then(data=>{
    slides=data.slides;if(!slides.length)return;
    previous.hidden=next.hidden=slides.length===1;
    dots.innerHTML=slides.length>1?slides.map((s,i)=>`<button type="button" data-slide="${i}" aria-label="Слайд ${i+1}: ${HV.escape(s.caption)}" aria-pressed="false"><span></span></button>`).join(''):'';
    show(0);
  }).catch(()=>{previous.hidden=next.hidden=true;document.querySelector('#slide-counter').textContent='';});
})();
