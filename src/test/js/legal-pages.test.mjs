import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const STATIC = new URL('../../main/resources/static/', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const read = (path) => readFileSync(join(STATIC, path), 'utf8');
const privacy = read('privacidade/index.html');
const terms = read('termos/index.html');

function htmlFiles(dir) {
    return readdirSync(dir).flatMap((name) => {
        const path = join(dir, name);
        if (statSync(path).isDirectory()) return htmlFiles(path);
        return name.endsWith('.html') ? [path] : [];
    });
}

// Spec 024 CA4
test('the privacy policy covers the data, the purpose, the providers, the retention, the rights and the contact', () => {
    for (const heading of ['Quais dados guardamos', 'Para que usamos', 'Com quem os dados passam', 'Por quanto tempo guardamos', 'Seus direitos']) {
        assert.ok(privacy.includes(`<h2>${heading}</h2>`), heading);
    }
    for (const provider of ['Resend', 'Google', 'Hostinger', 'Cloudflare']) {
        assert.ok(privacy.includes(provider), provider);
    }
    assert.match(privacy, /até 14 dias/);
    assert.match(privacy, /até 30 dias/);
    assert.ok(privacy.includes('href="/app/#/suporte"'));
});

// Spec 024 CA5
test('both pages show the date of the last update', () => {
    for (const page of [privacy, terms]) {
        assert.match(page, /Última atualização: <time datetime="\d{4}-\d{2}-\d{2}">/);
    }
});

// Spec 024 CA2
test('every page with the site footer links the privacy policy and the terms', () => {
    const pages = htmlFiles(STATIC).filter((path) => readFileSync(path, 'utf8').includes('<footer class="site-footer">'));
    assert.ok(pages.length >= 15, 'site, help pages and the two legal pages');
    for (const path of pages) {
        const html = readFileSync(path, 'utf8');
        assert.ok(html.includes('href="/privacidade/"'), path);
        assert.ok(html.includes('href="/termos/"'), path);
    }
});

test('the legal pages are described for search and link previews', () => {
    for (const page of [privacy, terms]) {
        assert.match(page, /<title>[^<]+ — moneyPilot<\/title>/);
        assert.match(page, /<meta name="description" content="[^"]+"/);
        assert.ok(page.includes('property="og:title"'));
        assert.ok(page.includes('<main id="conteudo"'));
        assert.ok(page.includes('class="site-skip"'));
    }
});

// Spec 024: fonts come from the site, not from Google
test('no page or stylesheet loads fonts from Google', () => {
    for (const path of [...htmlFiles(STATIC), join(STATIC, 'css/app.css'), join(STATIC, 'css/site.css')]) {
        assert.doesNotMatch(readFileSync(path, 'utf8'), /fonts\.googleapis\.com|fonts\.gstatic\.com/, path);
    }
    const css = read('css/app.css');
    assert.match(css, /url\(\/fonts\/manrope-latin\.woff2\)/);
    assert.match(css, /url\(\/fonts\/bricolage-grotesque-latin\.woff2\)/);
    for (const file of ['manrope-latin.woff2', 'bricolage-grotesque-latin.woff2', 'OFL-manrope.txt', 'OFL-bricolage-grotesque.txt']) {
        assert.ok(statSync(join(STATIC, 'fonts', file)).size > 1000, file);
    }
});
