/**
 * views/aimatch.js — 「AI 匹配」页面视图
 * 基于用户「专业 + 意向岗位」在岗位库中关键词匹配打分，未过期优先。
 */
import { JobStore } from '../data/catalog.js';
import { ProfileStore } from '../data/profile.js';
import { showToast } from '../core/toast.js';

const MAX_RESULTS = 30;

/* ── 公司 logo 渐变色板（按首字母哈希选色）── */
const LOGO_COLORS = [
  ['#6366F1','#8B5CF6'], ['#0EA5E9','#06B6D4'], ['#10B981','#34D399'],
  ['#F59E0B','#F97316'], ['#EF4444','#F43F5E'], ['#8B5CF6','#EC4899'],
  ['#14B8A6','#06B6D4'], ['#F97316','#EAB308'], ['#3B82F6','#6366F1'],
];
function logoGrad(name) {
  const idx = (name || '?').charCodeAt(0) % LOGO_COLORS.length;
  const [a, b] = LOGO_COLORS[idx];
  return `linear-gradient(135deg,${a},${b})`;
}

/* ── 关键词拆分：支持中英文逗号、顿号、斜杠、空格 ── */
function splitKeywords(str) {
  return (str || '').split(/[,，、/\s]+/).map(s => s.trim()).filter(Boolean);
}

/* ── 判断岗位是否未过期 ── */
function isActive(deadline) {
  const m = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(deadline || '');
  if (!m) return true;
  return new Date(+m[1], +m[2] - 1, +m[3]) >= new Date(new Date().toDateString());
}

/* ── 截止日期显示 ── */
function deadlineInfo(dl) {
  if (!dl) return { text: '', cls: 'open' };
  const m = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(dl);
  if (!m) return { text: dl, cls: 'open' };
  const days = Math.ceil((new Date(+m[1], +m[2]-1, +m[3]) - new Date()) / 86400000);
  if (days < 0)  return { text: '已截止', cls: 'expired' };
  if (days === 0) return { text: '今日截止', cls: 'urgent' };
  if (days <= 7)  return { text: `还剩 ${days} 天`, cls: 'urgent' };
  return { text: `截止 ${+m[2]}/${String(+m[3]).padStart(2,'0')}`, cls: 'safe' };
}

/* ── 招聘类型徽章颜色 ── */
const TYPE_COLOR = {
  '秋招提前批': '#7C3AED', '秋招': '#1A56DB', '春招': '#059669',
  '春招补录': '#059669', '实习': '#D97706', '暑假实习': '#D97706',
};
function typeBadge(rt) {
  const color = TYPE_COLOR[rt] || '#6B7280';
  return `<span class="match-type-badge" style="background:${color}18;color:${color}">${rt || ''}</span>`;
}

/* ── SVG 匹配度环 ── */
function scoreRing(pct) {
  const R = 20, C = 2 * Math.PI * R;
  const fill = (pct / 100) * C;
  const color = pct >= 70 ? '#10B981' : pct >= 40 ? '#F59E0B' : '#9CA3AF';
  const label = pct >= 80 ? '高匹配' : pct >= 60 ? '较匹配' : pct >= 40 ? '一般' : '低匹配';
  return `
    <div class="match-ring">
      <svg width="52" height="52" viewBox="0 0 52 52">
        <circle class="match-ring-track" cx="26" cy="26" r="${R}"/>
        <circle class="match-ring-fill" cx="26" cy="26" r="${R}"
          stroke="${color}" stroke-dasharray="${C}" stroke-dashoffset="${C - fill}"/>
      </svg>
      <div class="match-ring-text">
        <span class="match-ring-num" style="color:${color}">${pct}</span>
        <span class="match-ring-pct">%</span>
      </div>
    </div>
    <div class="match-rank-label" style="color:${color}">${label}</div>`;
}

/* ── 匹配打分 ── */
function scoreJob(job, keywords) {
  if (!keywords.length) return 0;
  const searchText = [
    job.positions || '',
    job.industry  || '',
    job.co        || '',
    job.note      || '',
  ].join(' ').toLowerCase();
  let hits = 0;
  for (const kw of keywords) {
    if (searchText.includes(kw.toLowerCase())) hits++;
  }
  return Math.round((hits / keywords.length) * 100);
}

