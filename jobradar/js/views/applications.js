/**
 * views/applications.js — 「我的投递」页面视图
 * ─────────────────────────────────────────────
 * 负责：列表渲染、状态筛选、状态编辑抽屉（流程轴 / 状态选择 / 动态表单 / 时间线）。
 * 只依赖 Store（数据） + meta（常量） + format（展示） + toast（提示），与数据来源解耦。
 */
import { Store } from '../data/store.js';
import { STATUS_META, STATUS_OPTS, FLOW } from '../data/meta.js';
import { formatDeadline, isUrgent } from '../core/format.js';
import { showToast } from '../core/toast.js';
import { on, emit, EVT } from '../core/bus.js';

export function initApplications() {
  let currentFilter = '全部';

  /* 抽屉状态 */
  const modal       = document.getElementById('status-modal');
  const modalBack   = document.getElementById('modal-back');
  const modalClose  = document.getElementById('modal-close');
  const modalCancel = document.getElementById('modal-cancel');
  const modalSave   = document.getElementById('modal-save');

  let editId      = null;
  let editApp     = null;   // 当前编辑的投递（打开时缓存，避免重复查询）
  let draftStatus = null;
  let draftFields = {};

  /* ════════ 计数 ════════ */
  async function updateCounts() {
    const counts = await Store.getCounts();
    Object.entries(counts).forEach(([k, v]) => {
      const el = document.getElementById('cnt-' + k);
      if (el) el.textContent = `(${v})`;
    });
  }

  /* ════════ 列表渲染 ════════ */
  async function renderTable() {
    const all = await Store.getAll();
    const data = currentFilter === '全部'
      ? all
      : all.filter((a) => a.status === currentFilter);

    const tbody = document.getElementById('app-tbody');
    if (!tbody) return;

    if (!data.length) {
      tbody.innerHTML = `<tr><td colspan="8" style="text-align:center;padding:40px;color:var(--c-text-2);font-size:13px">
        <i class="ti ti-inbox" style="font-size:28px;display:block;margin-bottom:8px;opacity:.4"></i>暂无记录
      </td></tr>`;
      return;
    }

    tbody.innerHTML = data.map((a) => {
      const m = STATUS_META[a.status] || STATUS_META['未投递'];
      const urgent = isUrgent(a.deadline);
      const lastLog = a.logs.length ? a.logs[a.logs.length - 1].time.split(' ')[0] : '—';
      return `
        <tr class="data-row" data-id="${a.id}">
          <td><span style="font-weight:500">${a.co}</span></td>
          <td style="color:var(--c-text-2)">${a.pos}</td>
          <td><span class="badge ${a.type.includes('实习') ? 'b-teal' : 'b-gray'}">${a.type}</span></td>
          <td>${a.city}</td>
          <td style="${urgent ? 'color:var(--red);font-weight:500' : ''}">${formatDeadline(a.deadline)}</td>
          <td><span class="badge ${m.badge}"><i class="ti ${m.icon}" style="font-size:11px;margin-right:3px"></i>${a.status}</span></td>
          <td style="color:var(--c-text-2)">${lastLog}</td>
          <td>
            <button class="icon-btn edit-btn" data-id="${a.id}" aria-label="编辑 ${a.co} 状态">
              <i class="ti ti-edit"></i>
            </button>
            <button class="icon-btn del-btn" data-id="${a.id}" style="color:var(--red)" aria-label="删除">
              <i class="ti ti-trash"></i>
            </button>
          </td>
        </tr>`;
    }).join('');

    /* 行点击：整行触发编辑 */
    tbody.querySelectorAll('tr.data-row').forEach((tr) => {
      tr.addEventListener('click', () => openModal(Number(tr.dataset.id)));
    });

    /* 编辑按钮 */
    tbody.querySelectorAll('.edit-btn').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        openModal(Number(btn.dataset.id));
      });
    });

    /* 删除按钮 */
    tbody.querySelectorAll('.del-btn').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        if (!confirm('确定删除这条记录吗？')) return;
        await Store.remove(Number(btn.dataset.id));
        emit(EVT.APPS_CHANGED);
        showToast('已删除');
      });
    });
  }

  /* Tab 筛选 */
  document.querySelectorAll('.tab[data-status]').forEach((tab) => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.tab[data-status]').forEach((t) => t.classList.remove('on'));
      tab.classList.add('on');
      currentFilter = tab.dataset.status;
      renderTable();
    });
  });

  /* ════════ 状态编辑抽屉 ════════ */
  async function openModal(id) {
    const app = await Store.getById(id);
    if (!app) return;

    editId      = id;
    editApp     = app;
    draftStatus = app.status;
    draftFields = { note: app.note || '' };

    /* 基本信息 */
    document.getElementById('modal-avatar').textContent = app.co[0];
    document.getElementById('modal-co').textContent     = app.co;
    document.getElementById('modal-pos').textContent    = `${app.pos} · ${app.type} · ${app.city}`;
    const dlEl = document.getElementById('modal-deadline');
    dlEl.textContent = formatDeadline(app.deadline);
    dlEl.style.color = isUrgent(app.deadline) ? 'var(--red)' : '';

    renderPipeline(app);
    renderStatusGrid();
    renderDetailPanels(app);
    renderTimeline(app);

    modal.classList.remove('hidden');
    modal.scrollTop = 0;
  }

  function closeModal() {
    modal.classList.add('hidden');
    editId      = null;
    editApp     = null;
    draftStatus = null;
    draftFields = {};
  }

  [modalBack, modalClose, modalCancel].forEach((el) => el && el.addEventListener('click', closeModal));
  modal.addEventListener('click', (e) => { if (e.target === modal) closeModal(); });

  /* ── 流程轴 ── */
  function renderPipeline(app) {
    const pipe   = document.getElementById('pipeline');
    const idx    = FLOW.indexOf(app.status);
    const isHung = app.status === '已挂';
    pipe.innerHTML = '';

    FLOW.forEach((s, i) => {
      if (i > 0) {
        const ln = document.createElement('div');
        ln.className = 'pipe-line' + (idx >= i ? ' done' : '');
        pipe.appendChild(ln);
      }

      const step = document.createElement('div');
      let cls = 'pipe-step ';
      if (s === '已OC' && app.status === '已OC') cls += 'oc';
      else if (isHung && i <= idx)               cls += (i === idx ? 'current' : 'done');
      else if (i < idx)                          cls += 'done';
      else if (i === idx)                        cls += 'current';
      step.className = cls.trim();

      const m = STATUS_META[s];
      step.innerHTML = `<div class="pipe-dot"><i class="ti ${m.icon}" style="font-size:13px"></i></div><div class="pipe-label">${s}</div>`;
      pipe.appendChild(step);
    });

    if (isHung) {
      const ln = document.createElement('div');
      ln.className = 'pipe-line';
      pipe.appendChild(ln);

      const step = document.createElement('div');
      step.className = 'pipe-step fail';
      step.innerHTML = `<div class="pipe-dot"><i class="ti ti-x" style="font-size:13px"></i></div><div class="pipe-label">已挂</div>`;
      pipe.appendChild(step);
    }
  }

  /* ── 状态选择格 ── */
  function renderStatusGrid() {
    const grid = document.getElementById('status-grid');
    grid.innerHTML = STATUS_OPTS.map((o) => `
      <div class="status-opt${draftStatus === o.s ? ' selected' : ''}"
           data-s="${o.s}" role="button" aria-pressed="${draftStatus === o.s}" aria-label="${o.s}">
        <span class="s-icon"><i class="ti ${o.icon}" style="font-size:18px;color:${(STATUS_META[o.s] || STATUS_META['未投递']).color}"></i></span>
        <div class="s-name">${o.s}</div>
        <div class="s-desc">${o.desc}</div>
      </div>`).join('');

    grid.querySelectorAll('.status-opt').forEach((el) => {
      el.addEventListener('click', () => {
        draftStatus = el.dataset.s;
        renderStatusGrid();
        renderDetailPanels(editApp);
      });
    });
  }

  /* ── 动态表单面板 ── */
  function renderDetailPanels(app) {
    const el = document.getElementById('detail-panels');
    let html = '';

    if (draftStatus === '已笔试') {
      html += `
      <div class="detail-block">
        <div style="font-size:13px;font-weight:500;margin-bottom:12px">
          <i class="ti ti-pencil" style="color:var(--amber);margin-right:6px"></i>笔试信息
        </div>
        <div class="form-row">
          <div class="field"><label>笔试时间</label><input type="datetime-local" id="f-exam-time" value="${draftFields['exam-time'] || ''}"></div>
          <div class="field"><label>笔试时长</label><input type="text" id="f-exam-duration" placeholder="如：90 分钟" value="${draftFields['exam-duration'] || ''}"></div>
        </div>
        <div class="field">
          <label>笔试平台</label>
          <select id="f-exam-platform">
            <option value="">请选择</option>
            ${['牛客', '赛码网', 'Hackerrank', '自有平台', '其他'].map((p) =>
              `<option ${draftFields['exam-platform'] === p ? 'selected' : ''}>${p}</option>`).join('')}
          </select>
        </div>
        <div class="field"><label>内容 / 备注</label><textarea id="f-exam-note" placeholder="题型、难度、感受等...">${draftFields['exam-note'] || ''}</textarea></div>
      </div>`;
    }

    if (draftStatus === '已面试') {
      const rounds = draftFields['interview-rounds'] || 1;
      html += `
      <div class="detail-block">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px">
          <span style="font-size:13px;font-weight:500"><i class="ti ti-users" style="color:var(--purple);margin-right:6px"></i>面试信息</span>
          <div style="display:flex;align-items:center;gap:8px;font-size:13px;color:var(--c-text-2)">
            当前轮次：
            <button class="icon-btn" id="rounds-dec" style="width:24px;height:24px;border:0.5px solid var(--c-border-2);border-radius:6px;font-size:16px;display:flex;align-items:center;justify-content:center">−</button>
            <span id="rounds-display" style="font-weight:500;color:var(--c-text-1);min-width:16px;text-align:center">${rounds}</span>
            <button class="icon-btn" id="rounds-inc" style="width:24px;height:24px;border:0.5px solid var(--c-border-2);border-radius:6px;font-size:16px;display:flex;align-items:center;justify-content:center">+</button>
            <span>面</span>
          </div>
        </div>
        <div class="form-row">
          <div class="field"><label>面试时间</label><input type="datetime-local" id="f-int-time" value="${draftFields['int-time'] || ''}"></div>
          <div class="field"><label>面试形式</label>
            <select id="f-int-type">
              <option value="">请选择</option>
              ${['视频面试', '现场面试', '电话面试'].map((t) =>
                `<option ${draftFields['int-type'] === t ? 'selected' : ''}>${t}</option>`).join('')}
            </select>
          </div>
        </div>
        <div class="field"><label>面试官 / 部门（选填）</label><input type="text" id="f-int-interviewer" placeholder="如：算法部门 李工" value="${draftFields['int-interviewer'] || ''}"></div>
        <div class="field"><label>面试内容 / 感受</label><textarea id="f-int-note" placeholder="考察点、难题、整体感受...">${draftFields['int-note'] || ''}</textarea></div>
        <div class="field"><label>面试结果</label>
          <select id="f-int-result">
            <option value="">结果待定</option>
            ${['通过，等待下一轮', '通过，等待 OC', '未通过'].map((r) =>
              `<option ${draftFields['int-result'] === r ? 'selected' : ''}>${r}</option>`).join('')}
          </select>
        </div>
      </div>`;
    }

    if (draftStatus === '已OC') {
      html += `
      <div class="detail-block">
        <div style="font-size:13px;font-weight:500;margin-bottom:12px">
          <i class="ti ti-trophy" style="color:var(--green);margin-right:6px"></i>Offer 信息
        </div>
        <div class="form-row">
          <div class="field"><label>月薪（税前）</label><input type="text" id="f-oc-salary" placeholder="如：25k×15" value="${draftFields['oc-salary'] || ''}"></div>
          <div class="field"><label>入职时间</label><input type="date" id="f-oc-start" value="${draftFields['oc-start'] || ''}"></div>
        </div>
        <div class="field"><label>工作城市 / 部门</label><input type="text" id="f-oc-dept" placeholder="如：上海 · 基础架构部" value="${draftFields['oc-dept'] || ''}"></div>
        <div class="field"><label>备注（福利、转正率等）</label><textarea id="f-oc-note" placeholder="如：五险一金 12%，年终 3 个月，转正率 80%...">${draftFields['oc-note'] || ''}</textarea></div>
      </div>`;
    }

    if (draftStatus === '已挂') {
      html += `
      <div class="detail-block danger">
        <div style="font-size:13px;font-weight:500;margin-bottom:12px;color:var(--red)">
          <i class="ti ti-x" style="margin-right:6px"></i>挂简历 / 挂面记录
        </div>
        <div class="field"><label>挂在哪个环节</label>
          <select id="f-fail-stage">
            <option value="">请选择</option>
            ${['简历筛选', '笔试', '一面', '二面', '三面+', 'HR 面', '体检/背调'].map((s) =>
              `<option ${draftFields['fail-stage'] === s ? 'selected' : ''}>${s}</option>`).join('')}
          </select>
        </div>
        <div class="field"><label>原因分析（选填）</label><textarea id="f-fail-reason" placeholder="如：技术栈不匹配、题目没发挥好...">${draftFields['fail-reason'] || ''}</textarea></div>
      </div>`;
    }

    html += `<div class="field" style="margin-top:12px"><label>通用备注</label><input type="text" id="f-note" placeholder="内推人、HR 联系方式、其他信息..." value="${draftFields.note || app?.note || ''}"></div>`;

    el.innerHTML = html;

    /* 轮次按钮绑定 */
    const dec = document.getElementById('rounds-dec');
    const inc = document.getElementById('rounds-inc');
    if (dec) dec.addEventListener('click', () => changeRounds(-1));
    if (inc) inc.addEventListener('click', () => changeRounds(1));
  }

  function changeRounds(delta) {
    const cur = draftFields['interview-rounds'] || 1;
    draftFields['interview-rounds'] = Math.max(1, Math.min(8, cur + delta));
    const el = document.getElementById('rounds-display');
    if (el) el.textContent = draftFields['interview-rounds'];
  }

  /* ── 时间线 ── */
  function renderTimeline(app) {
    const el = document.getElementById('timeline');
    if (!app.logs.length) {
      el.innerHTML = `<div style="font-size:13px;color:var(--c-text-2);padding:8px 0">暂无记录，保存后将自动生成时间线。</div>`;
      return;
    }
    el.innerHTML = app.logs.map((l) => {
      const m = STATUS_META[l.s] || STATUS_META['未投递'];
      const dotCls = l.s === '已挂' ? 'red' : l.s === '已OC' ? 'green' : '';
      return `
        <div class="tl-item">
          <div class="tl-left">
            <div class="tl-dot ${dotCls}"></div>
            <div class="tl-line"></div>
          </div>
          <div>
            <div class="tl-title">
              <span class="badge ${m.badge}" style="margin-right:6px"><i class="ti ${m.icon}" style="font-size:11px;margin-right:3px"></i>${l.s}</span>
            </div>
            <div class="tl-meta">${l.time}${l.note ? ' · ' + l.note : ''}</div>
          </div>
        </div>`;
    }).join('');
  }

  /* ── 收集表单字段 ── */
  function collectFields() {
    const ids = [
      'exam-time', 'exam-duration', 'exam-platform', 'exam-note',
      'int-time', 'int-type', 'int-interviewer', 'int-note', 'int-result',
      'oc-salary', 'oc-start', 'oc-dept', 'oc-note',
      'fail-stage', 'fail-reason', 'note',
    ];
    ids.forEach((id) => {
      const el = document.getElementById('f-' + id);
      if (el) draftFields[id] = el.value;
    });
  }

  /* ── 保存 ── */
  async function saveStatus() {
    collectFields();
    const app = await Store.updateStatus(editId, draftStatus, draftFields);
    if (!app) return;
    emit(EVT.APPS_CHANGED);
    closeModal();
    showToast(`已保存：${app.co} → ${draftStatus}`);
  }

  modalSave && modalSave.addEventListener('click', saveStatus);

  /* 投递数据变化（本页或「添加岗位」触发）时刷新列表与计数 */
  on(EVT.APPS_CHANGED, () => { updateCounts(); renderTable(); });

  /* ════════ 初始化 ════════ */
  updateCounts();
  renderTable();
}
