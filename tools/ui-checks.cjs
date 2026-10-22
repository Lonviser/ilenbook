async (page)=>{
 const checks=[];
 await page.evaluate(()=>{closeSheet();if(state.reader)exitReader();state.tab='library';state.query='';state.filter='all';render()});
 await page.getByRole('textbox',{name:'Поиск в библиотеке',exact:true}).fill('Чехов');
 if(await page.locator('[data-book]').count()!==2)throw Error('Author search');checks.push('Author search');
 await page.getByRole('textbox',{name:'Поиск в библиотеке',exact:true}).fill('Белые');
 await page.locator('[data-book]').click();if(!(await page.locator('#favorite').innerText()).includes('В избранном'))await page.locator('#favorite').click();
 if(!(await page.locator('#favorite').innerText()).includes('В избранном'))throw Error('Favorite not set');checks.push('Favorite toggling');
 await page.locator('#read-book').click();await page.locator('#article').waitFor();await page.waitForFunction(()=>state.reader&&!state.reader.restoring);
 await page.locator('#next-page').click();await page.locator('#add-bookmark').click();
 const before=await page.evaluate(()=>({chapter:meta('white-nights').chapter,offset:meta('white-nights').offset,bookmarks:meta('white-nights').bookmarks.length}));
 if(before.offset<=0||before.bookmarks<1)throw Error('Progress or bookmark not saved');checks.push('Paging and bookmark');
 await page.locator('#reader-settings').click();await page.getByRole('button',{name:'Сепия',exact:true}).click();
 if(await page.locator('.reader').getAttribute('data-theme')!=='sepia')throw Error('Theme not applied');checks.push('Reader sepia setting');
 await page.getByRole('button',{name:'Закрыть',exact:true}).click();await page.locator('.scrim').waitFor({state:'detached'});await page.locator('#exit-reader').click();await page.locator('.reader').waitFor({state:'detached'});
 await page.locator('[data-book="white-nights"]').click();await page.locator('#read-book').click();await page.locator('#article').waitFor();await page.waitForFunction(()=>state.reader&&!state.reader.restoring);
 const after=await page.evaluate(()=>({offset:meta('white-nights').offset,scroll:document.querySelector('#reading-area').scrollTop}));
 if(after.scroll<=0||Math.abs(after.offset-before.offset)>.03)throw Error('Position was not restored');checks.push('Restore reading position');
 await page.locator('#reader-search').click();await page.getByRole('textbox',{name:'Поиск по тексту',exact:true}).fill('ночь');
 if(await page.locator('[data-hit]').count()<1)throw Error('No text search results');await page.locator('[data-hit]').first().click();await page.locator('mark').waitFor();checks.push('Book search and navigation');
 await page.locator('#toc').click();if(await page.locator('[data-chapter]').count()!==6)throw Error('TOC');await page.locator('[data-chapter="1"]').click();checks.push('Chapter navigation');
 await page.screenshot({path:'output/playwright/android-reader-sepia.png'});
 await page.locator('#exit-reader').click();await page.getByRole('button',{name:'Избранное',exact:true}).click();if(await page.locator('[data-book="white-nights"]').count()!==1)throw Error('Favorites shelf');checks.push('Favorites shelf');
 return checks;
}
