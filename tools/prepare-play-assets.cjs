/** Render the existing app logo and exact app screenshots using an existing Playwright runtime. */
const fs=require('node:fs');
const path=require('node:path');
const {pathToFileURL}=require('node:url');
const {chromium}=require('playwright');
const root=path.resolve(__dirname,'..');
const output=path.resolve(process.argv[2] || path.join(root,'release/play-store/2026-10-12'));
fs.mkdirSync(output,{recursive:true});
const xml=fs.readFileSync(path.join(root,'app/src/main/res/drawable/ic_launcher_foreground.xml'),'utf8');
const attribute=(text,name,fallback)=>text.match(new RegExp(`android:${name}="([^"]+)"`))?.[1] || fallback;
const paths=[...xml.matchAll(/<path\s+([\s\S]*?)\/>/g)].map(m=>{
 const p=m[1];
 const color=attribute(p,'fillColor','none');
 return `<path d="${attribute(p,'pathData','')}" fill="${color==='#00000000'?'none':color}" stroke="${attribute(p,'strokeColor','none')}" stroke-width="${attribute(p,'strokeWidth','0')}" stroke-linecap="${attribute(p,'strokeLineCap','butt')}" stroke-linejoin="${attribute(p,'strokeLineJoin','miter')}"/>`;
}).join('');
const group=xml.match(/<group\s+([\s\S]*?)>/)?.[1] || '';
const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108"><rect width="108" height="108" fill="#0B3D2E"/><g transform="translate(${attribute(group,'translateX','0')} ${attribute(group,'translateY','0')}) scale(${attribute(group,'scaleX','1')} ${attribute(group,'scaleY','1')})">${paths}</g></svg>`;
fs.writeFileSync(path.join(output,'app-icon.svg'),svg);
const font=pathToFileURL(path.join(root,'app/src/main/assets/fonts/ReadexPro.ttf')).href;
const home=path.join(output,'screenshots/01-home.png');
const homeUrl=fs.existsSync(home)?pathToFileURL(home).href:null;
const css=`@font-face{font-family:Readex;src:url('${font}')}*{box-sizing:border-box}body{margin:0;font-family:Readex,Arial,sans-serif}.feature{width:1024px;height:500px;background:linear-gradient(120deg,#0B3D2E,#135B40);color:#F5F7F0;display:flex;align-items:center;justify-content:center;gap:48px;padding:38px 80px}.copy{width:550px}.name{font-size:23px;letter-spacing:.02em;color:#C8F25A;margin-bottom:22px}.title{font-size:45px;line-height:1.35;font-weight:700;margin:0 0 20px}.detail{font-size:20px;line-height:1.65;color:#D9E5DB;margin:0}.tags{display:flex;gap:10px;margin-top:24px}.tag{font-size:15px;padding:8px 12px;border:1px solid #80B197;border-radius:20px}.shot{height:400px;width:225px;object-fit:contain;flex:none}.origin{font-size:14px;color:#BCEB4A;margin-top:12px}.sample{font-size:12px;opacity:.8;margin-top:20px}`;
async function render(page,filename,html,width,height){
 const source=path.join(output,filename.replace('.png','.html'));
 fs.writeFileSync(source,html);
 await page.setViewportSize({width,height});await page.goto(pathToFileURL(source).href);
 await page.evaluate(()=>document.fonts.ready);
 await page.screenshot({path:path.join(output,filename),omitBackground:false});
}
(async()=>{
 const options={headless:true};if(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE)options.executablePath=process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE;
 const browser=await chromium.launch(options);
 try{
  const page=await browser.newPage();
  await page.route(/^https?:/,route=>route.abort());
  await render(page,'app-icon.png',`<html><body style="margin:0;background:#0B3D2E">${svg}</body></html>`,512,512);
  if(!homeUrl){console.log('Icon rendered. Real home screenshot required before feature graphics.');return;}
  await render(page,'feature-ar.png',`<html lang="ar" dir="rtl"><style>${css}</style><body><main class="feature"><div class="copy"><div class="name" dir="ltr">Follow Money</div><h1 class="title">اعرف فلوسك<br>راحت فين</h1><p class="detail">معاملات من رسائل البنك بإذنك،<br>وميزانية تراجعها على جهازك.</p><div class="tags"><span class="tag">التقاط SMS تلقائي</span><span class="tag">مراجعة محلية</span></div><div class="origin">بكل فخر، صُنع في مصر</div><div class="sample">واجهة التطبيق ببيانات تجريبية</div></div><img class="shot" src="${homeUrl}" alt="واجهة Follow Money الحقيقية ببيانات مصطنعة"></main></body></html>`,1024,500);
  await render(page,'feature-en.png',`<html lang="en"><style>${css}</style><body><main class="feature"><div class="copy"><div class="name">Follow Money</div><h1 class="title">Know your<br>spending.</h1><p class="detail">Supported bank SMS, captured with consent.<br>Review your money and budgets locally.</p><div class="tags"><span class="tag">Automatic SMS capture</span><span class="tag">Local review</span></div><div class="origin">Proudly made in Egypt</div><div class="sample">Actual app interface with invented demo data</div></div><img class="shot" src="${homeUrl}" alt="Actual Follow Money home screen with invented demo data"></main></body></html>`,1024,500);
  console.log('Rendered 512x512 icon and Arabic/English 1024x500 feature graphics from existing assets.');
 }finally{await browser.close();}
})().catch(error=>{console.error(error.message);process.exitCode=1;});
