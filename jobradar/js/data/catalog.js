/**
 * data/catalog.js — 校招信息库 & AI 推荐 数据层
 * ─────────────────────────────────────────────
 * 与 store.js 同样的「mock / http 双适配器 + 门面」模式：
 *   JobStore   → 校招信息库（公司岗位目录）
 *   RecoStore  → AI 匹配推荐岗位
 * 接后端后把 CONFIG.USE_MOCK 改 false 即可切换到真实接口。
 */
import { CONFIG } from '../config.js';

/* ── 徽章样式映射（视图复用）── */
export const JOB_BADGE = {
  coType:  { '央国企': 'b-blue', '外企': 'b-purple', '民企': 'b-gray', '银行': 'b-teal', '事业单位': 'b-purple' },
  recruit: { '秋招': 'b-blue', '春招': 'b-green', '春招补录': 'b-green', '实习': 'b-teal', '秋招提前批': 'b-amber' },
};

/* ── 校招信息库数据：仅 mock 模式按需懒加载 jobs.seed.js（约 9MB）。
 *    http 模式（USE_MOCK=false）走后端分页接口，永不下载这份种子。 ── */
const FREE_MAX_PAGES = 5;   // 免费用户可浏览页数（与后端 JobService 一致）
let JOBS = null;
async function jobsData() {
  if (!JOBS) JOBS = (await import('./jobs.seed.js')).JOBS_SEED;
  return JOBS;
}

/* ── 种子数据：AI 推荐岗位 ── */
const RECOS = [
  { id: 1, co: '米哈游',   pos: '游戏后端开发工程师', city: '上海', target: '2027届', recruitType: '秋招提前批', deadline: '2026-07-10', tags: ['秋招提前批', 'Java', '分布式'],  match: 92 },
  { id: 2, co: '商汤科技', pos: 'CV 算法实习生',      city: '北京', target: '2027届', recruitType: '实习',       deadline: '招满为止',   tags: ['实习', 'Python', '深度学习'],   match: 88 },
  { id: 3, co: '美团',     pos: '后端开发工程师',     city: '北京', target: '2027届', recruitType: '秋招',       deadline: '2026-08-05', tags: ['秋招', 'Java', '高并发'],       match: 84 },
  { id: 4, co: '蔚来',     pos: '自动驾驶算法工程师', city: '上海', target: '2027届', recruitType: '秋招',       deadline: '2026-08-12', tags: ['秋招', 'C++', '自动驾驶'],      match: 81 },
  { id: 5, co: '大疆创新', pos: '嵌入式软件工程师',   city: '深圳', target: '2027届', recruitType: '秋招',       deadline: '2026-08-01', tags: ['秋招', 'C++', '嵌入式'],        match: 79 },
  { id: 6, co: '字节跳动', pos: '后端开发工程师',     city: '上海', target: '2027届', recruitType: '秋招',       deadline: '2026-07-15', tags: ['秋招', 'Go', '微服务'],         match: 76 },
];

