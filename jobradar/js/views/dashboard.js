/**
 * views/dashboard.js — 「总览」页面视图
 * ─────────────────────────────────────────────
 * 统计卡 / 投递漏斗 / 即将截止 全部由「我的投递」数据实时派生，
 * 今日推荐来自 RecoStore。投递数据变化时（监听总线）自动刷新。
 */
import { Store } from '../data/store.js';
import { RecoStore } from '../data/catalog.js';
import { formatDeadline, isUrgent } from '../core/format.js';
import { on, EVT } from '../core/bus.js';

const FUNNEL = [
  { key: '已投递', label: '已投递', color: '#1A56DB' },
  { key: '已笔试', label: '已笔试', color: '#5B8AF5' },
  { key: '已面试', label: '已面试', color: '#0E9F6E' },
  { key: '已OC',  label: '已获 OC', color: '#059669' },
  { key: '已挂',  label: '已挂',   color: '#9CA3AF' },
];

/** 该投递是否「到达过」某阶段（看当前状态或历史时间线） */
function reached(app, stage) {
  return app.status === stage || app.logs.some((l) => l.s === stage);
}

async function renderStats() {
  const el = document.getElementById('dash-stats');
  if (!el) return;
  const apps = await Store.getAll();

  const total     = apps.length;
  const submitted = apps.filter((a) => a.status !== '未投递').length;
  const interview = apps.filter((a) => reached(a, '已面试')).length;
  const urgent    = apps.filter((a) => isUrgent(a.deadline)).length;
  const oc        = apps.filter((a) => a.status === '已OC').length;
  const rate      = submitted ? Math.round((interview / submitted) * 100) : 0;

  const cards = [
    { label: '已投递',  val: submitted, cls: 'blue',   note: `共 ${total} 个岗位` },
    { label: '已获面试', val: interview, cls: 'green',  note: `面试转化率 ${rate}%` },
    { label: '即将截止', val: urgent,    cls: 'orange', note: '3 天内截止' },
    { label: '已拿 OC',  val: oc,        cls: 'green',  note: '继续加油！' },
  ];

  el.innerHTML = cards.map((c) => `
    <div class="stat-card">
      <div class="stat-label">${c.label}</div>
      <div class="stat-val ${c.cls}">${c.val}</div>
      <div class="stat-note">${c.note}</div>
    </div>`).join('');
}

async function renderFunnel() {
  const el = document.getElementById('dash-funnel');
  if (!el) return;
  const apps = await Store.getAll();

  const counts = FUNNEL.map((f) =>
    f.key === '已挂'
      ? apps.filter((a) => a.status === '已挂').length
      : f.key === '已投递'
        ? apps.filter((a) => a.status !== '未投递').length
        : apps.filter((a) => reached(a, f.key)).length
  );
  const max = Math.max(1, ...counts);

  el.innerHTML = FUNNEL.map((f, i) => {
    const n = counts[i];
    const w = Math.round((n / max) * 100);
    return `
      <div class="funnel-row">
        <span class="funnel-label">${f.label}</span>
        <div class="funnel-bar-wrap"><div class="funnel-bar" style="width:${w}%;background:${f.color}">${n}</div></div>
        <span class="funnel-num">${n}</span>
      </div>`;
  }).join('');
}

async function renderDeadlines() {
  const el = document.getElementById('dash-deadlines');
  if (!el) return;
  const apps = await Store.getAll();

  const upcoming = apps
    .filter((a) => a.deadline && a.deadline !== '招满为止')
    .map((a) => ({ ...a, diff: Math.ceil((new Date(a.deadline) - new Date()) / 86400000) }))
    .filter((a) => a.diff >= 0)
    .sort((x, y) => x.diff - y.diff)
    .slice(0, 4);

  if (!upcoming.length) {
    el.innerHTML = `<div style="font-size:13px;color:var(--c-text-2);padding:10px 0">近期暂无临近截止的投递。</div>`;
    return;
  }

  el.innerHTML = upcoming.map((a) => {
    const color = a.diff <= 1 ? 'red' : a.diff <= 3 ? 'orange' : 'blue';
    return `
      <div class="deadline-item">
        <div class="deadline-dot ${color}"></div>
        <div>
          <div class="deadline-co">${a.co}</div>
          <div class="deadline-pos">${a.pos} · ${a.type}</div>
        </div>
        <div class="deadline-date ${color}">${formatDeadline(a.deadline)}</div>
      </div>`;
  }).join('');
}

async function renderRecommend() {
  const el = document.getElementById('dash-recommend');
  if (!el) return;
  const recos = (await RecoStore.getAll()).slice(0, 3);
  el.innerHTML = recos.map((r) => `
    <tr>
      <td><b>${r.co}</b></td>
      <td>${r.pos}</td>
      <td>${r.city}</td>
      <td>${formatDeadline(r.deadline)}</td>
      <td><span class="badge ${r.match >= 80 ? 'b-green' : 'b-blue'}">${r.match}%</span></td>
      <td><button class="btn sm" data-goto="addjob"><i class="ti ti-plus"></i>添加</button></td>
    </tr>`).join('');
}

function renderAll() {
  renderStats();
  renderFunnel();
  renderDeadlines();
}

export function initDashboard() {
  renderAll();
  renderRecommend();
  /* 投递数据变化时，总览统计实时跟随 */
  on(EVT.APPS_CHANGED, renderAll);
}
