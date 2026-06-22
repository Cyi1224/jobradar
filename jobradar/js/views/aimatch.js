/**
 * views/aimatch.js — 「AI 匹配」页面视图
 * ─────────────────────────────────────────────
 * 从 RecoStore 读取推荐岗位并渲染匹配卡片；「刷新推荐」重新拉取。
 */
import { RecoStore } from '../data/catalog.js';
import { showToast } from '../core/toast.js';

export function initAimatch() {
  const list  = document.getElementById('aimatch-list');
  const count = document.getElementById('aimatch-count');
  const refreshBtn = document.getElementById('aimatch-refresh');

  function render(recos) {
    if (count) count.textContent = recos.length;
    list.innerHTML = recos.map((r) => `
      <div class="match-card">
        <div class="match-logo">${r.co[0]}</div>
        <div class="match-info">
          <div class="match-co">${r.co}</div>
          <div class="match-pos">${r.pos} · ${r.city} · ${r.target}</div>
          <div class="tag-row">${r.tags.map((t) => `<span class="tag">${t}</span>`).join('')}</div>
        </div>
        <div class="match-right">
          <div class="match-pct">${r.match}%</div>
          <div class="match-pct-label">匹配度</div>
        </div>
      </div>`).join('');
  }

  refreshBtn?.addEventListener('click', async () => {
    render(await RecoStore.refresh());
    showToast('已刷新推荐');
  });

  RecoStore.getAll().then(render);
}
