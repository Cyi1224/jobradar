/**
 * core/router.js — 极简前端路由（页面切换）
 */

let prevPage = null;   // 上一个页面 id，用于「返回总览」按钮

/** 切换到指定页面，并同步侧边栏激活态 */
export function switchPage(pageId) {
  const current = document.querySelector('.page.active')?.id?.replace('page-', '');
  prevPage = current || null;

  document.querySelectorAll('.page').forEach((p) => p.classList.remove('active'));
  document.querySelectorAll('.nav-item[data-page]').forEach((n) => n.classList.remove('active'));

  const pageEl = document.getElementById('page-' + pageId);
  if (pageEl) pageEl.classList.add('active');

  const navEl = document.querySelector(`.nav-item[data-page="${pageId}"]`);
  if (navEl) navEl.classList.add('active');

  updateBackBtn(pageId);
  window.scrollTo(0, 0);
}

/** 非总览页面在 topbar 注入「← 返回总览」按钮 */
function updateBackBtn(pageId) {
  // 移除已有按钮
  document.querySelectorAll('.topbar-back-btn').forEach((b) => b.remove());
  if (pageId === 'dashboard') return;

  const topbar = document.querySelector(`#page-${pageId} .topbar`);
  if (!topbar) return;

  const btn = document.createElement('button');
  btn.className = 'btn topbar-back-btn';
  btn.innerHTML = '<i class="ti ti-arrow-left"></i>返回总览';
  btn.addEventListener('click', () => switchPage('dashboard'));
  topbar.prepend(btn);
}

/** 绑定侧边栏与所有 [data-goto] 跳转入口 */
export function initRouter() {
  document.querySelectorAll('.nav-item[data-page]').forEach((el) => {
    el.addEventListener('click', () => switchPage(el.dataset.page));
  });

  /* 用事件委托处理 [data-goto]，这样动态渲染出来的「添加」按钮也能跳转 */
  document.addEventListener('click', (e) => {
    const target = e.target.closest('[data-goto]');
    if (!target) return;
    e.stopPropagation();
    switchPage(target.dataset.goto);
  });
}
