/**
 * Sitemap generator — run manually or via CI after deploy.
 * Usage: npx tsx fe/scripts/generate-sitemap.ts
 */
import { writeFileSync } from 'fs';

const BASE_URL = process.env.SITE_URL || 'https://vnshop.vn';

async function generateSitemap() {
  const staticRoutes = [
    { loc: '/', priority: '1.0', changefreq: 'daily' },
    { loc: '/categories', priority: '0.8', changefreq: 'weekly' },
    { loc: '/search', priority: '0.7', changefreq: 'daily' },
  ];

  let productRoutes: Array<{ loc: string; priority: string; changefreq: string }> = [];

  try {
    const res = await fetch('http://localhost:8080/products?size=1000&sort=createdAt,desc');
    const data = await res.json();
    const products = data.data?.content ?? [];
    productRoutes = products.map((p: any) => ({
      loc: `/products/${p.id}`,
      priority: '0.7',
      changefreq: 'weekly',
    }));
  } catch (e) {
    console.warn('Could not fetch products — generating static-only sitemap');
  }

  const allRoutes = [...staticRoutes, ...productRoutes];

  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${allRoutes.map(r => `  <url>
    <loc>${BASE_URL}${r.loc}</loc>
    <changefreq>${r.changefreq}</changefreq>
    <priority>${r.priority}</priority>
  </url>`).join('\n')}
</urlset>`;

  writeFileSync('fe/public/sitemap.xml', xml);
  console.log(`Generated sitemap with ${allRoutes.length} URLs`);
}

generateSitemap();
