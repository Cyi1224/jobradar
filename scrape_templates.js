const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  console.log('Opening templates page...');
  await page.goto('https://jianli.xiaolinnote.com/templates', { waitUntil: 'networkidle', timeout: 30000 });

  // Wait for template cards to render
  await page.waitForTimeout(3000);

  // Extract template data from the DOM
  const templates = await page.evaluate(() => {
    const items = [];
    // Try various selectors to find template cards
    const cards = document.querySelectorAll('a[href*="/templates/"], div[class*="template"], div[class*="card"], [class*="TemplateCard"]');
    cards.forEach(card => {
      const name = card.querySelector('h2,h3,h4,[class*="name"],[class*="title"]')?.textContent?.trim();
      const link = card.href || card.querySelector('a')?.href;
      if (name && link) items.push({ name, link });
    });
    // If no structured cards, dump all links
    if (items.length === 0) {
      document.querySelectorAll('a').forEach(a => {
        if (a.href && a.href.includes('/templates/')) {
          items.push({ name: a.textContent.trim() || a.href, link: a.href });
        }
      });
    }
    return items;
  });

  console.log(`Found ${templates.length} templates:`);
  templates.forEach((t, i) => console.log(`  ${i+1}. ${t.name}\n     ${t.link}`));

  await browser.close();
})();
