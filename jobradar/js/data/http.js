/**
 * data/http.js — 真实后端适配器（对接 Spring Boot REST API）
 * ─────────────────────────────────────────────
 * 与 mock.js 接口完全一致。约定的 REST 契约：
 *
 *   GET    /api/applications              → 列表
 *   GET    /api/applications/{id}         → 单条
 *   POST   /api/applications              → 新增
 *   PATCH  /api/applications/{id}/status  → 更新状态 { status, fields }
 *   DELETE /api/applications/{id}         → 删除
 *   GET    /api/applications/stats        → 各状态计数
 *
 * 后端返回的 JSON 字段需与前端一致：
 *   { id, co, pos, type, city, deadline, status, note, logs:[{s,time,note}] }
 */
import { CONFIG } from '../config.js';

async function request(path, options = {}) {
  const token = localStorage.getItem('jr_token') || '';
  const res = await fetch(CONFIG.API_BASE + path, {
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    ...options,
  });
  if (res.status === 401) {                    // 受保护接口未授权
    const had = !!localStorage.getItem('jr_token');
    localStorage.removeItem('jr_token');
    localStorage.removeItem('jr_user');
    if (had) location.reload();                // 之前登录过 → 会话过期，刷新回匿名态；匿名访问则静默失败
    throw new Error('未登录');
  }
  if (!res.ok) {
    throw new Error(`请求失败 ${res.status}: ${path}`);
  }
  if (res.status === 204) return null;
  return res.json();
}

export const httpRepo = {
  getAll() {
    return request('/applications');
  },

  getById(id) {
    return request(`/applications/${id}`);
  },

  add(entry) {
    return request('/applications', {
      method: 'POST',
      body: JSON.stringify(entry),
    });
  },

  updateStatus(id, status, fields = {}) {
    return request(`/applications/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status, fields }),
    });
  },

  remove(id) {
    return request(`/applications/${id}`, { method: 'DELETE' }).then(() => true);
  },

  getCounts() {
    return request('/applications/stats');
  },
};
