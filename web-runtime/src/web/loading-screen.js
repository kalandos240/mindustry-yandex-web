/* Shared boot UI for standalone, Yandex Games and Playgama builds. */
(() => {
    'use strict';
    const root = document.documentElement;
    const panel = document.getElementById('mindustry-loader');
    const message = document.getElementById('mindustry-loading-message');
    const bar = document.getElementById('mindustry-loading-progress');
    const fill = document.getElementById('mindustry-loading-fill');
    const percent = document.getElementById('mindustry-loading-percent');
    const retry = document.getElementById('mindustry-loading-retry');
    const words = {
        en: {prepare:'Preparing game…', platform:'Connecting platform and loading saves…', runtime:'Loading game engine…', assets:'Loading game resources…', starting:'Starting Mindustry…', ready:'Ready', error:'Unable to load the game. Check your connection and try again.', slow:'Loading is taking longer than usual. You can wait or retry.', retry:'Retry', loading:'Loading'},
        ru: {prepare:'Подготовка игры…', platform:'Подключение платформы и загрузка сохранений…', runtime:'Загрузка игрового движка…', assets:'Загрузка ресурсов…', starting:'Запуск Mindustry…', ready:'Готово', error:'Не удалось загрузить игру. Проверьте подключение и попробуйте ещё раз.', slow:'Загрузка занимает больше времени, чем обычно. Можно подождать или повторить.', retry:'Повторить', loading:'Загрузка'}
    };
    let value = 0, stage = 'prepare', counts = '', failed = false, complete = false;
    function text(){
        const locale = root.getAttribute('data-mindustry-locale') || navigator.language || 'en';
        const w = words[locale.startsWith('ru') ? 'ru' : 'en'];
        message.textContent = w[stage] + counts;
        retry.textContent = w.retry;
        bar.setAttribute('aria-label', w.loading);
        panel.setAttribute('aria-label', 'Mindustry — ' + w.loading);
    }
    function update(next, name, loaded, total){
        if(failed || complete) return;
        value = Math.max(value, Math.min(99, Math.floor(Number(next) || 0)));
        stage = name;
        counts = total ? ' ' + loaded + ' / ' + total : '';
        fill.style.width = value + '%';
        percent.textContent = value + '%';
        bar.setAttribute('aria-valuenow', String(value));
        text();
    }
    function fail(error){
        if(complete || failed) return;
        failed = true;
        clearTimeout(stallTimer);
        stage = 'error'; counts = '';
        panel.hidden = false;
        panel.setAttribute('aria-busy', 'false');
        retry.hidden = false;
        text();
        if(error) console.error('Mindustry loading:', error);
    }
    const stallTimer = setTimeout(() => {
        if(complete || failed) return;
        stage = 'slow'; counts = ''; retry.hidden = false; text();
    }, 90000);
    retry.addEventListener('click', () => location.reload());
    function sync(){
        const state = root.getAttribute('data-mindustry-web');
        if(state === 'error') { fail(); return; }
        if(state === 'ready' && !failed && !complete){
            complete = true;
            clearTimeout(stallTimer);
            fill.style.width = '100%'; percent.textContent = '100%';
            bar.setAttribute('aria-valuenow', '100');
            stage = 'ready'; counts = ''; text();
            // Hide only after the runtime has verified rendered frames.
            panel.setAttribute('aria-busy', 'false');
            panel.hidden = true;
            observer.disconnect();
            return;
        }
        text();
    }
    const observer = new MutationObserver(sync);
    observer.observe(root, {attributes:true, attributeFilter:['data-mindustry-web', 'data-mindustry-locale']});
    window.addEventListener('error', event => {
        if(!complete) fail(event.error || event.message || 'Resource load failed');
    }, true);
    window.addEventListener('unhandledrejection', event => { if(!complete) fail(event.reason); });
    globalThis.__mindustryLoading = {update, fail};
    text(); sync();
})();
