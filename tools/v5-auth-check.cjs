async(page)=>{
 const pages=page.context().pages();const login=pages.find(p=>/^https:\/\/(passport|oauth|id)\.yandex\./.test(p.url()));
 if(!login)throw Error('Login WebView not found');
 await login.waitForFunction(()=>document.readyState==='complete'&&document.body.innerText.length>20);
 return await login.evaluate(()=>({host:location.hostname,hasReaderBridge:typeof window.Native!=='undefined',hasLoginContent:/Яндекс|Войти|Log in|Yandex|Sign in/i.test(document.body.innerText),formFields:document.querySelectorAll('input').length}));
}
