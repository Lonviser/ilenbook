async(page)=>{
 return await page.evaluate(async()=>{const result=[];for(const [name,url] of [['Gutenberg ru','https://www.gutenberg.org/ebooks/search.opds/?query=l.ru'],['Wolne Lektury','https://wolnelektury.pl/opds/search/?q=lalka'],['Ebooks gratuits','https://www.ebooksgratuits.com/opds/feed.php?mode=search&query=Verne']]){try{const c=await api('catalog',{url});result.push({name,count:c.entries.length,title:c.title})}catch(e){result.push({name,error:e.message})}}return result});
}
