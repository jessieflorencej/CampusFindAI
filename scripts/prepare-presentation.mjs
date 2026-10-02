// Creates persistent, explicitly fictional presentation records through the normal APIs.
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
const email='demo@example.com',password=demoPassword('CAMPUS_PRESENTATION_PASSWORD');
const admin=new Session(),demo=new Session(),finder=new Session();
await admin.login('admin@campus.edu',demoPassword('CAMPUS_DEMO_ADMIN_PASSWORD'),'admin');
await finder.login('priya@campus.edu',demoPassword('CAMPUS_DEMO_STUDENT_PASSWORD'));
let people=(await admin.call('/api/admin')).users;
if(!people.some(u=>u.email===email))await demo.call('/api/auth/register','POST',{
  name:'Alex Morgan · Presentation Demo',email,collegeId:'PRESENTATION-001',department:'Computer Science',year:'3',phone:'9000012345',password
});
let auth=await demo.login(email,password);
if(!auth.user.emailVerified){
  assert.equal(auth.mailMode,'outbox','This setup is only for the local demonstration outbox.');
  const files=(await readdir('.local/mail')).sort().reverse();let token;
  for(const file of files){const mail=await readFile('.local/mail/'+file,'utf8');if(mail.includes('To: '+email+'\n')&&mail.includes('/verify-email?token=')){token=mail.match(/verify-email\?token=([^\s]+)/)[1];break;}}
  assert(token,'Local verification link not found');await demo.call('/api/auth/verify-email','POST',{token});
}
auth=await demo.call('/api/auth/me');
if(!auth.user.identityVerified)await admin.call('/api/admin/users/'+auth.user.id,'POST',{action:'VERIFY'});
async function upload(session,asset,purpose='ITEM'){
  const form=new FormData();form.append('file',new Blob([await readFile('documentation/demo-images/'+asset+'.png')],{type:'image/png'}),'demo-illustration-'+asset+'.png');form.append('purpose',purpose);
  return (await session.call('/api/uploads','POST',form)).id;
}
async function report(session,index,type){
  const [title,category,brand,model,color,location,days,value,asset,mark]=examples[index];
  const existing=(await session.call('/api/my-items')).find(i=>i.title===title&&i.type===type);
  if(existing)return existing;
  const date=new Date();date.setDate(date.getDate()-days);
  const data={title,type,category,brand,model,color,location,value,date:date.toLocaleDateString('en-CA'),time:['09:20','11:45','13:10','16:30','10:05','17:15','08:50','12:25','15:40','14:00'][index],
    description:'Presentation sample: '+title+'. '+(type==='LOST'?'Last used during campus activities at ':'Safely collected from ')+location+'. Picture is an illustrative demo image.',
    building:location,floor:index%2?'First floor':'Ground floor',room:['Reading area','Seminar hall','Table 8','Room 204','Computer lab','Court entrance','Waiting area','Cycle stand','Lab corridor','Room 105'][index],lastSeen:location,
    storageLocation:'Campus Security Office',privateDetails:mark,serial:'DEMO-2026-'+(4100+index),imageIds:[await upload(session,asset)]};
  return session.call('/api/items','POST',data);
}
const lost=[],found=[];
for(let i=0;i<5;i++)lost[i]=await report(demo,i,'LOST');
for(let i=5;i<9;i++)await report(demo,i,'FOUND');
for(let i=0;i<3;i++)found[i]=await report(finder,i,'FOUND');
const returnLost=await report(demo,9,'LOST'),returnFound=await report(finder,9,'FOUND');
async function claim(index,lostItem,foundItem){
  // Finder and claimant lists are flattened without depending on display section names.
  const list=await demo.call('/api/claims');
  const rows=Object.values(list).filter(Array.isArray).flat();
  const existing=rows.find(c=>c.item?.id===foundItem.id);
  if(existing)return demo.call('/api/claims/'+existing.id);
  return demo.call('/api/claims','POST',{itemId:foundItem.id,lostItemId:lostItem.id,answers:'Fictional presentation evidence: '+examples[index][9]+'. I used my '+examples[index][0]+' at '+examples[index][5]+'. This demonstrates human ownership review.',serial:'DEMO-2026-'+(4100+index),lossLocation:examples[index][5],evidenceIds:[await upload(demo,examples[index][8],'EVIDENCE')]});
}
// Complete the historic return before leaving two active review examples.
let returned=await claim(9,returnLost,returnFound);
if(returned.status==='FINDER_REVIEW')returned=await finder.call('/api/claims/'+returned.id+'/finder','POST',{response:'YES',note:'Fictional demo: details reviewed against the sample item.'});
if(returned.status==='ADMIN_REVIEW')returned=await admin.call('/api/claims/'+returned.id+'/review','POST',{decision:'APPROVE',note:'Presentation sample only; illustrates independent review and safe return.',location:'Security Office'});
returned=await demo.call('/api/claims/'+returned.id);
if(returned.status==='APPROVED')returned=await finder.call('/api/claims/'+returned.id+'/handover','POST',{code:returned.handoverCode});
if(returned.status==='ITEM_HANDOVER')await demo.call('/api/claims/'+returned.id+'/confirm','POST');
await claim(0,lost[0],found[0]);
let pending=await claim(1,lost[1],found[1]);
if(pending.status==='FINDER_REVIEW')await finder.call('/api/claims/'+pending.id+'/finder','POST',{response:'YES',note:'Presentation example ready for administrator review.'});
const profile=await demo.call('/api/profile');
if(!profile.profileImageId)await demo.call('/api/profile','PUT',{name:profile.name,department:profile.department,year:profile.year,phone:profile.phone,profileImageId:await upload(demo,'backpack','PROFILE')});
const before=(await demo.call('/api/dashboard')).stats;
await demo.call('/api/auth/logout','POST');demo.token=null;await demo.login(email,password);
const after=(await demo.call('/api/dashboard')).stats;assert.deepEqual(after,before,'Sign-out and sign-in must preserve dashboard data');
assert(after.lost>=5&&after.found>=4&&after.matches>=3&&after.pendingClaims>=2&&after.returned>=1);
const reports=await demo.call('/api/my-items');assert(reports.every(i=>i.images.length>0));
for(const item of reports){const response=await fetch(base+item.images[0].url);assert.equal(response.status,200);assert(response.headers.get('content-type').startsWith('image/'));await response.arrayBuffer();}
await mkdir('.local',{recursive:true});await writeFile('.local/presentation-result.json',JSON.stringify({email,stats:after,reportIds:reports.map(i=>i.id),returnedClaim:returned.id,pendingClaim:pending.id,verifiedAt:new Date().toISOString()},null,2));
console.log(JSON.stringify({email,stats:after,illustratedReports:reports.length,signInPersistence:'PASS'},null,2));
