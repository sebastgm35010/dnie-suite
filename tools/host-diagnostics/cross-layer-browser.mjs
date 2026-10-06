import {Cdp,recorder} from './edge-capture.mjs';
import {appendFileSync,writeFileSync} from 'node:fs';
const [port,target,output]=process.argv.slice(2);
if(!/^\d+$/.test(port)||!/^http:\/\/127\.0\.0\.1:\d+\/$/.test(target))throw Error('Local fixture only');
const base=`http://127.0.0.1:${port}`;
const tabs=await (await fetch(base+'/json/list')).json();
const tab=tabs.find(t=>t.type==='page'&&t.url==='about:blank');
if(!tab)throw Error('Isolated blank page required');
const events=[];
const page=await Cdp.connect(tab.webSocketDebuggerUrl,recorder((event,fields)=>{
 const row={time:Date.now(),category:'edge',event,fields};events.push(row);appendFileSync(output,JSON.stringify(row)+'\n');
}));
try{
 await page.send('Page.enable');await page.send('Network.enable');await page.send('Runtime.enable');
 console.log('Cross-layer CDP domains ready');
 await page.send('Page.navigate',{url:target},20000);
 console.log('Cross-layer page navigation completed');
 let result;
 for(let i=0;i<100;i++){
  const r=await page.send('Runtime.evaluate',{expression:'window.fixtureResult',returnByValue:true});
  if(r.result?.value){result=r.result.value;break;}
  await new Promise(resolve=>setTimeout(resolve,100));
 }
 await new Promise(resolve=>setTimeout(resolve,200));
 if(!result?.success||!events.some(e=>e.event==='response'&&e.fields.status===200))throw Error('Browser/native fixture failed');
 writeFileSync(output+'.result.json',JSON.stringify({success:true,syntheticOnly:true,networkEvents:events.length}));
 console.log('PASS Edge -> native PCSC -> TLS -> Kotlin mobile engine');
}finally{
 page.close();const version=await (await fetch(base+'/json/version')).json();
 const browser=await Cdp.connect(version.webSocketDebuggerUrl);await browser.send('Browser.close').catch(()=>{});browser.close();
}
