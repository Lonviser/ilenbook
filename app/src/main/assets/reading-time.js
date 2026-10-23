'use strict';

function readingPacePlace(){
 const r=state.reader,a=$('#reading-area');if(!r?.progressBook||!a)return null;
 const m=meta(r.book.id),s=effectiveReadingSettings(),p=r.progressBook;
 return {chapter:r.chapter,page:r.page,offset:m.offset,characters:p.before[r.chapter]+p.lengths[r.chapter]*m.offset,signature:[s.font,s.size,s.line,s.margin,s.readingMode,a.clientWidth,a.clientHeight,r.pages].join('|')};
}
function readingPaceStamp(){const r=state.reader,place=readingPacePlace();if(r&&place&&statsSession?.bookId===r.book.id&&!r.restoring)r.paceStamp={...place,seconds:statsSession.readingSeconds||0,session:statsSession}}
function updateReadingTime(){
 const r=state.reader,node=$('#time-remaining');if(!r?.progressBook||!node)return;
 const estimate=ReadingTimeModel.estimate(r.progressBook,r.chapter,meta(r.book.id).offset,prefs.statistics);
 node.textContent=`Книга ≈ ${ReadingTimeModel.label(estimate.bookMinutes)} · Глава ≈ ${ReadingTimeModel.label(estimate.chapterMinutes)}`;
 node.title=`Примерно ${estimate.bookMinutes} мин до конца книги и ${estimate.chapterMinutes} мин до конца главы. ${estimate.personal?'По вашему темпу чтения':'Начальная оценка: 1200 знаков в минуту; после нескольких прочитанных страниц темп уточняется'}.`;
 node.setAttribute('aria-label',node.title);
 if(!r.paceStamp)readingPaceStamp();
}
const remainingTimeProgress=updateReaderProgress;
updateReaderProgress=function(){remainingTimeProgress();updateReadingTime()};
const remainingTimeTurn=turn;
turn=function(direction){
 const r=state.reader;if(!r)return remainingTimeTurn(direction);
 statsFlush();const from=readingPacePlace(),stamp=r.paceStamp,session=statsSession,seconds=session?.readingSeconds||0,eligible=statsCanRead()&&!r.restoring;
 remainingTimeTurn(direction);if(state.reader!==r)return;
 const to=readingPacePlace();if(!from||!to||from.characters===to.characters)return;
 if(eligible&&direction===1&&stamp?.session===session&&stamp.chapter===from.chapter&&stamp.signature===from.signature&&Math.abs(stamp.characters-from.characters)<1&&to.chapter===from.chapter&&to.signature===from.signature&&to.characters>from.characters){
  if(ReadingTimeModel.addSample(statsData(),to.characters-from.characters,seconds-stamp.seconds))save();
 }
 readingPaceStamp();updateReadingTime();
};
