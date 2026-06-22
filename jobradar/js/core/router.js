/**
 * core/router.js — 极简前端路由（页面切换）
 */

/** 切换到指定页面，并同步侧边栏激活态 */
export function switchPage(pageId) {
  document.querySelectorAll('.page').forEach((p) => p.classList.remove('active'));
  document.querySelectorAll('.nav-item[data-page]').forEach((n) => n.classList.remove('active'));

  const pageEl = document.getElementById('page-' + pageId);
  if (pageEl) pageEl.classList.add('active');

  const navEl = document.querySelector(`.nav-item[data-page="${pageId}"]`);
  if (navEl) navEl.classList.add('active');

  window.scrollTo(0, 0);
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
