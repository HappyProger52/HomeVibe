'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {randomUUID}=require('node:crypto');
const {createApp,passwordRecord}=require('./server');
test('Заявки, авторизация, приватность, конфликты и сохранность базы',async t=>{
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),'homevibe-test-'));
 const database=path.join(directory,'test.sqlite');
 let app=createApp({database,secure:false});
 const listen=()=>new Promise(resolve=>app.server.listen(0,'127.0.0.1',resolve));
 const close=()=>new Promise((resolve,reject)=>app.server.close(error=>error?reject(error):resolve()));
 await listen();
 let base=`http://127.0.0.1:${app.server.address().port}`,cookie='';
 const password='Test-only-long-password';
 const record=passwordRecord(password);app.db.prepare('INSERT INTO owner VALUES (1,?,?)').run(record.salt,record.digest);
 async function request(route,method='GET',data,options={}){
  const response=await fetch(base+route,{method,headers:{Origin:base,...(data?{'Content-Type':'application/json'}:{}),...(cookie?{Cookie:cookie}:{}),...options},body:data?JSON.stringify(data):undefined});
  return {status:response.status,headers:response.headers,data:await response.json()};
 }
 const booking=(extra={})=>({homeId:'forest',start:'2099-11-10',end:'2099-11-13',guests:2,name:'Тестовый гость',phone:'+7 000 000-00-00',note:'<script>test</script>',requestKey:randomUUID(),...extra});
 let first,second;
 try{
  await t.test('Приватные данные и изменения закрыты без входа',async()=>{
   assert.equal((await request('/api/admin/bookings')).status,401);
   assert.equal((await request('/api/admin/bookings/'+randomUUID(),'PATCH',{status:'confirmed'})).status,401);
   assert.equal((await request('/api/login','POST',{password:'wrong'})).status,401);
   assert.equal((await request('/api/bookings','POST',booking(),{Origin:'https://other.example'})).status,403);
  });
  await t.test('Сервер проверяет ввод и сам рассчитывает цену',async()=>{
   for(const extra of [{guests:9},{guests:1.5},{start:'2099-02-30'},{end:'2099-11-09'},{phone:'abc'},{name:' '},{homeId:'missing'}])assert.equal((await request('/api/bookings','POST',booking(extra))).status,400);
   const input=booking({total:1,status:'confirmed'});first=await request('/api/bookings','POST',input);assert.equal(first.status,201);
   const retry=await request('/api/bookings','POST',input);assert.equal(retry.data.id,first.data.id);assert.equal(app.db.prepare('SELECT COUNT(*) AS n FROM bookings').get().n,1);
   const stored=app.db.prepare('SELECT * FROM bookings').get();assert.equal(stored.total,25500);assert.equal(stored.status,'pending');
   second=await request('/api/bookings','POST',booking());assert.equal(second.status,201);
  });
  await t.test('Вход выдаёт защищённую cookie и список заявок',async()=>{
   const login=await request('/api/login','POST',{password});assert.equal(login.status,200);cookie=login.headers.get('set-cookie').split(';')[0];
   assert.match(login.headers.get('set-cookie'),/HttpOnly/);assert.match(login.headers.get('set-cookie'),/SameSite=Strict/);
   assert.equal((await request('/api/admin/bookings')).data.bookings.length,2);
  });
  await t.test('Одновременные подтверждения не создают пересекающиеся брони',async()=>{
   const results=await Promise.all([first,second].map(b=>request('/api/admin/bookings/'+b.data.id,'PATCH',{status:'confirmed'})));
   assert.deepEqual(results.map(r=>r.status).sort(),[200,409]);
   const data=(await request('/api/homes')).data;assert.equal(data.unavailable.length,1);
   assert.deepEqual(Object.keys(data.unavailable[0]).sort(),['end','homeId','start']);
   assert.equal((await request('/api/bookings','POST',booking())).status,409);
   assert.equal((await request('/api/bookings','POST',booking({start:'2099-11-13',end:'2099-11-14'}))).status,201);
  });
  await t.test('База и сеанс переживают перезапуск сервера',async()=>{
   await close();app=createApp({database,secure:false});await listen();base=`http://127.0.0.1:${app.server.address().port}`;
   assert.equal((await request('/api/admin/bookings')).data.bookings.length,3);
   assert.equal((await request('/api/homes')).data.unavailable.length,1);
  });
  await t.test('Отмена освобождает даты, выход отзывает доступ',async()=>{
   const list=(await request('/api/admin/bookings')).data.bookings;const confirmed=list.find(b=>b.status==='confirmed');
   assert.equal((await request('/api/admin/bookings/'+confirmed.id,'PATCH',{status:'cancelled'})).status,200);
   assert.equal((await request('/api/homes')).data.unavailable.length,0);
   assert.equal((await request('/api/bookings','POST',booking())).status,201);
   assert.equal((await request('/api/logout','POST',{})).status,200);
   assert.equal((await request('/api/admin/bookings')).status,401);
  });
  await t.test('Перебор пароля ограничен',async()=>{
   for(let i=0;i<10;i++)assert.equal((await request('/api/login','POST',{password:'wrong'})).status,401);
   assert.equal((await request('/api/login','POST',{password:'wrong'})).status,429);
  });
 }finally{await close();for(const file of fs.readdirSync(directory))fs.unlinkSync(path.join(directory,file));fs.rmdirSync(directory);}
});
