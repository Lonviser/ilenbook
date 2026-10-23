'use strict';
const ReadingTimeModel=(()=>{
 const DEFAULT_CPM=1200,MAX_SAMPLES=40;
 function valid(sample){return sample&&Number.isFinite(sample.characters)&&Number.isFinite(sample.seconds)&&sample.characters>0&&sample.seconds>=4&&sample.seconds<=180&&sample.characters*60/sample.seconds>=100&&sample.characters*60/sample.seconds<=5000}
 function samples(statistics){return (Array.isArray(statistics?.pace?.samples)?statistics.pace.samples:[]).slice(-MAX_SAMPLES).filter(valid)}
 function pace(statistics){
  const history=samples(statistics),seconds=history.reduce((n,s)=>n+s.seconds,0),characters=history.reduce((n,s)=>n+s.characters,0),personal=statistics?.enabled!==false&&history.length>=3&&seconds>=90&&characters>=900;
  return {cpm:personal?Math.max(200,Math.min(3000,characters*60/seconds)):DEFAULT_CPM,personal,samples:history.length};
 }
 function addSample(statistics,characters,seconds){
  const next={characters,seconds};if(!statistics||statistics.enabled===false||!valid(next))return false;
  statistics.pace={samples:[...samples(statistics),next].slice(-MAX_SAMPLES)};return true;
 }
 function estimate(progress,chapter,offset,statistics){
  const lengths=Array.isArray(progress?.lengths)?progress.lengths:[],index=Math.max(0,Math.min(lengths.length-1,Math.trunc(Number(chapter)||0))),fraction=Math.max(0,Math.min(1,Number(offset)||0)),speed=pace(statistics);
  const chapterLeft=Math.max(0,(lengths[index]||0)*(1-fraction)),later=Number.isFinite(progress?.total)&&Number.isFinite(progress?.before?.[index])?Math.max(0,progress.total-progress.before[index]-(lengths[index]||0)):lengths.slice(index+1).reduce((n,length)=>n+Math.max(0,Number(length)||0),0),bookLeft=chapterLeft+later;
  return {...speed,bookMinutes:Math.ceil(bookLeft/speed.cpm),chapterMinutes:Math.ceil(chapterLeft/speed.cpm),bookCharacters:bookLeft,chapterCharacters:chapterLeft};
 }
 function label(minutes){return minutes>999?Math.ceil(minutes/60)+' ч':minutes+' мин'}
 return {DEFAULT_CPM,pace,addSample,estimate,label};
})();
if(typeof module!=='undefined'&&module.exports)module.exports=ReadingTimeModel;
