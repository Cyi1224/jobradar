/**
 * views/dashboard.js — 「总览」页面视图
 * 数据来源：投递 Store（实时）+ 校招库 JobStore（今日统计）
 * 联动：点击卡片 / 列表跳转对应功能页面
 */
import { Store } from '../data/store.js';
import { JobStore } from '../data/catalog.js';
import { ProfileStore } from '../data/profile.js';
import { formatDeadline, isUrgent } from '../core/format.js';
import { on, EVT } from '../core/bus.js';
import { switchPage } from '../core/router.js';

/* ── 工具 ── */
const $ = (id) => document.getElementById(id);
const STATUS_ORDER = ['未投递','已投递','已笔试','已面试','已OC','已挂'];
const STATUS_COLOR = {
  '未投递':'#9CA3AF','已投递':'#3B82F6','已笔试':'#8B5CF6',
  '已面试':'#10B981','已OC':'#059669','已挂':'#EF4444',
};
const STATUS_ICON = {
  '未投递':'ti-clock','已投递':'ti-send','已笔试':'ti-writing',
  '已面试':'ti-users','已OC':'ti-trophy','已挂':'ti-x',
};

function dayDiff(deadline) {
  if (!deadline || deadline === '招满为止') return null;
  return Math.ceil((new Date(deadline) - new Date()) / 86400000);
}
function reached(app, stage) {
  return app.status === stage || (app.logs || []).some((l) => l.s === stage);
}

/* ── 今日校招动态 pill ── */
async function renderTodayPill() {
  const pill = $('dash-today-pill');
  if (!pill) return;
  try {
    const s = await JobStore.stats();
    pill.textContent = s.today > 0
      ? `今日新增 ${s.today} 个岗位`
      : `校招库共 ${s.total} 个岗位`;
  } catch { pill.style.display = 'none'; }
}

/* ── 统计卡 ── */
async function renderStats(apps) {
  const el = $('dash-stats');
  if (!el) return;

  const total     = apps.length;
  const submitted = apps.filter((a) => a.status !== '未投递').length;
  const interview = apps.filter((a) => reached(a, '已面试')).length;
  const oc        = apps.filter((a) => a.status === '已OC').length;
  const urgent    = apps.filter((a) => isUrgent(a.deadline)).length;
  const rate      = submitted ? Math.round((interview / submitted) * 100) : 0;

  const cards = [
    { icon: 'ti-briefcase',  label: '追踪岗位', val: total,     note: '全部岗位',      color: '#6366F1', goto: 'applications' },
    { icon: 'ti-send',       label: '已投递',   val: submitted, note: `占比 ${total ? Math.round(submitted/total*100) : 0}%`, color: '#3B82F6', goto: 'applications' },
    { icon: 'ti-users',      label: '已获面试', val: interview, note: `转化率 ${rate}%`, color: '#10B981', goto: 'applications' },
    { icon: 'ti-alarm',      label: '即将截止', val: urgent,    note: '3天内',          color: '#F59E0B', goto: 'applications' },
    { icon: 'ti-trophy',     label: '已拿 OC',  val: oc,        note: oc ? '恭喜！' : '继续加油',     color: '#059669', goto: 'applications' },
  ];

  el.innerHTML = cards.map((c) => `
    <div class="dash-stat-card" data-goto="${c.goto}" style="--card-color:${c.color}">
      <div class="dash-stat-icon"><i class="ti ${c.icon}"></i></div>
      <div class="dash-stat-body">
        <div class="dash-stat-val">${c.val}</div>
        <div class="dash-stat-label">${c.label}</div>
        <div class="dash-stat-note">${c.note}</div>
      </div>
    </div>`).join('');
}

/* ── 投递漏斗 ── */
async function renderFunnel(apps) {
  const el = $('dash-funnel');
  if (!el) return;

  const STAGES = [
    { key: '已投递', label: '投递' },
    { key: '已笔试', label: '笔试' },
    { key: '已面试', label: '面试' },
    { key: '已OC',  label: '获OC' },
  ];
  const counts = STAGES.map((s, i) =>
    i === 0
      ? apps.filter((a) => a.status !== '未投递').length
      : apps.filter((a) => reached(a, s.key)).length
  );
  const max = Math.max(1, counts[0]);

  el.innerHTML = STAGES.map((s, i) => {
    const n = counts[i];
    const prev = i > 0 ? counts[i - 1] : n;
    const conv = prev ? Math.round((n / prev) * 100) : 0;
    const w = Math.round((n / max) * 100);
    const colors = ['#3B82F6','#8B5CF6','#10B981','#059669'];
    return `
      <div class="funnel-row">
        <span class="funnel-label">${s.label}</span>
        <div class="funnel-bar-wrap">
          <div class="funnel-bar" style="width:${w}%;background:${colors[i]}">&nbsp;</div>
        </div>
        <span class="funnel-num">${n}</span>
        ${i > 0 ? `<span class="funnel-conv">${conv}%</span>` : '<span class="funnel-conv"></span>'}
      </div>`;
  }).join('');

  // 挂科率
  const hung = apps.filter((a) => a.status === '已挂').length;
  if (hung) {
    el.innerHTML += `<div class="funnel-hung"><i class="ti ti-x" style="color:#EF4444"></i> 已挂 ${hung} 个</div>`;
  }
}