export function initAimatch() {
  const list       = document.getElementById('aimatch-list');
  const countEl    = document.getElementById('aimatch-count');
  const refreshBtn = document.getElementById('aimatch-refresh');
  const summary    = document.getElementById('aimatch-profile-summary');

  let lastProfile = null;

  /* ── 渲染偏好摘要 ── */
  function renderSummary(u) {
    if (!summary) return;
    const jobs   = splitKeywords(u.intentJob);
    const cities = splitKeywords(u.intentCity);
    if (!jobs.length && !cities.length && !u.major) {
      summary.innerHTML = `<span style="color:var(--c-text-3);font-size:13px">尚未设置求职偏好</span><a class="btn sm primary" data-goto="profile" style="margin-left:auto"><i class="ti ti-user"></i>去填写</a>`;
      return;
    }
    const chips = [
      ...jobs.map(t   => `<span class="badge b-blue"><i class="ti ti-briefcase" style="font-size:10px"></i> ${t}</span>`),
      ...cities.map(t => `<span class="badge b-gray"><i class="ti ti-map-pin" style="font-size:10px"></i> ${t}</span>`),
      u.major ? `<span class="badge b-green"><i class="ti ti-school" style="font-size:10px"></i> ${u.major}</span>` : '',
    ].filter(Boolean).join('');
    summary.innerHTML = chips + `<a class="btn sm" data-goto="profile" style="margin-left:auto;flex-shrink:0"><i class="ti ti-edit"></i>编辑偏好</a>`;
  }

  /* ── 渲染空状态（未填信息）── */
  function renderEmpty(msg, hint) {
    if (countEl) countEl.textContent = '0';
    list.innerHTML = `
      <div class="jdb-empty" style="padding:48px 0">
        <i class="ti ti-user-search" style="font-size:40px;color:var(--c-text-3);margin-bottom:12px"></i>
        <div style="font-size:15px;font-weight:500;margin-bottom:6px">${msg}</div>
        <div style="font-size:13px;color:var(--c-text-3);margin-bottom:16px">${hint}</div>
        <button class="btn primary" data-goto="profile"><i class="ti ti-user"></i>去填写个人信息</button>
      </div>`;

  }

  /* ── 渲染匹配卡片 ── */
  function renderCards(results) {
    if (countEl) countEl.textContent = results.length;
    if (!results.length) {
      list.innerHTML = `<div class="jdb-empty"><i class="ti ti-search-off"></i>暂无匹配岗位，请尝试调整意向岗位关键词</div>`;
      return;
    }

    // 列表头部
    const header = `
      <div class="match-list-header">
        <div class="match-list-title">
          <i class="ti ti-sparkles" style="color:var(--brand)"></i>
          为你推荐的岗位
        </div>
        <span class="match-list-badge">共 ${results.length} 个</span>
      </div>`;

    const cards = results.map((r, idx) => {
      const active = isActive(r.deadline);
      const pct    = r._score;
      const dl     = deadlineInfo(r.deadline);
      const pos    = (r.positions || '').replace(/\s+/g, ' ').trim();
      const city   = r.city || '';
      const target = r.target || '';
      return `
        <div class="match-card${active ? '' : ' expired'}">
          <div class="match-main">
            <div class="match-logo" style="background:${logoGrad(r.co)}">${(r.co || '?')[0]}</div>
            <div class="match-info">
              <div class="match-co-row">
                <span class="match-co">${r.co || ''}</span>
                ${r.recruitType ? typeBadge(r.recruitType) : ''}
                ${!active ? '<span class="match-type-badge" style="background:#F3F4F6;color:#9CA3AF">已过期</span>' : ''}
                ${idx === 0 ? '<span class="badge b-amber" style="font-size:10px">最佳匹配</span>' : ''}
              </div>
              <div class="match-pos" title="${pos}">${pos}</div>
              <div class="match-meta-row">
                ${city ? `<span class="match-meta-item"><i class="ti ti-map-pin"></i>${city}</span>` : ''}
                ${target ? `<span class="match-meta-item"><i class="ti ti-users"></i>${target}</span>` : ''}
                ${r.industry ? `<span class="match-meta-item"><i class="ti ti-building"></i>${r.industry.split('/')[0]}</span>` : ''}
                ${dl.text ? `<span class="match-deadline-badge ${dl.cls}">${dl.text}</span>` : ''}
                ${r.applyUrl ? `<a class="match-apply-link" href="${r.applyUrl}" target="_blank" rel="noopener"><i class="ti ti-send"></i>立即投递</a>` : ''}
              </div>
            </div>
            <div class="match-right">
              ${scoreRing(pct)}
            </div>
          </div>
        </div>`;
    }).join('');

    list.innerHTML = header + cards;
  }

  /* ── 核心：拉取并匹配 ── */
  async function doMatch(profile) {
    const keywords = [
      ...splitKeywords(profile.major),
      ...splitKeywords(profile.intentJob),
    ];

    if (!profile.major && !profile.intentJob) {
      renderEmpty('请先填写专业和意向岗位', 'AI 匹配需要根据你的专业和求职意向来推荐合适岗位');
      return;
    }
    if (!keywords.length) {
      renderEmpty('关键词为空', '请在个人信息中填写专业和意向岗位');
      return;
    }

    list.innerHTML = `<div class="jdb-empty"><i class="ti ti-loader-2 jdb-spin"></i> 匹配中…</div>`;

    // 用关键词搜索，多关键词 OR 拼成逗号分隔（后端已支持）
    const q = keywords.slice(0, 5).join(',');
    try {
      const res = await JobStore.search({ q, size: 100, page: 0 });
      const jobs = res.content || [];

      // 打分 + 排序：未过期优先，同组内按匹配度降序
      const scored = jobs
        .map(j => ({ ...j, _score: scoreJob(j, keywords) }))
        .filter(j => j._score > 0)
        .sort((a, b) => {
          const da = isActive(a.deadline) ? 0 : 1;
          const db = isActive(b.deadline) ? 0 : 1;
          if (da !== db) return da - db;
          return b._score - a._score;
        })
        .slice(0, MAX_RESULTS);

      renderCards(scored);
    } catch (e) {
      list.innerHTML = `<div class="jdb-empty"><i class="ti ti-alert-circle"></i> 匹配失败：${e.message}</div>`;
    }
  }

  /* ── 页面初始化 ── */
  async function init() {
    try {
      lastProfile = await ProfileStore.get();
    } catch {
      lastProfile = {};
    }
    renderSummary(lastProfile);
    await doMatch(lastProfile);
  }

  refreshBtn?.addEventListener('click', async () => {
    try { lastProfile = await ProfileStore.get(); } catch { lastProfile = lastProfile || {}; }
    renderSummary(lastProfile);
    await doMatch(lastProfile);
    showToast('已刷新匹配结果');
  });

  init();
}
