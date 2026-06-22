/**
 * server.js — JobRadar 本地一体化服务（前端静态托管 + DeepSeek 代理）
 * ─────────────────────────────────────────────
 * 一个进程同时承担「前端」与「后端」：
 *   · 静态托管 ./jobradar 下的前端页面（http://localhost:8123）
 *   · POST /api/llm/v1/chat/completions → 转发到 DeepSeek，服务端注入密钥
 * 这样密钥不落前端、且同源无 CORS 问题。
 *
 * 启动（密钥从环境变量读取，不写进源码）：
 *   DEEPSEEK_API_KEY=sk-xxx  PORT=8123  node server.js
 */
const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');

const PORT = Number(process.env.PORT) || 8123;
const STATIC_DIR = path.join(__dirname, 'jobradar');
const DEEPSEEK_KEY = process.env.DEEPSEEK_API_KEY || '';
const DEEPSEEK_HOST = process.env.DEEPSEEK_HOST || 'api.deepseek.com';
const LLM_PREFIX = '/api/llm'; // 前端 BASE_URL = '/api/llm/v1'

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js':   'text/javascript; charset=utf-8',
  '.mjs':  'text/javascript; charset=utf-8',
  '.css':  'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg':  'image/svg+xml',
  '.ico':  'image/x-icon',
  '.png':  'image/png',
  '.jpg':  'image/jpeg',
  '.webp': 'image/webp',
  '.woff2':'font/woff2',
  '.woff': 'font/woff',
};

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  if (req.method === 'OPTIONS') { res.writeHead(204); return res.end(); }

  if (req.url.startsWith(LLM_PREFIX + '/')) return proxyLlm(req, res);
  return serveStatic(req, res);
});

/* ── DeepSeek 代理 ── */
function proxyLlm(req, res) {
  if (req.method !== 'POST') { res.writeHead(405); return res.end('Method Not Allowed'); }
  if (!DEEPSEEK_KEY) {
    res.writeHead(500, { 'Content-Type': 'application/json; charset=utf-8' });
    return res.end(JSON.stringify({ error: '后端未配置 DEEPSEEK_API_KEY' }));
  }

  const chunks = [];
  req.on('data', (c) => chunks.push(c));
  req.on('end', () => {
    const body = Buffer.concat(chunks);
    const upstreamPath = req.url.slice(LLM_PREFIX.length); // /v1/chat/completions
    const opts = {
      host: DEEPSEEK_HOST,
      path: upstreamPath,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${DEEPSEEK_KEY}`,
        'Content-Length': Buffer.byteLength(body),
      },
    };
    const up = https.request(opts, (upRes) => {
      res.writeHead(upRes.statusCode || 502, { 'Content-Type': 'application/json; charset=utf-8' });
      upRes.pipe(res);
    });
    up.on('error', (e) => {
      res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ error: 'DeepSeek 上游请求失败：' + e.message }));
    });
    up.write(body);
    up.end();
  });
}

/* ── 静态资源 ── */
function serveStatic(req, res) {
  let urlPath = decodeURIComponent(req.url.split('?')[0]);
  if (urlPath === '/') urlPath = '/index.html';
  const filePath = path.join(STATIC_DIR, path.normalize(urlPath));
  if (!filePath.startsWith(STATIC_DIR)) { res.writeHead(403); return res.end('Forbidden'); }

  fs.readFile(filePath, (err, data) => {
    if (err) { res.writeHead(404); return res.end('Not Found'); }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(filePath).toLowerCase()] || 'application/octet-stream' });
    res.end(data);
  });
}

server.listen(PORT, () => {
  console.log(`JobRadar 已启动 → http://localhost:${PORT}`);
  console.log(`DeepSeek 代理 → ${LLM_PREFIX}/v1/chat/completions（密钥${DEEPSEEK_KEY ? '已' : '未'}配置）`);
});