/* ── 即将截止 ── */
async function renderDeadlines(apps) {
  const el = $('dash-deadlines');
  if (!el) return;

  const upcoming = apps
    .filter((a) => a.deadline && a.deadline !== '招满为止' && a.status !== '已OC' && a.status !== '已挂')
    .map((a) => ({ ...a, diff: dayDiff(a.deadline) }))
    .filter((a) => a.diff !== null && a.diff >= 0)
    .sort((x, y) => x.diff - y.diff)
    .slice(0, 5);

  if (!upcoming.length) {
    el.innerHTML = `<div class="dash-empty-tip"><i class="ti ti-check-circle" style="color:var(--green)"></i> 近期无截止压力，保持节奏！</div>`;
    return;
  }

  el.innerHTML = upcoming.map((a) => {
    const urgency = a.diff === 0 ? 'red' : a.diff <= 2 ? 'orange' : a.diff <= 7 ? 'amber' : 'blue';
    const urgencyText = a.diff === 0 ? '今天截止' : a.diff === 1 ? '明天截止' : `还剩 ${a.diff} 天`;
    return `
      <div class="deadline-item" data-goto="applications" style="cursor:pointer">
        <div class="deadline-dot ${urgency}"></div>
        <div style="flex:1;min-width:0">
          <div class="deadline-co">${a.co} <span class="badge b-gray" style="font-size:10px">${a.type || ''}</span></div>
          <div class="deadline-pos">${a.pos}</div>
        </div>
        <div class="deadline-date ${urgency}">${urgencyText}</div>
      </div>`;
  }).join('');
}

/* ── 状态分布 ── */
async function renderStatusDist(apps) {
  const el = $('dash-status-dist');
  if (!el) return;

  if (!apps.length) {
    el.innerHTML = `<div class="dash-empty-tip"><i class="ti ti-plus-circle"></i> 添加岗位后查看分布</div>`;
    return;
  }

  const dist = {};
  apps.forEach((a) => { dist[a.status] = (dist[a.status] || 0) + 1; });
  const total = apps.length;

  el.innerHTML = STATUS_ORDER.filter((s) => dist[s]).map((s) => {
    const n = dist[s];
    const pct = Math.round((n / total) * 100);
    const color = STATUS_COLOR[s] || '#9CA3AF';
    const icon  = STATUS_ICON[s]  || 'ti-circle';
    return `
      <div class="dist-row" data-goto="applications">
        <div class="dist-icon" style="background:${color}18;color:${color}"><i class="ti ${icon}"></i></div>
        <span class="dist-label">${s}</span>
        <div class="dist-bar-wrap">
          <div class="dist-bar" style="width:${pct}%;background:${color}"></div>
        </div>
        <span class="dist-num" style="color:${color}">${n}</span>
      </div>`;
  }).join('');
}

/* ── 最近动态 ── */
async function renderActivity(apps) {
  const el = $('dash-activity');
  if (!el) return;

  // 收集所有状态变更日志，附上公司信息
  const events = [];
  apps.forEach((a) => {
    (a.logs || []).forEach((l) => {
      if (l.time) events.push({ co: a.co, pos: a.pos, status: l.s, time: l.time });
    });
  });
  events.sort((a, b) => new Date(b.time) - new Date(a.time));
  const recent = events.slice(0, 6);

  if (!recent.length) {
    el.innerHTML = `<div class="dash-empty-tip"><i class="ti ti-history"></i> 暂无操作记录</div>`;
    return;
  }

  el.innerHTML = recent.map((e) => {
    const color = STATUS_COLOR[e.status] || '#9CA3AF';
    const icon  = STATUS_ICON[e.status]  || 'ti-circle';
    const d = new Date(e.time);
    const timeStr = isNaN(d) ? '' : `${d.getMonth()+1}/${d.getDate()}`;
    return `
      <div class="activity-item">
        <div class="activity-dot" style="background:${color}"><i class="ti ${icon}" style="font-size:9px;color:#fff"></i></div>
        <div class="activity-body">
          <span class="activity-co">${e.co}</span>
          <span class="activity-arrow">→</span>
          <span class="activity-status" style="color:${color}">${e.status}</span>
        </div>
        <div class="activity-time">${timeStr}</div>
      </div>`;
  }).join('');
}

