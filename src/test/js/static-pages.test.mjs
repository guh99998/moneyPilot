import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const STATIC = new URL('../../main/resources/static/', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const site = readFileSync(join(STATIC, 'index.html'), 'utf8');
const app = readFileSync(join(STATIC, 'app/index.html'), 'utf8');

// CA8
test('the site has a title, a description and link preview tags', () => {
    assert.match(site, /<title>[^<]+<\/title>/);
    assert.match(site, /<meta name="description" content="[^"]+"/);
    for (const tag of ['og:title', 'og:description', 'og:image', 'og:type']) {
        assert.ok(site.includes(`property="${tag}"`), tag);
    }
    assert.ok(site.includes('name="twitter:card"'));
});

test('the site sections come in the order of the spec', () => {
    const order = ['id="site-what"', 'id="site-features"', 'id="site-start"', 'id="site-faq"', '<footer'];
    const positions = order.map((marker) => site.indexOf(marker));
    assert.ok(positions.every((p) => p >= 0), 'every section exists');
    assert.deepEqual([...positions].sort((a, b) => a - b), positions);
});

// CA10
test('the footer links support, the project support, help, GitHub and privacy', () => {
    const footer = site.slice(site.indexOf('<footer'));
    for (const href of ['/ajuda/', '/app/#/suporte', '/app/#/apoie', 'github.com/guh99998/moneyPilot']) {
        assert.ok(footer.includes(`href="${href}`) || footer.includes(href), href);
    }
});

// CA1
test('the session links work without JavaScript and both lead to /app/', () => {
    const nav = site.slice(site.indexOf('id="site-session"'), site.indexOf('</nav>'));
    assert.match(nav, />Entrar</);
    assert.match(nav, />Criar conta</);
    assert.equal((nav.match(/href="\/app\/"/g) || []).length, 2);
});

// CA4, CA5
test('the app page loads css and js by absolute path', () => {
    assert.ok(app.includes('id="app"'));
    assert.doesNotMatch(app, /(?:href|src)="(?:css|js|image)\//);
});

test('no view uses a relative path for the logo', () => {
    const offenders = [];
    const walk = (dir) => readdirSync(dir).forEach((name) => {
        const path = join(dir, name);
        if (statSync(path).isDirectory()) walk(path);
        else if (name.endsWith('.js') && /['"]image\/money-pilot-logo/.test(readFileSync(path, 'utf8'))) offenders.push(path);
    });
    walk(join(STATIC, 'js'));
    assert.deepEqual(offenders, []);
});
