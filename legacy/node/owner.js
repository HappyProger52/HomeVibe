'use strict';
const readline=require('node:readline/promises');
const {Writable}=require('node:stream');
const path=require('node:path');
const {openDatabase,passwordRecord}=require('./server');
let muted=false;
const output=new Writable({write(chunk,encoding,done){if(!muted)process.stdout.write(chunk,encoding);done();}});
const rl=readline.createInterface({input:process.stdin,output,terminal:true});
async function secret(prompt){process.stdout.write(prompt);muted=true;try{return await rl.question('');}finally{muted=false;process.stdout.write('\n');}}
(async()=>{
  const password=await secret('Новый пароль владельца (12–128 символов, ввод скрыт): ');
  if(password.length<12||password.length>128)throw Error('Пароль должен содержать от 12 до 128 символов.');
  if(password!==await secret('Повторите пароль: '))throw Error('Пароли не совпадают.');
  const db=openDatabase(path.join(__dirname,'data','homevibe.sqlite'));
  const record=passwordRecord(password);
  try{db.exec('BEGIN IMMEDIATE');db.prepare('INSERT OR REPLACE INTO owner VALUES (1,?,?)').run(record.salt,record.digest);db.exec('DELETE FROM sessions; COMMIT');}finally{db.close();}
  console.log('Пароль сохранён. Теперь можно войти в кабинет на сайте. Предыдущие сеансы завершены.');
})().catch(error=>{console.error(error.message);process.exitCode=1;}).finally(()=>rl.close());
