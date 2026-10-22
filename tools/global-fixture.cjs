const http=require('http'),fs=require('fs');const image=fs.readFileSync(require('path').resolve(__dirname,'../docs/ilenbook-library.png'));
http.createServer((req,res)=>{
 if(req.url.startsWith('/cover.png')){res.setHeader('Content-Type','image/png');res.end(image);return}
 if(req.url.startsWith('/fail')){res.writeHead(503);res.end('Unavailable');return}
 const send=()=>{res.setHeader('Content-Type','application/atom+xml');res.end(`<?xml version="1.0" encoding="UTF-8"?><feed xmlns="http://www.w3.org/2005/Atom"><title>Проверка общего поиска</title><id>urn:fixture</id><entry><id>urn:shared</id><title>Общая книга</title><author><name>Тестовый Автор</name></author><link rel="http://opds-spec.org/image" type="image/png" href="/cover.png"/><link rel="http://opds-spec.org/acquisition/open-access" type="application/epub+zip" href="/sample.epub"/></entry><entry><id>urn:second</id><title>Вторая книга</title><author><name>Другой Автор</name></author><link rel="http://opds-spec.org/acquisition/open-access" type="text/plain" href="/sample.txt"/></entry></feed>`)};
 if(req.url.startsWith('/slow'))setTimeout(send,3000);else send();
}).listen(4191,'0.0.0.0',()=>console.log('Global fixture on :4191'));
