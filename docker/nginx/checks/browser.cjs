const assert = require('node:assert/strict');
const { chromium } = require('/app/node_modules/playwright');
const site = process.env.SITE_URL;

async function httpChecks() {
  for (const [path, type] of [
    ['/', 'text/html'], ['/app.css', 'text/css'], ['/app.js', 'javascript'],
    ['/vendor/fontawesome-free/webfonts/fa-solid-900.woff2', 'font/woff2']
  ]) {
    const response = await fetch(site + path);
    assert.equal(response.status, 200, path);
    assert.ok(response.headers.get('content-type').includes(type), path + ' MIME');
    assert.equal(response.headers.get('cache-control'), 'no-store', path + ' cache');
  }
  assert.equal((await fetch(site + '/missing-file.js')).status, 404);
  const response = await fetch(site + '/api/v1/catalog', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sourceId: 'CONTRACT_WEEKLY' })
  });
  if (process.env.CHECK_OUTAGE === 'true') {
    assert.equal(response.status, 502);
    console.log('OUTAGE_OK static=200 missing=404 api=502');
    return false;
  }
  assert.equal(response.status, 200);
  const redirect = await fetch(site + '/admin', {
    redirect: 'manual', headers: {
      'X-Forwarded-Host': 'untrusted.invalid', 'X-Forwarded-Proto': 'https',
      'X-Forwarded-Prefix': '/untrusted', 'Forwarded': 'host=untrusted.invalid;proto=https'
    }
  });
  assert.equal(redirect.status, 302);
  assert.equal(redirect.headers.get('location'), site + '/login');
  console.log('HTTP_OK assets MIME cache 404 API protected-admin forwarded-headers');
  return true;
}

(async () => {
  if (!await httpChecks()) return;
  const browser = await chromium.launch({ executablePath: chromium.executablePath(), headless: true, args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(site + '/');
    await page.waitForFunction(() => document.querySelector('#chart circle') !== null);
    assert.ok((await page.locator('#summary').innerText()).length > 0);
    await page.locator('#calc-mode').selectOption('volume');
    await page.locator('#volume').fill('300');
    await page.waitForFunction(() => !document.querySelector('#calculate').disabled);
    const calculation = page.waitForResponse(r => r.url().endsWith('/api/v1/refueling/estimate') && r.request().method() === 'POST');
    await page.locator('#calculate').click();
    const result = await (await calculation).json();
    assert.equal(result.totalLiters, 300);
    assert.equal(result.estimatedCost, Math.round(300 * result.pricePerLiter * 100) / 100);
    await page.waitForFunction(() => document.querySelector('#estimate-result strong') !== null);

    await page.goto(site + '/login');
    await page.locator('#username').fill('admin');
    await page.locator('#password').fill('admin');
    await page.locator('#login-submit').click();
    await page.waitForURL(site + '/admin');
    await page.locator('#open-import').waitFor({ state: 'visible' });
    assert.equal(await page.locator('#admin-name').innerText(), 'admin');
    await page.locator('#admin-logout-form button').click();
    await page.waitForURL(site + '/');
    const session = await page.request.get(site + '/api/v1/admin/session');
    assert.equal((await session.json()).authenticated, false);
    assert.deepEqual(errors, []);
    console.log('BROWSER_OK chart budget login admin logout no-script-errors');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
