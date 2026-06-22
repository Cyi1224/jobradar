/**
 * views/addjob.js — 「添加岗位」写入逻辑
 * ─────────────────────────────────────────────
 * 手动填写表单 → 写入数据层（Store.add）→ 广播事件 → 跳转到「我的投递」。
 * 这是数据层的「写路径」，与读列表共用同一个 Store。
 */
import { Store } from '../data/store.js';
import { emit, EVT } from '../core/bus.js';
import { showToast } from '../core/toast.js';
import { switchPage } from '../core/router.js';

export function initAddjob() {
  const saveBtn = document.getElementById('addjob-save');
  if (!saveBtn) return;

  const val = (id) => (document.getElementById(id)?.value || '').trim();

  saveBtn.addEventListener('click', async () => {
    const co  = val('addjob-co');
    const pos = val('addjob-pos');
    if (!co || !pos) {
      showToast('请先填写公司名称和岗位名称');
      return;
    }

    const entry = {
      co,
      pos,
      type:     val('addjob-type') || '秋招',
      city:     val('addjob-city') || '—',
      deadline: val('addjob-deadline') || '招满为止',
      status:   val('addjob-status') || '未投递',
      note:     val('addjob-note'),
    };

    await Store.add(entry);
    emit(EVT.APPS_CHANGED);          // 通知「我的投递」「总览」刷新
    showToast(`已添加：${co} · ${pos}`);

    /* 清空表单 */
    ['addjob-co', 'addjob-pos', 'addjob-city', 'addjob-deadline', 'addjob-note'].forEach((id) => {
      const el = document.getElementById(id);
      if (el) el.value = '';
    });

    switchPage('applications');
  });
}
