// Public-domain starter library. Original Russian texts, no contemporary edition artwork.
const fs=require('fs'),cp=require('child_process'),path=require('path');
const root=path.resolve(__dirname,'..'),assets=path.join(root,'app/src/main/assets');
const specs=[
 ['white-nights','Белые ночи','Фёдор Достоевский','Белые ночи (Достоевский)','night'],
 ['kashtanka','Каштанка','Антон Чехов','Каштанка (Чехов)','garden'],
 ['asya','Ася','Иван Тургенев','Ася (Тургенев)','sun'],
 ['overcoat-1938','Шинель','Николай Гоголь','Шинель (Гоголь)/ПСС 1938 (СО)','ink'],
 ['lady-dog','Дама с собачкой','Антон Чехов','Дама с собачкой (Чехов)','sand']
];
function text(s){return s.replace(/<span class="mw-editsection">[\s\S]*?<\/span><\/span>/g,'').replace(/<sup\b[^>]*>[\s\S]*?<\/sup>/g,'').replace(/<br\s*\/?\s*>/gi,'\n').replace(/<[^>]*>/g,'').replace(/&#(x[0-9a-f]+|[0-9]+);/gi,(_,c)=>String.fromCodePoint(c[0]==='x'?parseInt(c.slice(1),16):Number(c))).replace(/&nbsp;/g,' ').replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&quot;/g,'"').replace(/\[править\]/g,'').trim()}
(async()=>{
 const settled=await Promise.allSettled(specs.map(async([id,title,author,page,art])=>{
  const source=path.join(root,'.tools',id+'-source.json');
  if(!fs.existsSync(source))await new Promise((resolve,reject)=>cp.execFile('curl.exe',['-sS','-L','--fail','--max-time','60','https://ru.wikisource.org/w/api.php?action=parse&page='+encodeURIComponent(page)+'&prop=text&format=json','-o',source],err=>err?reject(err):resolve()));
  const json=JSON.parse(fs.readFileSync(source,'utf8'));if(!json.parse)throw Error(JSON.stringify(json));
  let html=json.parse.text['*'];html=html.split('<div class="printfooter"')[0].split('<div class="licenseContainer')[0];
  // The header contains public-domain notices; the novel begins in .indent.
  const proseStart=html.indexOf('<div class="indent">');if(proseStart>=0)html=html.slice(proseStart);
  const chapters=[];let current={title,text:''};
  for(const m of html.matchAll(/<(h[1-6]|p)\b[^>]*>([\s\S]*?)<\/\1>/g)){
   const t=text(m[2]);if(!t)continue;
   if(m[1]!=='p'){if(current.text.length>200)chapters.push(current);current={title:t,text:''}}
   else if(!/^(См\.|Источник|Это произведение|Автор умер|Текст доступен|Поскольку Российская|Исключительное право|Если произведение)/.test(t))current.text+=t+'\n\n';
  }
  if(current.text.trim().length>200)chapters.push(current);
  if(chapters.length&&chapters[0].text.includes('Дата создания:'))chapters.shift();
  if(!chapters.length)throw Error('No chapters: '+title);
  console.log(title,chapters.length,chapters.reduce((n,c)=>n+c.text.length,0));
  return {id,title,author,format:'ТЕКСТ',art,added:1700000000000+specs.findIndex(s=>s[0]===id),source:'Викитека · '+page+' · https://ru.wikisource.org/wiki/'+encodeURIComponent(page),chapters};
 }));
 const failed=settled.filter(s=>s.status==='rejected');if(failed.length){failed.forEach(s=>console.error(s.reason));process.exitCode=1;return}
 const books=settled.map(s=>s.value);
 const welcome=JSON.parse(fs.readFileSync(path.join(assets,'welcome.json'),'utf8'));
 books.push(welcome);fs.writeFileSync(path.join(assets,'seed-books.json'),JSON.stringify(books));fs.writeFileSync(path.join(assets,'welcome.json'),JSON.stringify(welcome));
})();
