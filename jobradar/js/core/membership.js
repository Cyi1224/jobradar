/**
 * core/membership.js — 付费会员
 * ─────────────────────────────────────────────
 * 套餐：月 ¥15 / 季 ¥40 / 半年 ¥70 / 年 ¥100。
 * 会员可解除「校招库前 5 页」限制（无限翻页）。
 * mock 模式把到期时间存 localStorage（本地演示）；http 模式走后端 /api/membership。
 * ⚠️ subscribe 为「即时开通」演示；正式上线应接入微信/支付宝，支付成功回调后再开通。
 */
import { CONFIG } from '../config.js';

export const PLANS = [
  { key: 'month',   name: '月度会员', price: 15,  days: 30,  perMonth: '15' },
  { key: 'quarter', name: '季度会员', price: 40,  days: 90,  perMonth: '13.3' },
  { key: 'half',    name: '半年会员', price: 70,  days: 180, perMonth: '11.7' },
  { key: 'year',    name: '年度会员', price: 100, days: 365, perMonth: '8.3', best: true },
];

const MEMBER_KEY = 'jr_member_until';

/* mock：到期时间存本地 */
const memMock = {
  async status() { return statusFromLocal(); },
  async subscribe(plan) {
    const p = PLANS.find((x) => x.key === plan);
    if (!p) throw new Error('未知套餐');
    const cur = localStorage.getItem(MEMBER_KEY);
    const base = (cur && new Date(cur) > new Date()) ? new Date(cur) : new Date();
    base.setDate(base.getDate() + p.days);
    localStorage.setItem(MEMBER_KEY, base.toISOString());
    return statusFromLocal();
  },
};

/* http：对接后端 */
async function req(path, options = {}) {
  const token = localStorage.getItem('jr_token') || '';
  const res = await fetch(CONFIG.API_BASE + path, {
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    ...options,
  });
  if (res.status === 401) {
    const had = !!localStorage.getItem('jr_token');
    localStorage.removeItem('jr_token'); localStorage.removeItem('jr_user');
    if (had) location.reload();
    throw new Error('未登录');
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.message || data.error || `请求失败 ${res.status}`);
  return data;
}
const memHttp = {
  status() { return req('/membership'); },
  subscribe(plan) { return req('/membership/subscribe', { method: 'POST', body: JSON.stringify({ plan }) }); },
};

function statusFromLocal() {
  const until = localStorage.getItem(MEMBER_KEY);
  const member = !!(until && new Date(until) > new Date());
  const daysLeft = member ? Math.ceil((new Date(until) - new Date()) / 86400000) : 0;
  return { member, memberUntil: member ? until : null, daysLeft, plan: member ? '会员' : '免费版' };
}

/** 同步判断本地是否会员（mock 模式给 catalog 解除分页上限用）。 */
export function isMemberLocal() {
  const until = localStorage.getItem(MEMBER_KEY);
  return !!(until && new Date(until) > new Date());
}

export const Membership = CONFIG.USE_MOCK ? memMock : memHttp;
