// Renders one HTML page to a PNG at an exact pixel size:
//   node scripts/shot.cjs page.html out.png WIDTH HEIGHT [chrome-binary] [scale]
// WIDTH and HEIGHT are CSS pixels; the PNG is that times scale (default 1).
// Chrome's own --screenshot under the new headless mode counts the browser
// frame in --window-size, which left the bottom ~87px of every asset blank.
let pw;
try { pw = require('playwright-core'); } catch { pw = require('playwright'); }
const [, , html, out, w, h, chrome, scale] = process.argv;
(async () => {
  const browser = await pw.chromium.launch({ executablePath: chrome || undefined, args: ['--no-sandbox'] });
  const page = await browser.newPage({ viewport: { width: +w, height: +h }, deviceScaleFactor: +(scale || 1) });
  await page.goto('file://' + html);
  await page.screenshot({ path: out });
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
