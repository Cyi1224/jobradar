/**
 * core/auth.js — 前端用户会话
 * ─────────────────────────────────────────────
 * 令牌存 localStorage。mock 模式任意账号可登录（本地演示）；
 * http 模式对接后端 /api/auth/login|register，返回 JWT。
 * 其它数据请求在 http.js / catalog.js 里自动带上 Authorization。
 */
import { CONFIG } from '../config.js';

const TOKEN_KEY = 'jr_token';
const USER_KEY  = 'jr_user';       // 存储 displayName 用于 UI 展示
const ACCT_KEY  = 'jr_account';    // 存储 account（登录标识）

function save(token, account, displayName) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(ACCT_KEY, account);
  localStorage.setItem(USER_KEY, displayName || account);
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
    return { token: 'mock-' + u, account: u, displayName: u };
  },
  async register(a, dn, p) {
    if (!a || a.length < 3) throw new Error('账号至少 3 位');
    if (!dn || dn.length > 15) throw new Error('用户名最长 15 位');
    if (!p || p.length < 6) throw new Error('密码至少 6 位');
    return { token: 'mock-' + a, account: a, displayName: dn };
  },
};

/* http：对接 Spring Boot */
const authHttp = {
  login(a, p)         { return post('/auth/login',    { account: a, password: p }); },
  register(a, dn, p)  { return post('/auth/register', { account: a, displayName: dn, password: p }); },
};

const adapter = CONFIG.USE_MOCK ? authMock : authHttp;

export const Auth = {
  getToken()   { return localStorage.getItem(TOKEN_KEY) || ''; },
  getUser()    { return localStorage.getItem(USER_KEY) || ''; },
  getAccount() { return localStorage.getItem(ACCT_KEY) || ''; },
  isLoggedIn() { return !!localStorage.getItem(TOKEN_KEY); },
  logout()     { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(ACCT_KEY); localStorage.removeItem(USER_KEY); location.reload(); },
  async login(a, p)          { const r = await adapter.login(a, p);          save(r.token, r.account, r.displayName); return r; },
  async register(a, dn, p)   { const r = await adapter.register(a, dn, p);  save(r.token, r.account, r.displayName); return r; },
};