/* ── 今日推荐（来自 AI 匹配真实数据）── */
async function renderRecommend() {
  const el = $('dash-recommend');
  if (!el) return;

  try {
    // 先拿用户 profile
    const profile = await ProfileStore.get().catch(() => ({}));
    const kws = [
      ...(profile.major || '').split(/[,，、/\s]+/),
      ...(profile.intentJob || '').split(/[,，、/\s]+/),
    ].map(s => s.trim()).filter(Boolean);

    if (!kws.length) {
      el.innerHTML = `<div class="dash-empty-tip"><i class="ti ti-user"></i> <a data-goto="profile" style="color:var(--brand);cursor:pointer">填写个人信息</a> 后获取智能推荐</div>`;
      return;
    }

    const q = kws.slice(0, 4).join(',');
    const res = await JobStore.search({ q, size: 30, page: 0 });
    const jobs = (res.content || [])
      .map((j) => {
        const text = [j.positions, j.industry, j.co].join(' ').toLowerCase();
        const hits = kws.filter(kw => text.includes(kw.toLowerCase())).length;
        return { ...j, _score: Math.round((hits / kws.length) * 100) };
      })
      .filter(j => j._score > 0)
      .sort((a, b) => {
        // 未过期优先
        const aExp = isExpiredJob(a.deadline) ? 1 : 0;
        const bExp = isExpiredJob(b.deadline) ? 1 : 0;
        return aExp - bExp || b._score - a._score;
      })
      .slice(0, 4);

    if (!jobs.length) {
      el.innerHTML = `<div class="dash-empty-tip"><i class="ti ti-search-off"></i> 暂无匹配推荐，<a data-goto="aimatch" style="color:var(--brand);cursor:pointer">刷新AI匹配</a></div>`;
      return;
    }

    el.innerHTML = jobs.map((j) => {
      const color = j._score >= 70 ? '#10B981' : j._score >= 40 ? '#F59E0B' : '#9CA3AF';
      const dl = deadlineBadge(j.deadline);
      return `
        <div class="dash-reco-card">
          <div class="dash-reco-logo" style="background:${logoGrad(j.co)}">${(j.co||'?')[0]}</div>
          <div class="dash-reco-info">
            <div class="dash-reco-co">${j.co || ''}
              <span class="badge" style="font-size:10px;background:${color}18;color:${color}">${j._score}% 匹配</span>
            </div>
            <div class="dash-reco-pos">${(j.positions||'').slice(0,60)}${j.positions?.length>60?'…':''}</div>
            <div class="dash-reco-meta">
              ${j.city ? `<span><i class="ti ti-map-pin"></i>${j.city}</span>` : ''}
              ${j.recruitType ? `<span>${j.recruitType}</span>` : ''}
              ${dl}
            </div>
          </div>
          ${j.applyUrl ? `<a class="dash-reco-apply" href="${j.applyUrl}" target="_blank" rel="noopener"><i class="ti ti-send"></i>投递</a>` : ''}
        </div>`;
    }).join('');
  } catch (e) {
    el.innerHTML = `<div class="dash-empty-tip">加载推荐失败</div>`;
  }
}

function isExpiredJob(dl) {
  const m = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(dl || '');
  if (!m) return false;
  return new Date(+m[1], +m[2]-1, +m[3]) < new Date(new Date().toDateString());
}

function deadlineBadge(dl) {
  const diff = dayDiff(dl);
  if (diff === null) return dl ? `<span>${dl}</span>` : '';
  if (diff < 0)  return `<span style="color:#9CA3AF">已截止</span>`;
  if (diff === 0) return `<span style="color:#EF4444;font-weight:600">今日截止</span>`;
  if (diff <= 7)  return `<span style="color:#F59E0B">还剩${diff}天</span>`;
  return `<span>截止${dl?.slice(5)}</span>`;
}

const LOGO_COLORS = [
  ['#6366F1','#8B5CF6'],['#0EA5E9','#06B6D4'],['#10B981','#34D399'],
  ['#F59E0B','#F97316'],['#EF4444','#F43F5E'],['#8B5CF6','#EC4899'],
  ['#14B8A6','#06B6D4'],['#3B82F6','#6366F1'],
];
function logoGrad(name) {
  const idx = (name||'?').charCodeAt(0) % LOGO_COLORS.length;
  const [a, b] = LOGO_COLORS[idx];
  return `linear-gradient(135deg,${a},${b})`;
}

/* ── 主渲染入口 ── */
async function renderAll() {
  const apps = await Store.getAll().catch(() => []);
  await Promise.all([
    renderStats(apps),
    renderFunnel(apps),
    renderDeadlines(apps),
    renderStatusDist(apps),
    renderActivity(apps),
  ]);
}

export function initDashboard() {
  renderAll();
  renderRecommend();
  renderTodayPill();
  on(EVT.APPS_CHANGED, renderAll);
}
