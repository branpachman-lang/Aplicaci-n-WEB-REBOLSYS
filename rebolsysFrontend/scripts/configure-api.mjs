import { writeFile } from 'node:fs/promises';

const apiUrl = process.env.REBOLSYS_API_URL?.trim().replace(/\/+$/, '') || '/api/v1';

if (apiUrl.startsWith('//')) {
  throw new Error('REBOLSYS_API_URL debe ser una ruta relativa o una URL absoluta válida.');
}

if (!apiUrl.startsWith('/')) {
  let parsedUrl;
  try {
    parsedUrl = new URL(apiUrl);
  } catch {
    throw new Error('REBOLSYS_API_URL debe ser una URL absoluta válida.');
  }

  if (process.env.VERCEL && parsedUrl.protocol !== 'https:') {
    throw new Error('REBOLSYS_API_URL debe usar HTTPS en Vercel.');
  }
}

const runtimeConfig = `window.__REBOLSYS_CONFIG__ = { apiUrl: ${JSON.stringify(apiUrl)} };\n`;

await writeFile(new URL('../public/runtime-config.js', import.meta.url), runtimeConfig);
