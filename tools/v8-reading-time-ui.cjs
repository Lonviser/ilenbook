async(page)=>{
 const original=await page.evaluate(()=>JSON.parse(JSON.stringify(prefs))),checks=[],widths=[];
 const assert=(ok,message)=>{if(!ok)throw Error(message)};
 try{
  await page.evaluate(async()=>{closeSheet();if(state.reader)exitReader();prefs.gesturesShown=true;prefs.statistics={enabled:true,days:{},completed:{},started:Date.now()};prefs.settings={...prefs.settings,readingMode:'pages',size:20,font:'literata',line:1.65,margin:22};const b=state.books.find(b=>b.id==='white-nights')||state.books.find(b=>b.chapterCount>1);delete meta(b.id).readingOverrides;meta(b.id).chapter=0;meta(b.id).offset=0;delete meta(b.id).anchor;await openReader(b.id)});
  await page.waitForFunction(()=>state.reader&&!state.reader.restoring&&state.reader.pages>5);
  for(const width of [320,360,412,600]){
   await page.setViewportSize({width,height:740});await page.waitForTimeout(180);
   const r=await page.evaluate(()=>{toggleReaderChrome(true);const e=$('#time-remaining'),b=$('#reader-menu-toggle'),f=$('.reader-foot').getBoundingClientRect(),m=$('.reader-menu').getBoundingClientRect();return {width:innerWidth,text:e.textContent,overflow:e.scrollWidth>e.clientWidth||b.scrollHeight>b.clientHeight,overlap:m.bottom>f.top+1,accessible:e.getAttribute('aria-label')}});
   assert(!r.overflow&&!r.overlap&&r.text.includes('Книга ≈')&&r.text.includes('Глава ≈'),JSON.stringify(r));widths.push(r);
  }
  checks.push('Book and chapter time fit 320/360/412/600 px with menu above footer');
  await page.evaluate(()=>{toggleReaderChrome(false);loadChapter(0,0)});
  await page.waitForFunction(()=>!state.reader.restoring);
  const afterTurn=await page.evaluate(()=>{window.dispatchEvent(new Event('resize'));turn(1);return state.reader.page});
  await page.waitForTimeout(140);
  assert(await page.evaluate(expected=>state.reader.page===expected,afterTurn),'Deferred resize restored a stale page');checks.push('Deferred resize preserves the latest reading position');
  await page.evaluate(()=>loadChapter(0,0));await page.waitForFunction(()=>!state.reader.restoring);
  const learned=await page.evaluate(()=>{
   const labels=[];statsSetActive(true);readingPaceStamp();
   const first=$('#time-remaining').textContent;
   for(let i=0;i<3;i++){statsSession.readingSeconds=(statsSession.readingSeconds||0)+60;turn(1);labels.push($('#time-remaining').textContent)}
   return {first,labels,pace:ReadingTimeModel.pace(prefs.statistics),saved:JSON.parse(localStorage.getItem(STORE)).statistics.pace};
  });
  assert(learned.pace.personal&&learned.pace.samples===3&&learned.saved.samples.length===3,JSON.stringify(learned));checks.push('Consecutive active pages calibrate and persist personal speed');
  const guards=await page.evaluate(()=>{
   const count=()=>ReadingTimeModel.pace(prefs.statistics).samples,before=count();turn(1);const quick=count();turn(-1);const back=count();toggleReaderChrome(true);statsSession.readingSeconds+=60;turn(1);const menu=count();toggleReaderChrome(false);loadChapter(0,.3);return {before,quick,back,menu};
  });
  assert(guards.before===guards.quick&&guards.quick===guards.back&&guards.back===guards.menu,JSON.stringify(guards));checks.push('Quick flips, backwards turns and menu time do not teach speed');
  await page.waitForFunction(()=>!state.reader.restoring);
  const disabled=await page.evaluate(()=>{prefs.statistics.enabled=false;statsSetActive(false);readingPaceStamp();const count=ReadingTimeModel.pace(prefs.statistics).samples;statsSession.readingSeconds+=60;turn(1);return {count,after:ReadingTimeModel.pace(prefs.statistics).samples,personal:ReadingTimeModel.pace(prefs.statistics).personal,title:$('#time-remaining').title}});
  assert(!disabled.personal&&disabled.count===disabled.after&&disabled.title.includes('Начальная оценка'),JSON.stringify(disabled));checks.push('Disabled statistics use generic estimate without collecting samples');
  const long=await page.evaluate(()=>{const r=state.reader,previous=r.progressBook;r.progressBook={lengths:[2400000],before:[0],total:2400000};const c=r.chapter;r.chapter=0;meta(r.book.id).offset=0;updateReadingTime();const e=$('#time-remaining'),result={text:e.textContent,overflow:e.scrollWidth>e.clientWidth};r.progressBook=previous;r.chapter=c;updateReaderProgress();return result});
  assert(!long.overflow&&long.text.includes('ч'),JSON.stringify(long));checks.push('Long books use compact approximate hours');
  await page.evaluate(()=>readerChangeText({readingMode:'scroll'}));await page.waitForFunction(()=>!state.reader.restoring);
  const scroll=await page.evaluate(()=>{const a=$('#reading-area');a.scrollTop=(a.scrollHeight-a.clientHeight)/2;updateReaderProgress();const r=state.reader,estimate=ReadingTimeModel.estimate(r.progressBook,r.chapter,meta(r.book.id).offset,prefs.statistics);return {offset:meta(r.book.id).offset,text:$('#time-remaining').textContent,expected:`Книга ≈ ${ReadingTimeModel.label(estimate.bookMinutes)} · Глава ≈ ${ReadingTimeModel.label(estimate.chapterMinutes)}`}});
  assert(Math.abs(scroll.offset-.5)<.01&&scroll.text===scroll.expected,JSON.stringify(scroll));checks.push('Scroll reading updates remaining time from the current position');
  return {checks,widths,learned,guards,disabled,long,scroll};
 }finally{await page.evaluate(previous=>{closeSheet();if(state.reader)exitReader();prefs=previous;save();render()},original)}
}
