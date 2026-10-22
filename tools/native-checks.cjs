async (page)=>{
 const result=await page.evaluate(async()=>{
  const checks=[];
  const c=await api('catalog',{url:'http://10.0.2.2:4190/opds'});if(c.entries.length!==2||!c.search)throw Error('OPDS parse failed');checks.push('Native HTTP + OPDS entries, search template and relative URLs');
  for(const e of c.entries){const b=await api('download',{...e.downloads[0],title:e.title,source:'Integration fixture'});const full=await api('read',{id:b.id});if(full.chapters.length!==2||!full.chapters[0].text.includes('Абзац'))throw Error('Native import failed: '+e.title);checks.push('Native '+b.format+' download, parsing, storage and reading');await api('delete',{id:b.id})}
  const t=await api('download',{url:'http://10.0.2.2:4190/sample.txt',title:'Тест TXT',type:'text/plain'});if(t.format!=='TXT')throw Error('TXT parse');await api('delete',{id:t.id});checks.push('Native TXT download and delete');
  let rejected=false;try{await api('download',{url:'http://10.0.2.2:4190/wrong.txt',title:'wrong',type:'text/plain'})}catch{rejected=true}if(!rejected)throw Error('HTML accepted as a book');checks.push('Native HTML error page rejected');
  return checks;
 });return result;
}
