/**
 * views/jobdb.js — 「校招信息库」页面视图（数字分页，50条/页，上下分页栏）
 */
import { JobStore, JOB_BADGE } from '../data/catalog.js';
import { Store } from '../data/store.js';
import { Auth } from '../core/auth.js';
import { emit, on, EVT } from '../core/bus.js';
import { showToast } from '../core/toast.js';

const PAGE_SIZE = 50;
const CITY_OPTS = ['北京', '上海', '深圳', '广州', '杭州', '成都', '南京', '武汉', '西安', '苏州', '天津', '重庆'];

export function initJobdb() {
  const filters = { q: '', recruitType: '', industry: '', city: '', target: '', apply: false, urgent: false, soe: false, inst: false, foreign: false, updatedAt: '' };
  let currentPage = 0, total = 0, totalPages = 0, capped = false;
  let items = [];
  let loading = false;
  // 手机端和桌面端统一默认卡片视图
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
  const fTarget  = document.getElementById('jobdb-f-target');
  const fRecruit = document.getElementById('jobdb-f-recruit');
  const fIndustry= document.getElementById('jobdb-f-industry');
  const fCity    = document.getElementById('jobdb-f-city');
  const countInfo= document.getElementById('jdb-count-info');
  const resetBtn = document.getElementById('jdb-reset');
  const scroller = pageEl?.querySelector('.content');

  /* ── 工具 ── */
  const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  // 未登录：岗位/地点显示遮罩提示（点击由 main.js 统一拦截弹登录）
  const maskHint = () => `<span class="jdb-mask"><i class="ti ti-lock"></i>登录后查看</span>`;
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
    const anon = !Auth.isLoggedIn();
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
          <span class="jc-city">${anon ? maskHint() : `<i class="ti ti-map-pin"></i>${esc(j.city)}`}</span>
          ${j.industry ? `<span class="jc-industry">${esc(j.industry)}</span>` : ''}
        </div>
        <div class="jc-pos"><i class="ti ti-briefcase"></i>${anon ? maskHint() : `<span>${esc(j.positions)}</span>`}</div>
        <div class="jc-actions">
          ${j.applyUrl
            ? (Auth.isLoggedIn() && Auth.isMember()
              ? `<button class="btn jc-apply jc-apply-btn" data-url="${esc(j.applyUrl)}"><i class="ti ti-external-link"></i>投递入口</button>`
              : `<button class="btn jc-apply jc-apply-btn" data-url="${esc(j.applyUrl)}" style="position:relative"><i class="ti ti-external-link"></i>投递入口<span style="font-size:10px;opacity:.7;margin-left:2px">（免费用${Auth.freeApplyLeft()}次）</span></button>`)
            : `<button class="btn jc-apply" disabled><i class="ti ti-external-link"></i>暂无入口</button>`}
          <button class="btn primary jc-add" data-add="${j.id}" ${added ? 'disabled' : ''}>
            <i class="ti ti-${added ? 'check' : 'circle-plus'}"></i>${added ? '已加入' : '加入我的投递'}
          </button>
        </div>
      </div>`;
  }
  function rowHtml(j) {
    const anon = !Auth.isLoggedIn();
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
        <td>${anon ? maskHint() : esc(j.city)}</td>
        <td>${anon ? maskHint() : esc(j.positions)}</td>
        <td class="${dc.urgent ? 'jdb-urgent' : ''}">${esc(dc.text)}</td>
        <td style="white-space:nowrap">
          ${j.applyUrl ? (Auth.isLoggedIn() && Auth.isMember()
            ? `<button class="btn sm jc-apply-btn" data-url="${esc(j.applyUrl)}" style="white-space:nowrap"><i class="ti ti-external-link"></i>投递</button>`
            : (Auth.isLoggedIn()
              ? `<button class="btn sm jc-apply-btn" data-url="${esc(j.applyUrl)}" style="white-space:nowrap"><i class="ti ti-external-link"></i>投递<span style="font-size:10px;opacity:.7">(${Auth.freeApplyLeft()}次)</span></button>`
              : `<button class="btn sm" onclick="document.getElementById('auth-modal').style.display='flex'" style="white-space:nowrap;color:var(--brand)"><i class="ti ti-lock"></i>登录查看</button>`)) : ''}
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

    // 免费用户到最后一页：下一页变成"解锁全部"，点击弹升级/注册引导而不是加载空页
    const atCap = capped && currentPage >= tp - 1;
    const nextBtnHtml = atCap
      ? `<button class="pg-arrow pg-cap-next" data-pg="${currentPage + 1}"><i class="ti ti-crown"></i><span>解锁全部</span></button>`
      : `<button class="pg-arrow" data-pg="${currentPage + 1}" ${currentPage >= tp - 1 ? 'disabled' : ''}><span>下一页</span><i class="ti ti-chevron-right"></i></button>`;

    container.innerHTML = `
      <div class="pg-bar">
        <button class="pg-arrow" data-pg="${currentPage - 1}" ${currentPage === 0 ? 'disabled' : ''}>
          <i class="ti ti-chevron-left"></i><span>上一页</span>
        </button>
        <div class="pg-nums">${btnHtml}</div>
        ${nextBtnHtml}
        <span class="pg-info">第 ${currentPage + 1}/${tp} 页 · 共 ${total} 条</span>
        ${capped ? capHtml() : ''}
      </div>`;

    container.querySelectorAll('[data-pg]').forEach(btn => {
      btn.addEventListener('click', () => {
        const pg = +btn.dataset.pg;
        if (isNaN(pg)) return;
        // 免费用户点击"解锁全部"想越过前 5 页上限 → 弹付费/注册引导，不加载空页
        if (capped && !Auth.isMember() && pg >= tp) { handleCapPaywall(); return; }
        if (pg < 0 || pg >= tp || pg === currentPage) return;
        currentPage = pg;
        if (scroller) scroller.scrollTop = 0;
        loadPage();
      });
    });
  }

  function capHtml() {
    return `<button class="btn sm primary pg-cap" data-goto="pricing"><i class="ti ti-crown"></i>升级查看全部</button>`;
  }

  /* 免费用户触达翻页上限：登录非会员弹升级，未登录引导注册（复用现成弹窗） */
  function handleCapPaywall() {
    if (Auth.isLoggedIn()) {
      Auth.requireMember('jobdb');
    } else if (window.openAuthModal) {
      window.openAuthModal('register');
    } else {
      document.getElementById('auth-modal').style.display = 'flex';
    }
  }

  /* ── 渲染 ── */
  function render() {
    const anon = !Auth.isLoggedIn();
    const todayActive = !!filters.updatedAt;
    // 未登录：显示遮罩骨架卡片，岗位/地点为「登录后查看」提示
    if (countInfo) countInfo.textContent = anon
      ? '登录后查看岗位详情'
      : (todayActive ? `今日新增 ${total} 条招聘信息` : `当前可查看 ${total} 条招聘信息`);

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
    // 已登录：移除任何残留的注册CTA/引导横幅
    if (Auth.isLoggedIn()) {
      const cta = document.getElementById('reg-cta');
      if (cta) cta.remove();
      const bn = document.getElementById('reg-banner');
      if (bn) bn.remove();
    }
    // 未登录浏览到第3页时弹出注册引导，或底部常驻CTA
    if (!Auth.isLoggedIn()) {
      if (currentPage >= 3) showRegBanner();
      showRegCTA(total);
    }
  }

  /* 第3页注册引导横幅 */
  function showRegBanner() {
    if (document.getElementById('reg-banner')) return;
    const banner = document.createElement('div');
    banner.id = 'reg-banner';
    banner.style.cssText = 'padding:16px 20px;margin-bottom:12px;background:linear-gradient(135deg,#EEF2FF,#F0FDF4);border:1px solid #A7F3D0;border-radius:12px;text-align:center';
    banner.innerHTML = '<div style="font-weight:600;font-size:14px;margin-bottom:4px">📋 看到第 ' + (currentPage + 1) + ' 页了！</div>' +
      '<div style="font-size:13px;color:var(--c-text-2);margin-bottom:10px">注册后可无限浏览全部 ' + total + ' 个校招岗位，还能管理投递进度</div>' +
      '<button class="btn primary" data-auth-open style="padding:8px 24px;font-size:14px;border-radius:8px">立即注册 · 免费使用</button>';
    const container = document.getElementById('jdb-footer') || document.querySelector('#page-jobdb .content') || document.getElementById('page-jobdb');
    if (container) {
      const cardsEl = document.getElementById('jobdb-cards');
      if (cardsEl && cardsEl.nextSibling) {
        cardsEl.parentNode.insertBefore(banner, cardsEl.nextSibling);
      } else {
        container.appendChild(banner);
      }
    }
  }

  /* 未登录底部CTA卡片（已登录或用户关闭后不再显示） */
  function showRegCTA(totalJobs) {
    if (document.getElementById('reg-cta')) return;
    // 用户手动关闭过，不再显示
    if (localStorage.getItem('jr_cta_closed') === '1') return;
    const cta = document.createElement('div');
    cta.id = 'reg-cta';
    cta.style.cssText = 'position:relative;padding:24px 20px;margin-top:16px;background:linear-gradient(135deg,var(--brand),#6366F1);border-radius:12px;text-align:center;color:#fff';
    cta.innerHTML = '<button class="reg-cta-x" title="关闭" style="position:absolute;top:8px;right:10px;width:26px;height:26px;border:none;background:rgba(255,255,255,.15);color:#fff;font-size:16px;cursor:pointer;border-radius:50%;display:flex;align-items:center;justify-content:center;line-height:1">×</button>' +
      '<div style="font-size:24px;margin-bottom:6px">📊</div>' +
      '<div style="font-size:16px;font-weight:700;margin-bottom:4px">加入 ' + totalJobs + ' 个校招岗位的管理</div>' +
      '<div style="font-size:13px;opacity:0.85;margin-bottom:12px">一键追踪投递进度，AI 匹配推荐，在线制作简历</div>' +
      '<button class="btn" data-auth-open style="background:#fff;color:var(--brand);padding:10px 32px;font-size:14px;font-weight:600;border-radius:8px;border:none;cursor:pointer;box-shadow:0 2px 8px rgba(0,0,0,.1)">免费注册</button>';
    // 关闭按钮：点击后移除并记住（不再显示）
    cta.querySelector('.reg-cta-x').addEventListener('click', (e) => {
      e.stopPropagation();
      e.preventDefault();
      cta.remove();
      localStorage.setItem('jr_cta_closed', '1');
    });
    const footer = document.getElementById('jdb-footer');
    if (footer) footer.parentNode.insertBefore(cta, footer);
    else {
      const page = document.getElementById('page-jobdb');
      if (page) page.appendChild(cta);
    }
  }

  /* ── 拉取当前页 ── */
  async function loadPage() {
    if (loading) return;
    loading = true;
    render();
    try {
      const res = await JobStore.search({ ...filters, page: currentPage, size: PAGE_SIZE });
      // 兜底：免费用户仍返回空页（第 6 页及以上）→ 回退到最后一页并弹付费/注册引导
      if (res.capped && !Auth.isMember() && res.content.length === 0 && currentPage >= (res.totalPages || 5)) {
        currentPage = Math.max(0, (res.totalPages || 5) - 1);
        handleCapPaywall();
      } else {
        items      = res.content || [];
        total      = res.total || 0;
        totalPages = res.totalPages || 0;
        capped     = !!res.capped;
      }
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
    // 未登录先弹窗
    if (!Auth.isLoggedIn()) {
      document.getElementById('auth-modal').style.display = 'flex';
      document.getElementById('auth-account')?.focus();
      return;
    }
    const k = key(job.co, job.positions);
    if (addedKeys.has(k)) return;
    addedKeys.add(k);
    render();
    await Store.add({
      co: job.co, pos: job.positions, type: job.recruitType || '秋招',
      city: job.city || '—', deadline: job.deadline || '招满为止', status: '待投递',
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
    // 投递入口：会员无限 / 登录非会员5次 / 未登录3次（设备+IP双重限制）
    const applyBtn = e.target.closest('.jc-apply-btn');
    if (applyBtn) {
      e.preventDefault();
      e.stopPropagation();
      var url = applyBtn.dataset.url;
      if (url && window.Auth) {
        var ok = window.Auth.tryFreeApply(url);
        if (ok) {
          // 使用成功 → 通知后端消耗一次 IP 额度
          window.Auth.reportIpUse();
        }
        // 刷新剩余次数提示（非会员）
        render();
      }
      return;
    }
  });

  let searchTimer;
  search?.addEventListener('input', () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => { filters.q = search.value.trim(); applyFilters(); }, 250);
  });
  fTarget?.addEventListener('change',   () => { filters.target = fTarget.value;       applyFilters(); });
  fRecruit?.addEventListener('change',  () => { filters.recruitType = fRecruit.value;  applyFilters(); });
  fIndustry?.addEventListener('change', () => { filters.industry    = fIndustry.value; applyFilters(); });
  fCity?.addEventListener('change',     () => { filters.city        = fCity.value;     applyFilters(); });

  resetBtn?.addEventListener('click', () => {
    if (search) search.value = '';
    [fTarget, fRecruit, fIndustry, fCity].forEach((s) => { if (s) s.value = ''; });
    Object.assign(filters, { q: '', recruitType: '', industry: '', city: '', target: '', apply: false, urgent: false, soe: false, inst: false, foreign: false, updatedAt: '' });
    pageEl.querySelectorAll('.jdb-chip.active').forEach((c) => c.classList.remove('active'));
    setTodayFilter(false);
  });

  on(EVT.APPS_CHANGED, () => { refreshAdded().then(render); });
  async function refreshAdded() { const apps = await Store.getAll(); addedKeys = new Set(apps.map((a) => key(a.co, a.pos))); }

  // 登录成功解锁岗位库（重新拉取数据）；登出重新锁定（loadPage 内部按登录态分支）
  on(EVT.AUTH_CHANGED, () => loadPage());

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
