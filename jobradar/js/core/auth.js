/**
 * core/auth.js — 前端用户会话
 * ─────────────────────────────────────────────
 * 令牌存 localStorage。mock 模式任意账号可登录（本地演示）；
 * http 模式对接后端 /api/auth/login|register，返回 JWT。
 * 其它数据请求在 http.js / catalog.js 里自动带上 Authorization。
 */
import { CONFIG } from '../config.js';

const TOKEN_KEY = 'jr_token';
const USER_KEY  = 'jr_user';

function save(token, username) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, username);
}

async function post(path, body) {
  const res = await fetch(CONFIG.API_BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.message || data.error || `请求失败 ${res.status}`);
  return data;
}

/* mock：任意账号可登录，发一个本地假令牌 */
const authMock = {
  async login(u, p) {
    if (!u || !p) throw new Error('请输入账号和密码');
    return { token: 'mock-' + u, username: u };
  },
  async register(u, p) {
    if (!u || u.length < 3) throw new Error('用户名至少 3 位');
    if (!p || p.length < 6) throw new Error('密码至少 6 位');
    return { token: 'mock-' + u, username: u };
  },
};

/* http：对接 Spring Boot */
const authHttp = {
  login(u, p)    { return post('/auth/login', { username: u, password: p }); },
  register(u, p) { return post('/auth/register', { username: u, password: p }); },
};

const adapter = CONFIG.USE_MOCK ? authMock : authHttp;

export const Auth = {
  getToken()  { return localStorage.getItem(TOKEN_KEY) || ''; },
  getUser()   { return localStorage.getItem(USER_KEY) || ''; },
  isLoggedIn(){ return !!localStorage.getItem(TOKEN_KEY); },
  logout()    { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY); location.reload(); },
  async login(u, p)    { const r = await adapter.login(u, p);    save(r.token, r.username); return r; },
  async register(u, p) { const r = await adapter.register(u, p); save(r.token, r.username); return r; },
};
