import net from 'node:net';
import { guardDatabase } from './fixture-guard.mjs';

// Minimal RESP2 client: one command per connection, no Redis dependency or global destructive commands.
export function parseResp(buffer, offset=0) {
  const end=buffer.indexOf('\r\n',offset);if(end<0) return null;
  const prefix=buffer[offset],value=buffer.subarray(offset+1,end).toString();let next=end+2;
  if(prefix===43||prefix===58) return {value:prefix===58?Number(value):value,next};
  if(prefix===45) throw new Error('Dedicated Redis command rejected');
  if(prefix===36) {const n=Number(value);if(n===-1)return {value:null,next};if(buffer.length<next+n+2)return null;return {value:buffer.subarray(next,next+n).toString(),next:next+n+2};}
  if(prefix===42) {const out=[];for(let i=0;i<Number(value);i++) {const r=parseResp(buffer,next);if(!r)return null;out.push(r.value);next=r.next;}return {value:out,next};}
  throw new Error('Invalid Redis protocol response');
}
async function command(args) {
  return new Promise((resolve,reject)=>{
    const s=net.connect({host:'127.0.0.1',port:16379});let buffer=Buffer.alloc(0);
    const fail=()=>{s.destroy();const e=new Error('Dedicated Redis communication failed');e.name='FixtureEnvironmentError';reject(e);};
    s.setTimeout(3000);s.once('error',fail);s.once('timeout',fail);
    s.once('connect',()=>s.write(Buffer.concat([Buffer.from(`*${args.length}\r\n`),...args.flatMap(a=>{const b=Buffer.from(String(a));return [Buffer.from(`$${b.length}\r\n`),b,Buffer.from('\r\n')];})])));
    s.on('data',b=>{buffer=Buffer.concat([buffer,b]);try {const r=parseResp(buffer);if(r){s.destroy();resolve(r.value);}}catch {fail();}});
  });
}
export async function countOwnedNamespace(database) {
  guardDatabase('127.0.0.1',13306,database);const prefix=database+':';let cursor='0';const keys=new Set();
  do {const response=await command(['SCAN',cursor,'MATCH',prefix+'*','COUNT','200']);cursor=response[0];for(const key of response[1]) {if(!key.startsWith(prefix))throw new Error('Redis namespace guard refused key');keys.add(key);}}while(cursor!=='0');return keys.size;
}
export async function clearOwnedNamespace(database) {
  guardDatabase('127.0.0.1',13306,database);
  const prefix=database+':';let cursor='0';
  do {const response=await command(['SCAN',cursor,'MATCH',prefix+'*','COUNT','200']);cursor=response[0];const found=response[1];if(found.some(k=>!k.startsWith(prefix)))throw new Error('Redis namespace guard refused key');if(found.length)await command(['DEL',...found]);}while(cursor!=='0');
}
