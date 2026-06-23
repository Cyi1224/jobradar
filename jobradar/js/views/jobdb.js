/**
 * views/jobdb.js — 「校招信息库」页面视图（数字分页，50条/页，上下分页栏）
 */
import { JobStore, JOB_BADGE } from '../data/catalog.js';
import { Store } from '../data/store.js';
import { emit, on, EVT } from '../core/bus.js';
import { showToast } from '../core/toast.js';

const PAGE_SIZE = 50;
const CITY_OPTS = ['北京', '上海', '深圳', '广州', '杭州', '成都', '南京', '武汉', '西安', '苏州', '天津', '重庆'];

export function initJobdb() {
  const filters = { q: '', recruitType: '', industry: '', city: '', apply: false, urgent: false, soe: false, inst: false, foreign: false, updatedAt: '' };
  let currentPage = 0, total = 0, totalPages = 0, capped = false;
  let items = [];
  let loading = false;
  let view = 'card';
  let todayDate = '';
  let addedKeys = new Set();

  const pageEl   = document.getElementById('page-jobdb');
  const cardsEl  = document.getElementById('jobdb-cards');
  const tableEl  = document.getElementById('jobdb-table');
  const tbody    = document.getElementById('jobdb-tbody');
  const topPager = document.getElementById('jobdb-top-pager');
  const footer   = document.getElementById('jobdb-pagination');
  const search   = document.getElementById('jobdb-search');
  const fRecruit = document.getElementById('jobdb-f-recruit');
  const fIndustry= document.getElementById('jobdb-f-industry');
  const fCity    = document.getElementById('jobdb-f-city');
  const countInfo= document.getElementById('jdb-count-info');
  const resetBtn = document.getElementById('jdb-reset');
  const scroller = pageEl?.querySelector('.content');

  /* ── 工具 ── */
  const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const key = (co, pos) => `${co}__${pos}`;
  const isDate = (s) => /\d{4}-\d{1,2}-\d{1,2}/.test(s || '');
  const TODAY_MS = new Date(new Date().toDateString()).getTime();
  function isExpired(dl) {
    const m = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(dl || '');
    if (!m) return false;
    return new Date(+m[1], +m[2] - 1, +m[3]).getTime() < TODAY_MS;
  }
  function mmdd(s) { const m = /(\d{4})-(\d{1,2})-(\d{1,2})/.exec(s || ''); return m ? `${+m[2]}月${+m[3]}日` : ''; }
  function dayDiff(dl) { const m = /(\d{4})-(\d{1,2})-(\d{1,2})/.exec(dl || ''); return m ? Math.ceil((new Date(+m[1], +m[2] - 1, +m[3]) - new Date()) / 86400000) : null; }
  const within7 = (dl) => { const d = dayDiff(dl); return d !== null && d >= 0 && d <= 7; };
  function deadlineCell(dl) {
    if (!dl) return { text: '—', urgent: false };
    if (!isDate(dl)) return { text: dl, urgent: false };
    const m = /(\d{4})-(\d{1,2})-(\d{1,2})/.exec(dl);
    return { text: `截止 ${String(+m[2]).padStart(2, '0')}/${String(+m[3]).padStart(2, '0')}`, urgent: within7(dl) };
  }

  /* ── 卡片 / 表格 ── */
  function cardHtml(j) {
    const dc = deadlineCell(j.deadline);
    const added = addedKeys.has(key(j.co, j.positions));
    const isNew = j.updatedAt && j.updatedAt === todayDate;
    const expired = isExpired(j.deadline);
    return `
      <div class="job-card${expired ? ' job-card--expired' : ''}">
        <div class="jc-top">
          <div class="jc-tags">
            <span class="badge b-gray">${esc(j.target)}</span>
            <span class="badge ${JOB_BADGE.recruit[j.recruitType] || 'b-gray'}">${esc(j.recruitType)}</span>
          </div>
          <div class="jc-top-right">
            <span class="jc-deadline ${dc.urgent ? 'urgent' : ''}">${esc(dc.text)}</span>
            ${j.updatedAt ? `<span class="jc-updated">更新 ${mmdd(j.updatedAt)}</span>` : ''}
          </div>
        </div>
        <div class="jc-co">${esc(j.co)}${isNew ? ' <span class="badge b-amber jobdb-new">NEW</span>' : ''}</div>
        <div class="jc-meta">
          <span class="badge ${JOB_BADGE.coType[j.coType] || 'b-gray'}">${esc(j.coType)}</span>
          <span class="jc-city"><i class="ti ti-map-pin"></i>${esc(j.city)}</span>
          ${j.industry ? `<span class="jc-industry">${esc(j.industry)}</span>` : ''}
        </div>
        <div class="jc-pos"><i class="ti ti-briefcase"></i><span>${esc(j.positions)}</span></div>
        <div class="jc-actions">
          ${j.applyUrl
            ? `<a class="btn jc-apply" href="${esc(j.applyUrl)}" target="_blank" rel="noopener"><i class="ti ti-external-link"></i>投递入口</a>`
            : `<button class="btn jc-apply" disabled><i class="ti ti-external-link"></i>暂无入口</button>`}
          <button class="btn primary jc-add" data-add="${j.id}" ${added ? 'disabled' : ''}>
            <i class="ti ti-${added ? 'check' : 'circle-plus'}"></i>${added ? '已加入' : '加入我的投递'}
          </button>
        </div>
      </div>`;
  }
  function rowHtml(j) {
    const added = addedKeys.has(key(j.co, j.positions));
    const dc = deadlineCell(j.deadline);
    const isNew = j.updatedAt && j.updatedAt === todayDate;
    const expired = isExpired(j.deadline);
    return `
      <tr class="${expired ? 'row--expired' : ''}">
        <td><b>${esc(j.co)}</b>${isNew ? ' <span class="badge b-amber jobdb-new">NEW</span>' : ''}</td>
        <td><span class="badge ${JOB_BADGE.coType[j.coType] || 'b-gray'}">${esc(j.coType)}</span></td>
        <td>${esc(j.industry)}</td>
        <td><span class="badge ${JOB_BADGE.recruit[j.recruitType] || 'b-gray'}">${esc(j.recruitType)}</span></td>
        <td>${esc(j.target)}</td>
        <td>${esc(j.city)}</td>
        <td>${esc(j.positions)}</td>
        <td class="${dc.urgent ? 'jdb-urgent' : ''}">${esc(dc.text)}</td>
        <td style="white-space:nowrap">
          ${j.applyUrl ? `<a class="btn sm" href="${esc(j.applyUrl)}" target="_blank" rel="noopener" title="打开投递页"><i class="ti ti-external-link"></i>投递</a>` : ''}
          <button class="btn sm primary" data-add="${j.id}" ${added ? 'disabled' : ''}><i class="ti ti-${added ? 'check' : 'circle-plus'}"></i>${added ? '已加入' : '加入'}</button>
        </td>
      </tr>`;
  }

  /* ── 今日新增卡片点击 & 横幅 ── */
  const todayCard   = document.getElementById('jdb-stat-today-card');
  const todayNote   = document.getElementById('jdb-stat-today-note');
  const todayBanner = document.getElementById('today-banner');
  const bannerCount = document.getElementById('today-banner-count');
  const bannerExit  = document.getElementById('today-banner-exit');

  function setTodayFilter(on) {
    filters.updatedAt = on ? todayDate : '';
    todayCard?.classList.toggle('active', on);
    if (todayNote) todayNote.textContent = on ? '▶ 点击取消筛选' : '今日新增已入库';
    if (todayBanner) todayBanner.style.display = on ? 'flex' : 'none';
    applyFilters();
  }
  function updateBannerCount() {
    if (bannerCount && filters.updatedAt) bannerCount.textContent = total;
  }
  todayCard?.addEventListener('click', () => { if (!todayDate) return; setTodayFilter(!filters.updatedAt); });
  bannerExit?.addEventListener('click', () => setTodayFilter(false));

  /* ── 分页工具 ── */
  function getPageNums(cur, tp) {
    if (tp <= 9) return Array.from({ length: tp }, (_, i) => i);
    const s = new Set([0, 1, tp - 2, tp - 1]);
    for (let i = cur - 2; i <= cur + 2; i++) { if (i >= 0 && i < tp) s.add(i); }
    const sorted = [...s].sort((a, b) => a - b);
    const result = [];
    let prev = -2;
    for (const n of sorted) {
      if (n > prev + 1) result.push('…');
      result.push(n);
      prev = n;
    }
    return result;
  }

  function renderPagination(container) {
    if (!container) return;
    if (loading) {
      container.innerHTML = `<div class="pg-bar"><span class="pg-loading"><i class="ti ti-loader-2 jdb-spin"></i> 加载中…</span></div>`;
      return;
    }
    if (!total) { container.innerHTML = ''; return; }

    const tp = totalPages || Math.ceil(total / PAGE_SIZE);
    if (tp <= 1) {
      container.innerHTML = `<div class="pg-bar"><span class="pg-info">共 ${total} 条</span>${capped ? capHtml() : ''}</div>`;
      return;
    }

    const nums = getPageNums(currentPage, tp);
    const btnHtml = nums.map(n => n === '…'
      ? `<span class="pg-ellipsis">…</span>`
      : `<button class="pg-num${n === currentPage ? ' active' : ''}" data-pg="${n}">${n + 1}</button>`
    ).join('');

    container.innerHTML = `
      <div class="pg-bar">
        <button class="pg-arrow" data-pg="${currentPage - 1}" ${currentPage === 0 ? 'disabled' : ''}>
          <i class="ti ti-chevron-left"></i><span>上一页</span>
        </button>
        <div class="pg-nums">${btnHtml}</div>
        <button class="pg-arrow" data-pg="${currentPage + 1}" ${currentPage >= tp - 1 ? 'disabled' : ''}>
          <span>下一页</span><i class="ti ti-chevron-right"></i>
        </button>
        <span class="pg-info">第 ${currentPage + 1}/${tp} 页 · 共 ${total} 条</span>
        ${capped ? capHtml() : ''}
      </div>`;

    container.querySelectorAll('[data-pg]').forEach(btn => {
      btn.addEventListener('click', () => {
        const pg = +btn.dataset.pg;
        if (isNaN(pg) || pg < 0 || pg >= tp || pg === currentPage) return;
        currentPage = pg;
        if (scroller) scroller.scrollTop = 0;
        loadPage();
      });
    });
  }

  function capHtml() {
    return `<button class="btn sm primary pg-cap" data-goto="pricing"><i class="ti ti-crown"></i>升级查看全部</button>`;
  }

  /* ── 渲染 ── */
  function render() {
    const todayActive = !!filters.updatedAt;
    countInfo.textContent = todayActive
      ? `今日新增 ${total} 条招聘信息`
      : `当前可查看 ${total} 条招聘信息`;

    if (!items.length) {
      const empty = `<div class="jdb-empty"><i class="ti ti-search-off"></i>${loading ? '加载中…' : '没有符合条件的岗位'}</div>`;
      cardsEl.style.display = view === 'card' ? '' : 'none';
      tableEl.style.display = view === 'table' ? '' : 'none';
      cardsEl.innerHTML = view === 'card' ? empty : '';
      if (tbody) tbody.innerHTML = view === 'table' ? `<tr><td colspan="9">${empty}</td></tr>` : '';
      renderPagination(topPager);
      renderPagination(footer);
      return;
    }

    if (view === 'card') {
      cardsEl.style.display = ''; tableEl.style.display = 'none';
      cardsEl.innerHTML = items.map(cardHtml).join('');
    } else {
      cardsEl.style.display = 'none'; tableEl.style.display = '';
      tbody.innerHTML = items.map(rowHtml).join('');
    }

    renderPagination(topPager);
    renderPagination(footer);
    updateBannerCount();
  }

  /* ── 拉取当前页 ── */
  async function loadPage() {
    if (loading) return;
    loading = true;
    render();
    try {
      const res = await JobStore.search({ ...filters, page: currentPage, size: PAGE_SIZE });
      items      = res.content || [];
      total      = res.total || 0;
      totalPages = res.totalPages || 0;
      capped     = !!res.capped;
    } catch (e) {
      console.warn('[jobdb] 拉取失败：', e.message);
      showToast('加载失败，请重试');
    } finally {
      loading = false;
      render();
    }
  }

  const applyFilters = () => { currentPage = 0; if (scroller) scroller.scrollTop = 0; loadPage(); };

  /* ── 「加入我的投递」 ── */
  async function addToApplications(job) {
    const k = key(job.co, job.positions);
    if (addedKeys.has(k)) return;
    addedKeys.add(k);
    render();
    await Store.add({
      co: job.co, pos: job.positions, type: job.recruitType || '秋招',
      city: job.city || '—', deadline: job.deadline || '招满为止', status: '未投递',
      note: job.applyUrl ? `投递入口：${job.applyUrl}` : '',
    });
    emit(EVT.APPS_CHANGED);
    showToast(`已加入我的投递：${job.co} · ${job.positions}`);
  }

  /* ── 事件委托 ── */
  pageEl.addEventListener('click', (e) => {
    const addBtn = e.target.closest('[data-add]');
    if (addBtn) { const j = items.find((x) => String(x.id) === addBtn.dataset.add); if (j) addToApplications(j); return; }
    const chip = e.target.closest('.jdb-chip');
    if (chip) { const c = chip.dataset.chip; filters[c] = !filters[c]; chip.classList.toggle('active', filters[c]); applyFilters(); return; }
    const vb = e.target.closest('.jdb-view');
    if (vb && vb.dataset.view !== view) { view = vb.dataset.view; pageEl.querySelectorAll('.jdb-view').forEach((b) => b.classList.toggle('active', b === vb)); render(); }
  });

  let searchTimer;
  search?.addEventListener('input', () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => { filters.q = search.value.trim(); applyFilters(); }, 250);
  });
  fRecruit?.addEventListener('change',  () => { filters.recruitType = fRecruit.value;  applyFilters(); });
  fIndustry?.addEventListener('change', () => { filters.industry    = fIndustry.value; applyFilters(); });
  fCity?.addEventListener('change',     () => { filters.city        = fCity.value;     applyFilters(); });

  resetBtn?.addEventListener('click', () => {
    if (search) search.value = '';
    [fRecruit, fIndustry, fCity].forEach((s) => { if (s) s.value = ''; });
    Object.assign(filters, { q: '', recruitType: '', industry: '', city: '', apply: false, urgent: false, soe: false, inst: false, foreign: false, updatedAt: '' });
    pageEl.querySelectorAll('.jdb-chip.active').forEach((c) => c.classList.remove('active'));
    setTodayFilter(false);
  });

  on(EVT.APPS_CHANGED, () => { refreshAdded().then(render); });
  async function refreshAdded() { const apps = await Store.getAll(); addedKeys = new Set(apps.map((a) => key(a.co, a.pos))); }

  function fillSelect(sel, values) {
    if (!sel) return;
    const first = sel.querySelector('option');
    sel.innerHTML = '';
    if (first) sel.appendChild(first);
    values.forEach((v) => { const o = document.createElement('option'); o.value = v; o.textContent = v; sel.appendChild(o); });
  }
  function setText(id, v) { const el = document.getElementById(id); if (el) el.textContent = v; }

  /* ── 初始化 ── */
  JobStore.stats().then((s) => {
    todayDate = s.todayDate || '';
    setText('jdb-stat-co',    s.companies ?? 0);
    setText('jdb-stat-total', s.total ?? 0);
    setText('jdb-stat-open',  s.open  ?? 0);
    setText('jdb-stat-today', s.today ?? 0);
    // 更新 hero 区 pill 标签
    const heroTag = document.getElementById('jdb-hero-tag-count');
    if (heroTag) heroTag.textContent = `今日新增 ${s.today ?? 0} 条`;
    const expiredEl = document.getElementById('jdb-stat-open-note');
    if (expiredEl && s.expired > 0) {
      expiredEl.innerHTML = `<span style="color:#9ca3af">${s.expired} 个已过期</span>`;
    }
    fillSelect(fRecruit, s.recruitTypes || []);
    fillSelect(fIndustry, s.industries || []);
    fillSelect(fCity, CITY_OPTS);
  }).catch((e) => console.warn('[jobdb] stats 失败：', e.message));

  Store.getAll()
    .then((apps) => { addedKeys = new Set(apps.map((a) => key(a.co, a.pos))); render(); })
    .catch(() => { addedKeys = new Set(); });

  loadPage();
}