function shuffle(arr) {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

/* ── 筛选辅助（mock 端与后端 JobService 逻辑保持一致）── */
function jDaysDiff(dl) {
  const m = /(\d{4})-(\d{1,2})-(\d{1,2})/.exec(dl || '');
  if (!m) return null;
  return Math.ceil((new Date(+m[1], +m[2] - 1, +m[3]) - new Date()) / 86400000);
}
function jWithin7(dl) { const d = jDaysDiff(dl); return d !== null && d >= 0 && d <= 7; }
function jobMatch(j, f) {
  const q = (f.q || '').trim();
  if (q && !`${j.co} ${j.positions} ${j.city} ${j.industry} ${j.note || ''}`.includes(q)) return false;
  if (f.recruitType && j.recruitType !== f.recruitType) return false;
  if (f.industry && j.industry !== f.industry) return false;
  if (f.city && !(j.city || '').includes(f.city)) return false;
  if (f.apply && !j.applyUrl) return false;
  if (f.urgent && !jWithin7(j.deadline)) return false;
  if (f.soe && j.coType !== '央国企') return false;
  if (f.foreign && j.coType !== '外企') return false;
  if (f.inst && !(/研究/.test(j.industry || '') || /研究院|研究所/.test(j.co || '') || j.coType === '事业单位')) return false;
  return true;
}

/* ── Mock 适配器 ── */
const jobsMock = {
  async getAll() { return (await jobsData()).map((j) => ({ ...j })); },
  async search(f = {}) {
    const JOBS = await jobsData();
    const list = JOBS.filter((j) => jobMatch(j, f))
      .sort((a, b) => String(b.updatedAt || '').localeCompare(String(a.updatedAt || '')) || a.id - b.id);
    const size = f.size > 0 ? Math.min(f.size, 100) : 30;
    const page = Math.max(0, f.page || 0);
    const realPages = Math.ceil(list.length / size);
    // 会员（本地到期时间未过）不限页；否则与后端一致：免费仅前 5 页
    const memberUntil = localStorage.getItem('jr_member_until');
    const unlimited = !!(memberUntil && new Date(memberUntil) > new Date());
    const capped = !unlimited && realPages > FREE_MAX_PAGES;
    const totalPages = unlimited ? realPages : Math.min(realPages, FREE_MAX_PAGES);
    const content = (!unlimited && page >= FREE_MAX_PAGES) ? [] : list.slice(page * size, page * size + size).map((j) => ({ ...j }));
    return { content, total: list.length, page, size, totalPages, capped };
  },
  async stats() {
    const JOBS = await jobsData();
    const today = JOBS.reduce((m, j) => (j.updatedAt > m ? j.updatedAt : m), '');
    return {
      companies: new Set(JOBS.map((j) => j.co)).size,
      total: JOBS.length,
      open: JOBS.filter((j) => j.applyUrl).length,
      today: JOBS.filter((j) => j.updatedAt === today).length,
      todayDate: today,
      recruitTypes: [...new Set(JOBS.map((j) => j.recruitType).filter(Boolean))].sort(),
      industries: [...new Set(JOBS.map((j) => j.industry).filter(Boolean))].sort(),
    };
  },
};
const recosMock = {
  async getAll()  { return [...RECOS].sort((a, b) => b.match - a.match); },
  async refresh() { return shuffle(RECOS); },
};

/* ── HTTP 适配器（对接 Spring Boot）── */
async function get(path) {
  const token = localStorage.getItem('jr_token') || '';
  const res = await fetch(CONFIG.API_BASE + path, {
    headers: token ? { Authorization: 'Bearer ' + token } : {},
  });
  if (res.status === 401) {
    const had = !!localStorage.getItem('jr_token');
    localStorage.removeItem('jr_token');
    localStorage.removeItem('jr_user');
    if (had) location.reload();              // 会话过期才刷新；匿名访问静默失败
    throw new Error('未登录');
  }
  if (!res.ok) throw new Error(`请求失败 ${res.status}: ${path}`);
  return res.json();
}
const jobsHttp = {
  getAll() { return get('/jobs'); },                          // 旧接口（保留兼容，现以 search 为主）
  search(f = {}) {                                            // GET /api/jobs?page&size&q&...
    const qs = new URLSearchParams();
    Object.entries(f).forEach(([k, v]) => {
      if (v === '' || v == null || v === false) return;       // 省略空值与未启用的开关
      qs.set(k, v);
    });
    return get('/jobs?' + qs.toString());
  },
  stats() { return get('/jobs/stats'); },                     // GET /api/jobs/stats
};
const recosHttp = {
  getAll()  { return get('/recommendations'); },              // GET /api/recommendations
  refresh() { return get('/recommendations?refresh=1'); },
};

/* ── 门面：按开关选择 ── */
export const JobStore  = CONFIG.USE_MOCK ? jobsMock  : jobsHttp;
export const RecoStore = CONFIG.USE_MOCK ? recosMock : recosHttp;
