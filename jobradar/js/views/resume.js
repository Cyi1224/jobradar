/**
 * views/resume.js — 「简历管理」页面视图
 */
import { ResumeStore } from '../data/resume.js';

export function initResume() {
  const grid = document.getElementById('resume-grid');
  if (!grid) return;

  ResumeStore.getAll().then((list) => {
    const cards = list.map((r) => `
      <div class="resume-card${r.active ? ' active' : ''}">
        <div class="resume-icon ${r.color}"><i class="ti ti-file-cv"></i></div>
        <div class="resume-name">${r.name}</div>
        <div class="resume-meta">更新于 ${r.updatedAt} · ${r.size}</div>
        <div class="tag-row">${r.tags.map((t) => `<span class="badge ${r.color === 'green' ? 'b-green' : 'b-blue'}">${t}</span>`).join('')}</div>
      </div>`).join('');

    const dashed = `
      <div class="resume-card dashed" data-goto="addjob">
        <i class="ti ti-plus" style="font-size:24px;color:var(--c-text-2)"></i>
        <div style="font-size:13px;color:var(--c-text-2)">上传新简历</div>
      </div>`;

    grid.innerHTML = cards + dashed;
  });
}
