'use strict';
const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const {randomBytes,randomUUID,scryptSync,timingSafeEqual,createHash}=require('node:crypto');
const {DatabaseSync}=require('node:sqlite');
const {homes}=require('./catalog');
const hash=v=>createHash('sha256').update(v).digest('hex');
function passwordRecord(password){const salt=randomBytes(16).toString('hex');return {salt,digest:scryptSync(password,salt,64).toString('hex')};}
function openDatabase(filename){
 if(filename!==':memory:')fs.mkdirSync(path.dirname(filename),{recursive:true});
 const db=new DatabaseSync(filename);
 db.exec(`PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;
 CREATE TABLE IF NOT EXISTS owner(id INTEGER PRIMARY KEY CHECK(id=1),salt TEXT NOT NULL,digest TEXT NOT NULL);
 CREATE TABLE IF NOT EXISTS sessions(token TEXT PRIMARY KEY,expires INTEGER NOT NULL);
 CREATE TABLE IF NOT EXISTS bookings(id TEXT PRIMARY KEY,homeId TEXT NOT NULL,start TEXT NOT NULL,end TEXT NOT NULL,guests INTEGER NOT NULL,name TEXT NOT NULL,phone TEXT NOT NULL,note TEXT NOT NULL,total INTEGER NOT NULL,status TEXT NOT NULL CHECK(status IN ('pending','confirmed','cancelled')),createdAt TEXT NOT NULL,requestKey TEXT UNIQUE NOT NULL);
 CREATE INDEX IF NOT EXISTS booking_dates ON bookings(homeId,status,start,end);`);
 return db;
}
function validDate(v){return typeof v==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(v)&&Number.isFinite(Date.parse(v))&&new Date(v).toISOString().slice(0,10)===v;}
const nights=(a,b)=>(Date.parse(b)-Date.parse(a))/86400000;
const today=()=>new Intl.DateTimeFormat('en-CA',{timeZone:'Europe/Moscow',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
function createApp({database=path.join(__dirname,'data','homevibe.sqlite'),secure=process.env.NODE_ENV==='production'}={}){
 const db=openDatabase(database),limits=new Map();
 const fail=(status,message)=>Object.assign(new Error(message),{status});
 const conflict=(homeId,start,end,id='')=>db.prepare("SELECT id FROM bookings WHERE homeId=? AND status='confirmed' AND start<? AND end>? AND id<>? LIMIT 1").get(homeId,end,start,id);
 function rate(req,bucket,max){const now=Date.now(),key=bucket+':'+req.socket.remoteAddress;for(const [k,v] of limits)if(v.until<now)limits.delete(k);const entry=limits.get(key)||{count:0,until:now+900000};entry.count++;limits.set(key,entry);if(entry.count>max)throw fail(429,'Слишком много попыток. Попробуйте через 15 минут.');}
 function session(req){const m=(req.headers.cookie||'').match(/(?:^|;\s*)hv_session=([a-f0-9]{64})(?:;|$)/);return m?db.prepare('SELECT token FROM sessions WHERE token=? AND expires>?').get(hash(m[1]),Date.now()):null;}
 function authorize(req){if(!session(req))throw fail(401,'Войдите в кабинет владельца.');}
 async function body(req){if(!(req.headers['content-type']||'').startsWith('application/json'))throw fail(415,'Требуется JSON.');const chunks=[];let size=0;for await(const chunk of req){size+=chunk.length;if(size>8192)throw fail(413,'Слишком большой запрос.');chunks.push(chunk);}try{const r=JSON.parse(Buffer.concat(chunks).toString('utf8'));if(!r||Array.isArray(r)||typeof r!=='object')throw Error();return r;}catch{throw fail(400,'Некорректный запрос.');}}
 const server=http.createServer(async(req,res)=>{
  res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('Referrer-Policy','same-origin');res.setHeader('X-Frame-Options','DENY');
  res.setHeader('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
  const json=(code,value)=>{res.writeHead(code,{'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify(value));};
  try{
   const url=new URL(req.url,'http://localhost');
   const localPort=server.address().port;
   const allowedHosts=process.env.PUBLIC_ORIGIN?[new URL(process.env.PUBLIC_ORIGIN).host]:[`127.0.0.1:${localPort}`,`localhost:${localPort}`];
   if(!allowedHosts.includes(req.headers.host))throw fail(403,'Недопустимый адрес сервера.');
   if(url.pathname.startsWith('/api/')){
    if(!['GET','HEAD'].includes(req.method)){const allowed=process.env.PUBLIC_ORIGIN||`${secure?'https':'http'}://${req.headers.host}`;if(req.headers.origin!==allowed)throw fail(403,'Недопустимый источник запроса.');}
    if(req.method==='GET'&&url.pathname==='/api/homes')return json(200,{homes,unavailable:db.prepare("SELECT homeId,start,end FROM bookings WHERE status='confirmed' AND end>=?").all(today())});
    if(req.method==='GET'&&url.pathname==='/api/session')return json(200,{authenticated:!!session(req),configured:!!db.prepare('SELECT id FROM owner WHERE id=1').get()});
    if(req.method==='POST'&&url.pathname==='/api/login'){
     rate(req,'login',10);const input=await body(req);if(typeof input.password!=='string'||input.password.length>128)throw fail(400,'Некорректный пароль.');
     const owner=db.prepare('SELECT * FROM owner WHERE id=1').get();if(!owner)throw fail(503,'Сначала создайте пароль владельца: npm run owner.');
     if(!timingSafeEqual(scryptSync(input.password,owner.salt,64),Buffer.from(owner.digest,'hex')))throw fail(401,'Неверный пароль.');
     const token=randomBytes(32).toString('hex');db.prepare('DELETE FROM sessions WHERE expires<=?').run(Date.now());const previous=session(req);if(previous)db.prepare('DELETE FROM sessions WHERE token=?').run(previous.token);
     db.prepare('INSERT INTO sessions VALUES (?,?)').run(hash(token),Date.now()+28800000);res.setHeader('Set-Cookie',`hv_session=${token}; HttpOnly; SameSite=Strict; Path=/; Max-Age=28800${secure?'; Secure':''}`);return json(200,{ok:true});
    }
    if(req.method==='POST'&&url.pathname==='/api/logout'){const current=session(req);if(current)db.prepare('DELETE FROM sessions WHERE token=?').run(current.token);res.setHeader('Set-Cookie',`hv_session=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0${secure?'; Secure':''}`);return json(200,{ok:true});}
    if(req.method==='POST'&&url.pathname==='/api/bookings'){
     rate(req,'booking',30);const input=await body(req),home=homes.find(h=>h.id===input.homeId),{start,end,guests,requestKey}=input;
     if(!home||!validDate(start)||!validDate(end)||start<today()||nights(start,end)<1||nights(start,end)>365)throw fail(400,'Выберите дом и корректные даты (от 1 до 365 ночей).');
     if(!Number.isInteger(guests)||guests<1||guests>home.guests)throw fail(400,'Недопустимое количество гостей.');
     if(typeof input.name!=='string'||input.name.trim().length<2||input.name.length>80)throw fail(400,'Введите имя, от 2 до 80 символов.');
     if(typeof input.phone!=='string'||input.phone.length>30||!/^\+?[\d\s()\-]+$/.test(input.phone)||!/^\d{10,15}$/.test(input.phone.replace(/\D/g,'')))throw fail(400,'Введите корректный телефон.');
     if(typeof input.note!=='string'||input.note.length>600||typeof requestKey!=='string'||!/^[a-f0-9-]{36}$/.test(requestKey))throw fail(400,'Некорректные данные заявки.');
     db.exec('BEGIN IMMEDIATE');try{
      const existing=db.prepare('SELECT id,status FROM bookings WHERE requestKey=?').get(requestKey);if(existing){db.exec('COMMIT');return json(200,existing);}
      if(conflict(home.id,start,end))throw fail(409,'Дом уже занят на эти даты. Выберите другие даты.');
      const id=randomUUID();db.prepare('INSERT INTO bookings VALUES (?,?,?,?,?,?,?,?,?,?,?,?)').run(id,home.id,start,end,guests,input.name.trim(),input.phone.trim(),input.note.trim(),nights(start,end)*home.price,'pending',new Date().toISOString(),requestKey);db.exec('COMMIT');return json(201,{id,status:'pending'});
     }catch(error){db.exec('ROLLBACK');throw error;}
    }
    if(req.method==='GET'&&url.pathname==='/api/admin/bookings'){authorize(req);return json(200,{bookings:db.prepare('SELECT id,homeId,start,end,guests,name,phone,note,total,status,createdAt FROM bookings ORDER BY createdAt').all()});}
    const match=url.pathname.match(/^\/api\/admin\/bookings\/([a-f0-9-]{36})$/);
    if(req.method==='PATCH'&&match){authorize(req);const {status}=await body(req);if(!['confirmed','cancelled'].includes(status))throw fail(400,'Некорректный статус.');
     db.exec('BEGIN IMMEDIATE');try{const b=db.prepare('SELECT * FROM bookings WHERE id=?').get(match[1]);if(!b)throw fail(404,'Заявка не найдена.');if(b.status==='cancelled'||(status==='confirmed'&&b.status!=='pending'))throw fail(409,'Статус уже изменён. Обновите список.');if(status==='confirmed'&&(b.start<today()||conflict(b.homeId,b.start,b.end,b.id)))throw fail(409,'Нельзя подтвердить: даты прошли или пересекаются с другим бронированием.');db.prepare('UPDATE bookings SET status=? WHERE id=?').run(status,b.id);db.exec('COMMIT');return json(200,{ok:true});}catch(error){db.exec('ROLLBACK');throw error;}
    }
    throw fail(404,'Страница не найдена.');
   }
   const files={'/':'index.html','/index.html':'index.html','/styles.css':'styles.css','/app.js':'app.js','/cabin.svg':'cabin.svg'},file=files[url.pathname];if(!file||!['GET','HEAD'].includes(req.method))throw fail(404,'Страница не найдена.');
   const types={'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'text/javascript; charset=utf-8','.svg':'image/svg+xml'},content=fs.readFileSync(path.join(__dirname,file));res.writeHead(200,{'Content-Type':types[path.extname(file)]});res.end(req.method==='HEAD'?undefined:content);
  }catch(error){if(!res.headersSent)json(error.status||500,{error:error.status?error.message:'Ошибка сервера. Попробуйте ещё раз.'});else res.end();}
 });
 server.requestTimeout=15000;server.headersTimeout=10000;server.on('close',()=>db.close());return {server,db};
}
if(require.main===module){const {server}=createApp();server.listen(Number(process.env.PORT)||3000,'127.0.0.1',()=>console.log('HomeVibe: http://127.0.0.1:'+(process.env.PORT||3000)));}
module.exports={createApp,openDatabase,passwordRecord};

