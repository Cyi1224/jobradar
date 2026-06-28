/**
 * views/resumeeditor.js — 「简历编辑器」
 * ─────────────────────────────────────────────
 * 可视化在线简历编辑：拖拽排序、行内编辑、6 套模板、主题定制、撤销/重做、
 * 自动保存、导出 PDF（浏览器打印另存）。文档存 localStorage，纯前端、离线可用。
 */
import { showToast } from '../core/toast.js';
import { ResumeDocStore } from '../data/resumedoc.js';

const HISTORY_MAX = 50;

/* 板块类型：experience/projects/education/custom 共用 {org,role,date,bullets} 结构 */
const TYPES = {
  summary:    { label: '个人简介', single: true },
  experience: { label: '实习 / 工作经历', org: '公司 / 机构', role: '职位', detail: '工作内容' },
  projects:   { label: '项目经历',       org: '项目名称',     role: '担任角色', detail: '项目描述' },
  education:  { label: '教育经历',       org: '学校',         role: '学历 / 专业', detail: '在校情况' },
  skills:     { label: '技能',           tags: true },
  custom:     { label: '自定义板块',     org: '标题',         role: '副标题',   detail: '描述' },
};

function defaultDoc() {
  return {
    template: 'latex',
    theme: { accent: '#1A56DB', font: 'sans', fontSize: 'md', nameSize: 'md', secStyle: 'normal', divider: 'thin', spacing: 'normal', margin: 'normal' },
    basics: { name: '张三', title: '后端开发工程师 · 2027届', phone: '138-0000-0000',
              email: 'zhangsan@example.com', location: '上海', link: 'github.com/zhangsan',
              photo: '', showPhoto: true },
    sections: [
      { id: sid(), type: 'summary', title: '个人简介',
        text: '计算机科学与技术专业 2027 届，熟悉 Java / Spring Boot 后端开发与分布式系统，有扎实的算法基础与实习经历，求职后端开发方向。' },
      { id: sid(), type: 'experience', title: '实习经历', items: [
        { org: '某互联网公司', role: '后端开发实习生', date: '2025.07 – 2025.09',
          bullets: ['参与核心交易链路重构，接口 P99 响应时间降低 40%。', '基于 Redis 设计多级缓存，缓存命中率提升至 95%。'] },
      ] },
      { id: sid(), type: 'projects', title: '项目经历', items: [
        { org: '校园二手交易平台', role: '负责人 · 后端', date: '2024.09 – 2025.01',
          bullets: ['Spring Boot + MySQL + Redis 实现商品/订单/IM 模块，支撑日活 1w+。', '设计幂等下单与库存扣减方案，杜绝超卖。'] },
      ] },
      { id: sid(), type: 'education', title: '教育经历', items: [
        { org: '某知名高校', role: '本科 · 计算机科学与技术', date: '2023.09 – 2027.06',
          bullets: ['GPA 3.8/4.0，专业前 10%；主修数据结构、操作系统、计算机网络、数据库。'] },
      ] },
      { id: sid(), type: 'skills', title: '专业技能',
        tags: ['Java', 'Spring Boot', 'MySQL', 'Redis', 'Kafka', '数据结构与算法', 'Linux', 'Git'] },
    ],
  };
}

function sid() { return 's' + Math.random().toString(36).slice(2, 8); }
function esc(s) { return String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])); }
function getPath(o, p) { return p.split('.').reduce((x, k) => (x == null ? x : x[k]), o); }
function setPath(o, p, v) { const ks = p.split('.'); const last = ks.pop(); const t = ks.reduce((x, k) => x[k], o); t[last] = v; }

