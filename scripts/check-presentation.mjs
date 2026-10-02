// Read-only verification of presentation data, including across a server restart.
import assert from 'node:assert/strict';
import {demoPassword} from './demo-credentials.mjs';
import {mkdir,writeFile,readFile,readdir} from 'node:fs/promises';

import {examples} from './presentation-data.mjs';
const base='http://127.0.0.1:8080';
class Session {
  cookies=new Map(); token=null;
  async call(path,method='GET',body,expected=200){
    if(method!=='GET'&&!this.token)this.token=await this.call('/api/auth/csrf');
    const headers={Cookie:[...this.cookies].map(([k,v])=>k+'='+v).join('; ')};
    if(method!=='GET')headers[this.token.headerName]=this.token.token;
    if(body&&!(body instanceof FormData)&&!(body instanceof URLSearchParams)){headers['Content-Type']='application/json';body=JSON.stringify(body)}
    const response=await fetch(base+path,{method,body,headers,redirect:'manual'});
    for(const cookie of response.headers.getSetCookie()){const [pair]=cookie.split(';');const at=pair.indexOf('=');this.cookies.set(pair.slice(0,at),pair.slice(at+1));}
    const raw=await response.text();let result;try{result=JSON.parse(raw)}catch{result=raw}
    assert.equal(response.status,expected,`${method} ${path}: ${raw.slice(0,200)}`);return result;
  }
  async login(email,password,portal='user'){await this.call('/api/auth/login','POST',new URLSearchParams({username:email,password,portal}));this.token=null;return this.call('/api/auth/me');}
}
const expected=JSON.parse(await readFile('.local/presentation-result.json','utf8'));
const demo=new Session();await demo.login('demo@example.com',demoPassword('CAMPUS_PRESENTATION_PASSWORD'));
const dashboard=await demo.call('/api/dashboard');
assert.deepEqual(dashboard.stats,expected.stats,'Saved dashboard counts changed');
const reports=await demo.call('/api/my-items');
assert.deepEqual(reports.map(i=>i.id).sort((a,b)=>a-b),expected.reportIds.sort((a,b)=>a-b));
for(const item of reports){assert(item.images.length>0);const response=await fetch(base+item.images[0].url);assert.equal(response.status,200);assert(response.headers.get('content-type').startsWith('image/'));await response.arrayBuffer();}
console.log(JSON.stringify({persistence:'PASS',stats:dashboard.stats,illustratedReports:reports.length},null,2));
