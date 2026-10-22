async(page)=>{
 return await page.evaluate(async()=>{
  const previous=prefs.statistics,checks=[];const assert=(ok,m)=>{if(!ok)throw Error(m)};
  try{
   closeSheet();if(state.reader)exitReader();prefs.statistics={started:Date.now(),days:{},completed:{},enabled:true};prefs.gesturesShown=true;await openReader('white-nights');await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));toggleReaderChrome(false);
   const now=Date.now();statsSession.lastTick=now-65000;statsSession.lastActivity=now-65000;statsSession.active=true;statsFlush(now);let totals=statsTotals([dayKey(now)]);assert(Math.abs(totals.seconds-65)<.2,'Active minute counted');checks.push('Active foreground reading time');
   statsSession.active=false;statsSession.lastTick=now-60000;statsSession.lastActivity=now;statsFlush(now);assert(Math.abs(statsTotals([dayKey(now)]).seconds-65)<.2,'Menu/paused time excluded');checks.push('Inactive time excluded');
   statsSession.active=true;statsSession.lastTick=now-10000;statsSession.lastActivity=now-STATS_IDLE_MS-10000;statsFlush(now);assert(Math.abs(statsTotals([dayKey(now)]).seconds-65)<.2,'Idle time excluded');checks.push('Idle cutoff');
   const before=statsTotals([dayKey(now)]).turns;turn(1);assert(statsTotals([dayKey(now)]).turns===before+1,'Page turn counted');statsMarkDone('white-nights');statsMarkDone('white-nights');assert(statsTotals([dayKey(now)]).completed===1,'Completion only once');checks.push('Turns and distinct completed books');
   const midnight=new Date();midnight.setHours(0,0,0,0);statsAddInterval(midnight.getTime()-10000,midnight.getTime()+10000,'split-fixture');assert(statsData().days[dayKey(midnight.getTime()-1)].books['split-fixture']===10&&statsData().days[dayKey(midnight.getTime())].books['split-fixture']===10,'Day split');checks.push('Intervals split at local midnight');
   statisticsDialog(7);assert(document.querySelectorAll('.stats-day').length===7,'Seven day history');assert(document.querySelector('#statistics-export'),'CSV action');checks.push('Statistics UI and CSV export');
   return checks;
  }finally{closeSheet();if(state.reader)exitReader();prefs.statistics=previous;save();render();}
 });
}
