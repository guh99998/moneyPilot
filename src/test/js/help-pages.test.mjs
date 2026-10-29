import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync, readdirSync } from 'node:fs';

import { installFakeDom } from './fake-dom.mjs';

installFakeDom();

const { assistantPrompt } = await import('../../main/resources/static/js/views/ai-guide.js');

const STATIC = new URL('../../main/resources/static/', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const AJUDA = STATIC + 'ajuda/';
const read = (name) => readFileSync(AJUDA + name, 'utf8');
const tutorials = readdirSync(AJUDA).filter((name) => name.endsWith('.html') && name !== 'index.html');
const main = (html) => html.slice(html.indexOf('<main'), html.indexOf('</main>'));
const h2s = (html) => [...main(html).matchAll(/<h2>([^<]+)<\/h2>/g)].map((m) => m[1]);

const AREAS = ['primeiros-passos', 'contas', 'categorias', 'lancamentos', 'orcamentos', 'a-pagar-e-receber',
    'previsao', 'visao-geral', 'alertas', 'perfil-e-seguranca', 'suporte', 'usando-com-ia'];

// CA1
test('the index links every area and every link points to a file that exists', () => {
    const hrefs = [...main(read('index.html')).matchAll(/href="([^"]+)"/g)].map((m) => m[1]);

    assert.deepEqual(hrefs, AREAS.map((slug) => `/ajuda/${slug}.html`));
    for (const href of hrefs) assert.ok(existsSync(STATIC + href.slice(1)), href);
    assert.deepEqual([...tutorials].sort(), AREAS.map((slug) => `${slug}.html`).sort());
});

// CA2
test('every tutorial has the three sections in order, a trail to the index and no content images', () => {
    for (const name of tutorials) {
        const html = read(name);
        const body = main(html);

        assert.deepEqual(h2s(html).slice(0, 3), ['Para que serve', 'Passo a passo', 'Dúvidas comuns'], name);
        assert.equal((body.match(/<h1>/g) || []).length, 1, `${name}: one h1`);
        assert.match(body, /<ol>/, name);
        assert.match(body, /<details>/, name);
        assert.match(body, /<a href="\/ajuda\/">Ajuda<\/a>/, `${name}: trail to the index`);
        assert.doesNotMatch(body, /<img/i, `${name}: no images`);
        assert.match(html, /<title>[^<]+<\/title>/, name);
        assert.match(html, /<meta name="description" content="[^"]+"/, name);
    }
});

test('every internal page link points to a file that exists', () => {
    const outside = ['/app/', '/swagger-ui.html', '/v3/api-docs', '/'];
    for (const name of ['index.html', ...tutorials]) {
        for (const [, href] of read(name).matchAll(/href="(\/[^"#?]*)/g)) {
            if (outside.includes(href) || href.startsWith('/app/')) continue;
            const target = href.endsWith('/') ? href.slice(1) + 'index.html' : href.slice(1);
            assert.ok(existsSync(STATIC + target), `${name}: ${href}`);
        }
    }
});

test('pages use absolute asset paths and share the same top and footer', () => {
    const slice = (html, from, to) => html.slice(html.indexOf(from), html.indexOf(to));
    const first = read('index.html');
    for (const name of ['index.html', ...tutorials]) {
        const html = read(name);
        assert.doesNotMatch(html, /(?:href|src)="(?:css|js|image)\//, name);
        assert.match(html, /<body data-page="help">/, name);
        assert.equal(slice(html, '<header', '</header>'), slice(first, '<header', '</header>'), `${name}: header`);
        assert.equal(slice(html, '<footer', '</footer>'), slice(first, '<footer', '</footer>'), `${name}: footer`);
        assert.match(slice(html, '<footer', '</footer>'), /href="\/ajuda\/"/, name);
    }
});

// CA4
test('"Usar com IA" has the simple path and the text to paste first, and "Para desenvolvedores" last', () => {
    const html = read('usando-com-ia.html');
    const headings = h2s(html);
    const devs = html.indexOf('<h2>Para desenvolvedores</h2>');

    assert.equal(headings.at(-1), 'Para desenvolvedores');
    assert.deepEqual(headings.slice(0, 3), ['Para que serve', 'Passo a passo', 'Dúvidas comuns']);
    assert.ok(html.indexOf('id="assistant-prompt"') < devs);
    assert.ok(html.indexOf('refreshTokenInBody</code>') > devs);
    assert.ok(html.indexOf('swagger-ui.html') > devs);
});

test('the text to paste in the page is the one the app builds, with an example address', () => {
    const html = read('usando-com-ia.html');
    const box = html.match(/<textarea[^>]*id="assistant-prompt"[^>]*>([\s\S]*?)<\/textarea>/)[1];
    const decoded = box.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');

    assert.equal(decoded, assistantPrompt('https://seu-endereco.exemplo'));
});
