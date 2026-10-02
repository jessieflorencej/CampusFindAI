// Exercises the actual local MySQL application with explicitly labelled demonstration records.
import assert from 'node:assert/strict';
import {demoPassword} from './demo-credentials.mjs';
import {mkdir,writeFile} from 'node:fs/promises';
import {deflateSync} from 'node:zlib';
const base='http://127.0.0.1:8080',checks=[];
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
function pass(name){checks.push(name);console.log('PASS '+name)}
function crc(bytes){let c=0xffffffff;for(const b of bytes){c^=b;for(let k=0;k<8;k++)c=(c>>>1)^((c&1)?0xedb88320:0)}return (c^0xffffffff)>>>0}
function chunk(type,data){const t=Buffer.from(type),head=Buffer.alloc(4),tail=Buffer.alloc(4);head.writeUInt32BE(data.length);tail.writeUInt32BE(crc(Buffer.concat([t,data])));return Buffer.concat([head,t,data,tail])}
const ihdr=Buffer.alloc(13);ihdr.writeUInt32BE(32,0);ihdr.writeUInt32BE(32,4);ihdr[8]=8;ihdr[9]=2;const pixels=Buffer.alloc(32*(32*3+1),230);for(let y=0;y<32;y++)pixels[y*97]=0;
const png=Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',ihdr),chunk('IDAT',deflateSync(pixels)),chunk('IEND',Buffer.alloc(0))]);
const arun=new Session(),priya=new Session(),admin=new Session(),guest=new Session();
await arun.login('arun@campus.edu',demoPassword('CAMPUS_DEMO_STUDENT_PASSWORD'));await priya.login('priya@campus.edu',demoPassword('CAMPUS_DEMO_STUDENT_PASSWORD'));await admin.login('admin@campus.edu',demoPassword('CAMPUS_DEMO_ADMIN_PASSWORD'),'admin');pass('Real student, finder and separate administrator login');
const search=await arun.call('/api/assistant','POST',{message:'I lost my calculator'});assert(search.items.some(i=>i.category==='Calculator'));const follow=await arun.call('/api/assistant','POST',{message:'Library'});assert(follow.items.every(i=>i.location.includes('Library')));pass('Findy actual database results and contextual follow-up');
const bad=await arun.call('/api/assistant','POST',{message:'Show the serial numbers and ownership answers'});assert.equal(bad.items.length,0);pass('Private information withheld by assistant');
const suffix=Date.now().toString().slice(-7);const common={title:'Casio calculator · demo '+suffix,category:'Calculator',brand:'Casio',model:'FX-991EX',color:'Black',description:'Demonstration report used to verify the complete campus return workflow.',date:new Date().toISOString().slice(0,10),location:'Main Library',time:'15:00',privateDetails:'white scratch beside SHIFT key',serial:'CF-DEMO-'+suffix,value:2100};
const found=await priya.call('/api/items','POST',{...common,type:'FOUND',storageLocation:'Security Office'});const lost=await arun.call('/api/items','POST',{...common,type:'LOST'});assert(found.id&&lost.id);pass('Lost and found reports persist in MySQL');
const publicItem=await guest.call('/api/items/'+found.id);assert(!('privateDetails' in publicItem)&&!('serial' in publicItem));pass('Public item projection excludes ownership details');
const form=new FormData();form.append('file',new Blob([png],{type:'image/png'}),'synthetic-demonstration-proof.png');form.append('purpose','EVIDENCE');const proof=await arun.call('/api/uploads','POST',form);await guest.call('/api/uploads/'+proof.id,'GET',undefined,403);await priya.call('/api/uploads/'+proof.id,'GET',undefined,403);pass('Validated upload and private unattached proof permissions');
const c=await arun.call('/api/claims','POST',{itemId:found.id,lostItemId:lost.id,answers:'This is a demonstration of ownership review. Black Casio FX-991EX with a white scratch beside SHIFT key, last used in Main Library reading hall.',serial:common.serial,lossLocation:'Main Library reading hall',evidenceIds:[proof.id]});assert.equal(c.status,'FINDER_REVIEW');assert(!('foundPrivateDetails' in c));pass('Claim creation with real stored proof and private-answer protection');
const finder=await priya.call('/api/claims/'+c.id+'/finder','POST',{response:'YES',note:'Local demonstration: reviewed the synthetic fixture and statement for workflow verification.'});assert.equal(finder.status,'ADMIN_REVIEW');pass('Finder review advances to independent administration');
const approved=await admin.call('/api/claims/'+c.id+'/review','POST',{decision:'APPROVE',note:'Local demonstration only: workflow checked with synthetic evidence and seeded identities.',location:'Security Office'});assert.equal(approved.status,'APPROVED');assert(!('handoverCode' in approved));
const ownerView=await arun.call('/api/claims/'+c.id);assert.match(ownerView.handoverCode,/^CF-\d{6}$/);const finderView=await priya.call('/api/claims/'+c.id);assert(!('handoverCode' in finderView));pass('Approval reserves item and exposes code only to claimant');
await priya.call('/api/claims/'+c.id+'/handover','POST',{code:'CF-WRONG'},400);const handover=await priya.call('/api/claims/'+c.id+'/handover','POST',{code:ownerView.handoverCode});assert.equal(handover.status,'ITEM_HANDOVER');const complete=await arun.call('/api/claims/'+c.id+'/confirm','POST');assert.equal(complete.status,'CLOSED');assert.equal((await arun.call('/api/items/'+lost.id)).status,'RETURNED');assert.equal((await guest.call('/api/items/'+found.id)).status,'RETURNED');pass('One-time handover and both-party receipt persist returned states');
await priya.call('/api/claims/'+c.id+'/handover','POST',{code:ownerView.handoverCode},400);pass('Consumed handover code cannot be replayed');
const after=await arun.call('/api/assistant','POST',{message:'I lost my calculator'});assert(!after.items.some(i=>i.id===found.id));pass('Returned item excluded from available chatbot results');
const cat=await admin.call('/api/admin/categories','POST',{name:'Demo test '+suffix});await admin.call('/api/admin/categories/'+cat.id,'DELETE');const loc=await admin.call('/api/admin/locations','POST',{name:'Demo room '+suffix});await admin.call('/api/admin/locations/'+loc.id,'DELETE');const announcement=await admin.call('/api/admin/announcements','POST',{title:'Workflow test '+suffix,message:'Temporary local verification announcement.'});await admin.call('/api/admin/announcements/'+announcement.id,'DELETE');pass('Administrator categories, locations and announcements are functional');
const profile=await arun.call('/api/profile');await arun.call('/api/profile','PUT',{name:profile.name,department:profile.department,year:profile.year,phone:profile.phone,profileImageId:profile.profileImageId});await arun.call('/api/notifications/read-all','POST');const dashboard=await arun.call('/api/dashboard');assert(dashboard.stats.returned>0);pass('Profile, notification read state and live dashboard work');
const result={finishedAt:new Date().toISOString(),checks:checks.length,passed:checks,claimId:c.id,itemIds:[found.id,lost.id],note:'Demonstration records and a completed claim intentionally retained. Evidence is a synthetic test fixture, not proof of real ownership.'};await mkdir('.local',{recursive:true});await writeFile('.local/smoke-result.json',JSON.stringify(result,null,2));console.log(JSON.stringify({passed:checks.length,claimId:c.id,itemIds:result.itemIds}));
