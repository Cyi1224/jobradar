/**
 * views/review.js — 「投递复盘」页面视图
 * ─────────────────────────────────────────────
 * 渲染通过率统计卡、城市分布柱状图、AI 复盘建议；
 * 投递数据变化时（监听总线）自动重算。
 */
import { ReviewStore } from '../data/review.js';
import { on, EVT } from '../core/bus.js';

export function initReview() {
  async function render() {
    const s = await ReviewStore.getSummary();

    const stats = document.getElementById('review-stats');
    if (stats) {
      stats.innerHTML = s.rates.map((r) => `
        <div class="stat-card">
          <div class="stat-label">${r.label}</div>
          <div class="stat-val ${r.cls}">${r.value}%</div>
          <div class="stat-note">${r.note}</div>
        </div>`).join('');
    }

    const chart = document.getElementById('review-chart');
    if (chart) {
      chart.innerHTML = s.distribution.map((d) => `
        <div class="bar-row">
          <span class="bar-label">${d.label}</span>
          <div class="bar-wrap"><div class="bar-fill" style="width:${d.width}%;background:${d.color}"></div></div>
          <span class="bar-num">${d.count} 个</span>
        </div>`).join('') || `<div style="font-size:13px;color:var(--c-text-2)">暂无投递数据</div>`;
    }

    const sug = document.getElementById('review-suggestions');
    if (sug) {
      sug.innerHTML = s.suggestions.map((t) => `
        <div class="ai-suggest info"><i class="ti ti-bulb" style="color:#1A56DB"></i><div>${t}</div></div>`).join('');
    }
  }

  render();
  on(EVT.APPS_CHANGED, render);
}
