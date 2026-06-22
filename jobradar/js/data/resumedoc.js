/**
 * data/resumedoc.js — 简历编辑器文档存取
 * ─────────────────────────────────────────────
 * mock 模式：存浏览器 localStorage（单机）。
 * http 模式：按登录用户存后端 MySQL（GET/PUT /api/resume-doc），多设备打开即同步最新。
 */
import { CONFIG } from '../config.js';

const KEY = 'jr_resume_doc';

const docMock = {
  async load() { try { const r = localStorage.getItem(KEY); return r ? JSON.parse(r) : null; } catch { return null; } },
  async save(doc) { localStorage.setItem(KEY, JSON.stringify(doc)); },
};

async function req(method, body) {
  const token = localStorage.getItem('jr_token') || '';
  const res = await fetch(CONFIG.API_BASE + '/resume-doc', {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });
  if (res.status === 401) {
    const had = !!localStorage.getItem('jr_token');
    localStorage.removeItem('jr_token'); localStorage.removeItem('jr_user');
    if (had) location.reload();
    throw new Error('未登录');
  }
  if (res.status === 204) return null;
  if (!res.ok) throw new Error('请求失败 ' + res.status);
  return res.json();
}

const docHttp = {
  load() { return req('GET'); },
  save(doc) { return req('PUT', doc); },
};

export const ResumeDocStore = CONFIG.USE_MOCK ? docMock : docHttp;
