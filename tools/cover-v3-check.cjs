async(page)=>{return await page.evaluate(async()=>{
 const b=await api('download',{url:'http://10.0.2.2:4190/sample.fb2',type:'application/fb2+xml',title:'Проверка FB2',source:'Тест',coverUrl:'http://10.0.2.2:4191/cover.png'});
 try{if(b.cover||!b.coverUrl)throw Error('Fixture must have no embedded cover');const enriched=await api('findCover',{id:b.id});const read=await api('read',{id:b.id});if(!enriched.cover?.startsWith('data:image/jpeg')||read.cover!==enriched.cover)throw Error('Cover was not persisted');return {catalogFallback:true,persistedInBook:true,coverBytes:enriched.cover.length}}finally{await api('delete',{id:b.id})}
})}