export function initResumeEditor() {
  const canvas = document.getElementById('re-canvas');
  if (!canvas) return;

  let doc = defaultDoc();
  let history = [];
  let hPtr = 0;
  let dirty = false;
  let saveTimer = null, histTimer = null;

  /* ── 持久化（mock=localStorage / http=后端按用户存）── */
  function snapshot() { return JSON.stringify(doc); }
  async function save() {
    try { await ResumeDocStore.save(doc); dirty = false; setStatus('已保存'); }
    catch { setStatus('未保存'); }
  }
  function setStatus(t) { const el = document.getElementById('re-save-status'); if (el) el.textContent = t; }

  function scheduleSave() {
    dirty = true; setStatus('编辑中…');
    clearTimeout(saveTimer);
    const interval = Number(document.getElementById('re-autosave')?.value) || 1000;
    saveTimer = setTimeout(save, interval);
  }

  /* ── 历史（撤销/重做）── */
  function pushHistory() {
    const snap = snapshot();
    if (snap === history[hPtr]) return;
    history = history.slice(0, hPtr + 1);
    history.push(snap);
    if (history.length > HISTORY_MAX) history.shift();
    hPtr = history.length - 1;
    updateUndoRedo();
  }
  function scheduleHistory() { clearTimeout(histTimer); histTimer = setTimeout(pushHistory, 500); }
  function updateUndoRedo() {
    const u  = document.getElementById('re-undo');
    const u2 = document.getElementById('re-undo-btn');
    const r  = document.getElementById('re-redo');
    if (u)  u.disabled  = hPtr <= 0;
    if (u2) u2.disabled = hPtr <= 0;
    if (r)  r.disabled  = hPtr >= history.length - 1;
  }
  function undo() { if (hPtr > 0) { hPtr--; doc = JSON.parse(history[hPtr]); render(); syncControls(); scheduleSave(); updateUndoRedo(); } }
  function redo() { if (hPtr < history.length - 1) { hPtr++; doc = JSON.parse(history[hPtr]); render(); syncControls(); scheduleSave(); updateUndoRedo(); } }

  /* ── 文本编辑（不重渲染，避免光标跳动）── */
  function onTextInput(el) { setPath(doc, el.dataset.path, el.textContent); scheduleHistory(); scheduleSave(); }
  /* ── 结构变更（重渲染）── */
  function structural(fn) { fn(); pushHistory(); render(); scheduleSave(); }

  /* ── 个人照片：上传 → 等比压缩到 ≤400px 的 JPEG，存进文档 ── */
  const photoInput = document.createElement('input');
  photoInput.type = 'file'; photoInput.accept = 'image/*'; photoInput.style.display = 'none';
  document.body.appendChild(photoInput);
  photoInput.addEventListener('change', () => {
    const f = photoInput.files && photoInput.files[0];
    if (f) processImage(f, (dataUrl) => structural(() => { doc.basics.photo = dataUrl; doc.basics.showPhoto = true; }));
    photoInput.value = '';
  });
  function pickPhoto() { photoInput.click(); }
  function processImage(file, cb) {
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        const max = 400, scale = Math.min(1, max / Math.max(img.width, img.height));
        const w = Math.round(img.width * scale), h = Math.round(img.height * scale);
        const cv = document.createElement('canvas'); cv.width = w; cv.height = h;
        cv.getContext('2d').drawImage(img, 0, 0, w, h);
        try { cb(cv.toDataURL('image/jpeg', 0.85)); } catch { showToast('图片读取失败'); }
      };
      img.onerror = () => showToast('不是有效的图片');
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  }

  /* ── 渲染画布 ── */
  function field(path, val, cls, ph) {
    return `<div class="re-f ${cls || ''}" contenteditable="true" data-path="${path}" data-ph="${esc(ph || '')}">${esc(val)}</div>`;
  }

  function entryHtml(secIdx, it, i) {
    const base = `sections.${secIdx}.items.${i}`;
    return `
      <div class="re-entry" data-sec="${secIdx}" data-item="${i}">
        <span class="re-grip re-grip-item" draggable="true" title="拖拽排序">⋮⋮</span>
        <button class="re-del" data-act="del-item" data-sec="${secIdx}" data-item="${i}" title="删除条目">×</button>
        <div class="re-entry-head">
          ${field(base + '.org', it.org, 're-org', '名称')}
          ${field(base + '.date', it.date, 're-date', '时间')}
        </div>
        ${field(base + '.role', it.role, 're-role', '职位 / 角色')}
        <ul class="re-bullets">
          ${(it.bullets || []).map((b, bi) => `
            <li>
              ${field(base + '.bullets.' + bi, b, 're-bullet', '一条要点…')}
              <button class="re-del re-del-b" data-act="del-bullet" data-sec="${secIdx}" data-item="${i}" data-b="${bi}" title="删除要点">×</button>
            </li>`).join('')}
        </ul>
        <button class="re-add-mini" data-act="add-bullet" data-sec="${secIdx}" data-item="${i}"><i class="ti ti-plus"></i>要点</button>
      </div>`;
  }

  function sectionHtml(sec, idx) {
    let body;
    if (sec.type === 'summary') {
      body = field(`sections.${idx}.text`, sec.text, 're-summary', '一句话介绍自己…');
    } else if (sec.type === 'skills') {
      body = `<div class="re-tags">
          ${(sec.tags || []).map((t, ti) => `<span class="re-tag">${field(`sections.${idx}.tags.${ti}`, t, '', '技能')}<button class="re-del re-del-t" data-act="del-tag" data-sec="${idx}" data-t="${ti}">×</button></span>`).join('')}
          <button class="re-add-mini" data-act="add-tag" data-sec="${idx}"><i class="ti ti-plus"></i>技能</button>
        </div>`;
    } else {
      body = (sec.items || []).map((it, i) => entryHtml(idx, it, i)).join('')
        + `<button class="re-add-entry" data-act="add-entry" data-sec="${idx}"><i class="ti ti-plus"></i>添加条目</button>`;
    }
    return `
      <section class="re-sec" data-idx="${idx}">
        <div class="re-sec-bar">
          <span class="re-grip re-grip-sec" draggable="true" title="拖拽排序板块">⋮⋮</span>
          ${field(`sections.${idx}.title`, sec.title, 're-sec-title', '板块标题')}
          <button class="re-del re-del-sec" data-act="del-sec" data-idx="${idx}" title="删除板块">×</button>
        </div>
        <div class="re-sec-body">${body}</div>
      </section>`;
  }

  function render() {
    const t = doc.theme;
    canvas.className = 're-canvas tpl-' + doc.template;
    canvas.dataset.font     = t.font;
    canvas.dataset.fontsize = t.fontSize  || 'md';
    canvas.dataset.namesize = t.nameSize  || 'md';
    canvas.dataset.secstyle = t.secStyle  || 'normal';
    canvas.dataset.divider  = t.divider   || 'thin';
    canvas.dataset.spacing  = t.spacing;
    canvas.dataset.margin   = t.margin;
    canvas.style.setProperty('--re-accent', t.accent);
    const b = doc.basics;
    const showPhoto = b.showPhoto !== false;
    const photo = b.photo || '';
    const photoBox = showPhoto ? `
      <div class="re-photo ${photo ? 'has' : ''}" data-act="photo-pick" title="点击${photo ? '更换' : '上传'}照片">
        ${photo
          ? `<img src="${photo}" alt="照片"><button class="re-photo-del" data-act="photo-remove" title="移除照片">×</button>`
          : `<i class="ti ti-camera-plus"></i><span>上传照片</span>`}
      </div>` : '';
    canvas.innerHTML = `
      <header class="re-header">
        <div class="re-head-main">
          ${field('basics.name', b.name, 're-name', '姓名')}
          ${field('basics.title', b.title, 're-title', '求职意向 / 头衔')}
          <div class="re-contacts">
            ${field('basics.phone', b.phone, 're-c', '电话')}
            ${field('basics.email', b.email, 're-c', '邮箱')}
            ${field('basics.location', b.location, 're-c', '城市')}
            ${field('basics.link', b.link, 're-c', '链接')}
          </div>
        </div>
        ${photoBox}
      </header>
      <div class="re-sections">${doc.sections.map(sectionHtml).join('')}</div>`;
    bindCanvas();
    updateUndoRedo();
  }

  function bindCanvas() {
    canvas.querySelectorAll('[data-path]').forEach((el) => {
      el.addEventListener('input', () => onTextInput(el));
      el.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !el.classList.contains('re-bullet') && !el.classList.contains('re-summary')) e.preventDefault(); });
    });
    canvas.querySelectorAll('[data-act]').forEach((el) => el.addEventListener('click', (e) => doAct(el.dataset, e)));
    bindDnD();
  }

  /* ── 结构操作 ── */
  function doAct(d, e) {
    if (d.act === 'photo-pick')   { if (e) e.stopPropagation(); pickPhoto(); return; }
    if (d.act === 'photo-remove') { if (e) e.stopPropagation(); structural(() => { doc.basics.photo = ''; }); return; }
    const i = Number(d.sec), j = Number(d.item), bi = Number(d.b), ti = Number(d.t), idx = Number(d.idx);
    structural(() => {
      switch (d.act) {
        case 'add-entry':  (doc.sections[i].items ||= []).push({ org: '', role: '', date: '', bullets: ['', ''] }); break;
        case 'del-item':   doc.sections[i].items.splice(j, 1); break;
        case 'add-bullet': (doc.sections[i].items[j].bullets ||= []).push(''); break;
        case 'del-bullet': doc.sections[i].items[j].bullets.splice(bi, 1); break;
        case 'add-tag':    (doc.sections[idx].tags ||= []).push(''); break;
        case 'del-tag':    doc.sections[idx].tags.splice(ti, 1); break;
        case 'del-sec':    doc.sections.splice(idx, 1); break;
      }
    });
  }

  function addSection(type) {
    structural(() => {
      const base = { id: sid(), type, title: TYPES[type].label };
      if (type === 'summary') base.text = '';
      else if (type === 'skills') base.tags = [''];
      else base.items = [{ org: '', role: '', date: '', bullets: [''] }];
      doc.sections.push(base);
    });
  }

  /* ── 拖拽排序（板块 & 条目）── */
  let drag = null;
  function bindDnD() {
    canvas.querySelectorAll('.re-grip-sec').forEach((g) => {
      g.addEventListener('dragstart', (e) => { drag = { kind: 'sec', idx: Number(g.closest('.re-sec').dataset.idx) }; e.dataTransfer.effectAllowed = 'move'; });
    });
    canvas.querySelectorAll('.re-grip-item').forEach((g) => {
      g.addEventListener('dragstart', (e) => { const en = g.closest('.re-entry'); drag = { kind: 'item', sec: Number(en.dataset.sec), idx: Number(en.dataset.item) }; e.dataTransfer.effectAllowed = 'move'; e.stopPropagation(); });
    });
    canvas.querySelectorAll('.re-sec').forEach((s) => {
      s.addEventListener('dragover', (e) => { if (drag?.kind === 'sec') e.preventDefault(); });
      s.addEventListener('drop', (e) => {
        if (drag?.kind !== 'sec') return;
        e.preventDefault();
        const to = Number(s.dataset.idx);
        if (to !== drag.idx) structural(() => { const [m] = doc.sections.splice(drag.idx, 1); doc.sections.splice(to, 0, m); });
        drag = null;
      });
    });
    canvas.querySelectorAll('.re-entry').forEach((en) => {
      en.addEventListener('dragover', (e) => { if (drag?.kind === 'item' && drag.sec === Number(en.dataset.sec)) e.preventDefault(); });
      en.addEventListener('drop', (e) => {
        if (drag?.kind !== 'item' || drag.sec !== Number(en.dataset.sec)) return;
        e.preventDefault();
        const to = Number(en.dataset.item);
        if (to !== drag.idx) structural(() => { const arr = doc.sections[drag.sec].items; const [m] = arr.splice(drag.idx, 1); arr.splice(to, 0, m); });
        drag = null;
      });
    });
  }

  /* ── 控件（模板 / 主题 / 自动保存 / 添加板块）── */
  function syncControls() {
    document.querySelectorAll('.re-tpl').forEach((b) => b.classList.toggle('active', b.dataset.tpl === doc.template));
    const set = (id, v) => { const el = document.getElementById(id); if (el) el.value = v; };
    set('re-accent',   doc.theme.accent);
    set('re-font',     doc.theme.font);
    set('re-fontsize', doc.theme.fontSize  || 'md');
    set('re-namesize', doc.theme.nameSize  || 'md');
    set('re-secstyle', doc.theme.secStyle  || 'normal');
    set('re-divider',  doc.theme.divider   || 'thin');
    set('re-spacing',  doc.theme.spacing);
    set('re-margin',   doc.theme.margin);
    // 同步色板激活状态
    document.querySelectorAll('.re-swatch').forEach((s) => s.classList.toggle('active', s.dataset.color === doc.theme.accent));
    const cb = document.getElementById('re-showphoto'); if (cb) cb.checked = doc.basics.showPhoto !== false;
  }
  function wireControls() {
    document.querySelectorAll('.re-tpl').forEach((b) =>
      b.addEventListener('click', () => structural(() => { doc.template = b.dataset.tpl; }) || syncControls()));
    const themeBind = (id, key) => document.getElementById(id)?.addEventListener('input', (e) => {
      doc.theme[key] = e.target.value;
      const t = doc.theme;
      canvas.dataset.font     = t.font;
      canvas.dataset.fontsize = t.fontSize  || 'md';
      canvas.dataset.namesize = t.nameSize  || 'md';
      canvas.dataset.secstyle = t.secStyle  || 'normal';
      canvas.dataset.divider  = t.divider   || 'thin';
      canvas.dataset.spacing  = t.spacing;
      canvas.dataset.margin   = t.margin;
      canvas.style.setProperty('--re-accent', t.accent);
      scheduleHistory(); scheduleSave();
    });
    themeBind('re-accent',   'accent');
    themeBind('re-font',     'font');
    themeBind('re-fontsize', 'fontSize');
    themeBind('re-namesize', 'nameSize');
    themeBind('re-secstyle', 'secStyle');
    themeBind('re-divider',  'divider');
    themeBind('re-spacing',  'spacing');
    themeBind('re-margin',   'margin');
    // 色板点击
    document.querySelectorAll('.re-swatch').forEach((s) => s.addEventListener('click', () => {
      doc.theme.accent = s.dataset.color;
      document.getElementById('re-accent').value = s.dataset.color;
      canvas.style.setProperty('--re-accent', s.dataset.color);
      document.querySelectorAll('.re-swatch').forEach((x) => x.classList.toggle('active', x === s));
      scheduleHistory(); scheduleSave();
    }));
    document.getElementById('re-showphoto')?.addEventListener('change', (e) => structural(() => { doc.basics.showPhoto = e.target.checked; }));
    document.querySelectorAll('.re-addsec').forEach((b) => b.addEventListener('click', () => addSection(b.dataset.type)));
    document.getElementById('re-undo')?.addEventListener('click', undo);
    document.getElementById('re-undo-btn')?.addEventListener('click', undo);
    document.getElementById('re-redo')?.addEventListener('click', redo);
    document.getElementById('re-save')?.addEventListener('click', () => { save(); showToast('已保存'); });
    document.getElementById('re-export')?.addEventListener('click', () => { save(); window.print(); });
    document.getElementById('re-reset')?.addEventListener('click', () => {
      if (!confirm('重置为示例简历？当前内容将被覆盖。')) return;
      structural(() => { doc = defaultDoc(); }); syncControls();
    });
    // 键盘撤销/重做
    document.addEventListener('keydown', (e) => {
      if (!document.getElementById('page-resume')?.classList.contains('active')) return;
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z') { e.preventDefault(); e.shiftKey ? redo() : undo(); }
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'y') { e.preventDefault(); redo(); }
    });
  }

  wireControls();
  // 先渲染占位，再从存储加载覆盖（http 模式按用户从后端拉取，多设备同步）
  syncControls();
  render();
  (async () => {
    try { const saved = await ResumeDocStore.load(); if (saved && saved.sections) doc = saved; } catch { /* 匿名/失败：用默认 */ }
    history = [snapshot()]; hPtr = 0;
    syncControls();
    render();
  })();
}
