async(page)=>await page.evaluate(async()=>{
 const originalApi=api,previousJobs=state.jobs,previousBooks=[...state.books],calls=[];const check=(ok,m)=>{if(!ok)throw Error(m)};
 try{
  api=(action,data)=>action==='download'?new Promise((resolve,reject)=>calls.push({data,resolve,reject})):originalApi(action,data);
  state.jobs=[{id:'queue-regression',title:'Fixture',url:'https://fixture.invalid/book',status:'queued'}];pumpDownloads();
  const job=state.jobs[0];job.status='cancelled';job.status='queued';pumpDownloads();
  check(calls.length===2&&calls[0].data.group!==calls[1].data.group,'Retry has a fresh cancellation group');
  calls[0].reject(Error('Cancelled old request'));await new Promise(r=>setTimeout(r,0));check(job.status==='loading','Old rejection cannot fail retry');
  calls[1].resolve({id:'queue-regression-book',title:'Fixture'});await new Promise(r=>setTimeout(r,0));check(job.status==='done'&&job.bookId==='queue-regression-book','Retry completes normally');
  return ['Distinct groups for retry','Cancelled callback cannot change new attempt','Retried download completes'];
 }finally{api=originalApi;state.jobs=previousJobs;state.books=previousBooks;save();render()}
})
