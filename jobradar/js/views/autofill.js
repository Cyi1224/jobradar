/**
 * views/autofill.js — 自动填充（简历解析 + 规则识别引擎）
 * 支持 PDF / DOCX / TXT 上传，纯本地正则规则提取，无需 AI 接口。
 */
import { ProfileStore } from '../data/profile.js';
import { showToast } from '../core/toast.js';

/* ────────────────────────────────────────────────
   工具函数
──────────────────────────────────────────────── */
const $ = (id) => document.getElementById(id);
const setVal = (id, v) => { const el = $(id); if (el && v) el.value = v; };
const getVal = (id) => ($(id)?.value ?? '').trim();

/* ────────────────────────────────────────────────
   规则识别引擎
──────────────────────────────────────────────── */
function parseResume(text) {
  const result = {};
  const lines = text.split(/\r?\n/).map(l => l.trim()).filter(Boolean);

  /* ── 手机号 ── */
  const phoneMatch = text.match(/(?:电话|手机|Tel|Phone|联系方式)[：:\s]*([1][3-9]\d{9})|([1][3-9]\d{9})/);
  result.phone = phoneMatch?.[1] || phoneMatch?.[2] || '';

  /* ── 邮箱 ── */
  const emailMatch = text.match(/[\w.+\-]+@[\w\-]+\.[\w.]+/);
  result.email = emailMatch?.[0] || '';

  /* ── 姓名：标签匹配 + 首行规则 ── */
  const nameByLabel = text.match(/(?:姓名|Name)[：:\s]+([^\s\n，,]{2,5})/);
  if (nameByLabel) {
    result.name = nameByLabel[1];
  } else {
    // 首行或首几行中，2-4 个汉字单独成行，且不含特殊字符
    for (const line of lines.slice(0, 6)) {
      if (/^[一-龥]{2,4}$/.test(line)) { result.name = line; break; }
    }
  }

  /* ── 性别 ── */
  if (/性别[：:\s]*男|[^女]男性/.test(text)) result.gender = '男';
  else if (/性别[：:\s]*女|女性/.test(text)) result.gender = '女';

  /* ── 学历 ── */
  const EDU_MAP = [
    [/博士研究生|博士/, '博士'],
    [/硕士研究生|研究生|硕士/, '硕士'],
    [/本科/, '本科'],
    [/大专|专科/, '专科'],
  ];
  for (const [re, val] of EDU_MAP) {
    if (re.test(text)) { result.education = val; break; }
  }

  /* ── 学校 ── */
  const schoolByLabel = text.match(/(?:毕业院校|就读院校|学校|School|University|Institute)[：:\s]+([^\n，,]{4,20})/);
  if (schoolByLabel) {
    result.school = schoolByLabel[1].trim();
  } else {
    // 包含"大学|学院|学校"的行
    for (const line of lines) {
      if (/(?:大学|学院|学校|University|College|Institute)/.test(line) && line.length < 25) {
        result.school = line.replace(/[（(].*[）)]/g, '').trim();
        break;
      }
    }
  }

  /* ── 专业 ── */
  const majorByLabel = text.match(/(?:专业|Major|所学专业)[：:\s]+([^\n，,（(]{2,20})/);
  if (majorByLabel) result.major = majorByLabel[1].trim();

  /* ── 毕业年份 ── */
  const gradMatch = text.match(/(?:毕业时间|毕业年份|预计毕业|(\d{4})\s*年?[届毕])/);
  if (gradMatch) {
    result.gradYear = gradMatch[1] || gradMatch[0].match(/\d{4}/)?.[0] || '';
  }
  if (!result.gradYear) {
    // 找 20XX 届
    const m = text.match(/20(2[3-9]|[3-9]\d)\s*届/);
    if (m) result.gradYear = m[0].match(/\d{4}/)[0];
  }

  /* ── GPA ── */
  const gpaMatch = text.match(/(?:GPA|绩点|成绩)[：:\s]*(\d+\.?\d*\s*[\/\s]\s*\d+\.?\d*|\d+\.?\d*)/i);
  if (gpaMatch) result.gpa = gpaMatch[1].replace(/\s+/g, '');

  /* ── 意向城市 ── */
  const cityByLabel = text.match(/(?:意向城市|求职城市|期望城市|工作地点)[：:\s]+([^\n]{2,30})/);
  if (cityByLabel) result.intentCity = cityByLabel[1].trim();

  /* ── 意向岗位 ── */
  const jobByLabel = text.match(/(?:意向岗位|求职意向|期望岗位|应聘岗位)[：:\s]+([^\n]{2,50})/);
  if (jobByLabel) result.intentJob = jobByLabel[1].trim();

  /* ── 技能：找技能/技术栈章节 ── */
  const skillsSection = text.match(/(?:专业技能|技术栈|技能|Skills?)[：:\s\n]+([\s\S]{10,300})(?=\n\n|\n[^\n]{1,5}\n|$)/i);
  if (skillsSection) {
    const raw = skillsSection[1];
    // 提取单词/技术名（英文 + 中文技术词）
    const tokens = raw.match(/[A-Za-z][A-Za-z0-9+#.\-]{1,20}|[一-龥]{2,8}/g) || [];
    // 过滤噪声词
    const noise = new Set(['能够','掌握','熟悉','了解','良好','具有','优先','经验','项目','开发','设计','能力','使用']);
    const skills = [...new Set(tokens.filter(t => !noise.has(t)))].slice(0, 15);
    if (skills.length) result.skills = skills.join(',');
  }

  return result;
}

/* ────────────────────────────────────────────────
   CDN 库预加载（页面初始化时触发，后台静默）
──────────────────────────────────────────────── */
let pdfJsReady    = null;
let mammothReady  = null;

function preloadScript(url) {
  return new Promise((res, rej) => {
    const s = document.createElement('script');
    s.src = url; s.onload = res; s.onerror = rej;
    document.head.appendChild(s);
  });
}

function preloadLibs() {
  // 两个库并行加载，不阻塞主流程
  pdfJsReady = window.pdfjsLib
    ? Promise.resolve()
    : preloadScript('https://cdn.jsdelivr.net/npm/pdfjs-dist@3.11.174/build/pdf.min.js')
        .then(() => {
          window.pdfjsLib.GlobalWorkerOptions.workerSrc =
            'https://cdn.jsdelivr.net/npm/pdfjs-dist@3.11.174/build/pdf.worker.min.js';
        });

  mammothReady = window.mammoth
    ? Promise.resolve()
    : preloadScript('https://cdn.jsdelivr.net/npm/mammoth@1.8.0/mammoth.browser.min.js');
}

/* ────────────────────────────────────────────────
   PDF 文本提取（并行处理各页，只取前3页）
──────────────────────────────────────────────── */
async function extractPdf(file) {
  await pdfJsReady;   // 库已在页面初始化时预加载，此处几乎为0等待
  const arrayBuffer = await file.arrayBuffer();
  const pdf = await window.pdfjsLib.getDocument({ data: arrayBuffer }).promise;
  const pageCount = Math.min(pdf.numPages, 3);   // 简历最多2页，取前3保险

  // 并行获取所有页的文本内容
  const pages = await Promise.all(
    Array.from({ length: pageCount }, (_, i) => pdf.getPage(i + 1))
  );
  const contents = await Promise.all(pages.map(p => p.getTextContent()));
  return contents.map(c => c.items.map(it => it.str).join(' ')).join('\n');
}

/* ────────────────────────────────────────────────
   DOCX 文本提取
──────────────────────────────────────────────── */
async function extractDocx(file) {
  await mammothReady;
  const arrayBuffer = await file.arrayBuffer();
  const result = await window.mammoth.extractRawText({ arrayBuffer });
  return result.value;
}

/* ────────────────────────────────────────────────
   提取文本入口
──────────────────────────────────────────────── */
async function extractText(file) {
  const ext = file.name.split('.').pop().toLowerCase();
  if (ext === 'pdf')  return extractPdf(file);
  if (ext === 'docx' || ext === 'doc') return extractDocx(file);
  if (ext === 'txt')  return file.text();
  throw new Error('不支持的文件格式，请上传 PDF / DOCX / TXT');
}

/* ────────────────────────────────────────────────
   UI 辅助
──────────────────────────────────────────────── */
function showStatus(text) {
  const el = $('af-status');
  if (!el) return;
  el.style.display = 'flex';
  $('af-status-text').textContent = text;
}
function hideStatus() {
  const el = $('af-status');
  if (el) el.style.display = 'none';
}

function highlightField(fieldId) {
  const el = $(fieldId);
  if (!el) return;
  el.classList.add('af-highlight');
  setTimeout(() => el.classList.remove('af-highlight'), 2500);
}

function applyResult(result) {
  const fieldMap = {
    name: 'autofill-name', phone: 'autofill-phone', email: 'autofill-email',
    gender: 'autofill-gender', school: 'autofill-school', education: 'autofill-education',
    major: 'autofill-major', gradYear: 'autofill-gradyear', gpa: 'autofill-gpa',
    intentCity: 'autofill-city', intentJob: 'autofill-intentjob', skills: 'autofill-skills',
  };
  let count = 0;
  for (const [key, id] of Object.entries(fieldMap)) {
    if (result[key]) { setVal(id, result[key]); highlightField(id); count++; }
  }

  // 显示结果 banner
  const banner = $('af-result-banner');
  if (banner) {
    banner.style.display = 'flex';
    $('af-result-desc').textContent = `成功识别 ${count} 个字段，请核对并修改有误的信息后保存。`;
  }
  return count;
}

function showRawText(text) {
  const card = $('af-raw-card');
  const pre  = $('af-raw-text');
  if (!card || !pre) return;
  pre.textContent = text.slice(0, 2000) + (text.length > 2000 ? '\n…（已截断）' : '');
  card.style.display = '';
}

/* ────────────────────────────────────────────────
   主流程
──────────────────────────────────────────────── */
async function handleFile(file) {
  if (!file) return;
  if (file.size > 10 * 1024 * 1024) { showToast('文件不能超过 10MB'); return; }

  showStatus(`正在解析 ${file.name}…`);
  $('af-result-banner') && ($('af-result-banner').style.display = 'none');

  try {
    const text = await extractText(file);
    if (!text.trim()) { showToast('未能提取到文本，请尝试粘贴文本方式'); hideStatus(); return; }

    showStatus('正在识别字段…');
    const result = parseResume(text);
    const count = applyResult(result);
    showRawText(text);
    showToast(count > 0 ? `识别完成，共提取 ${count} 个字段` : '未识别到结构化信息，请手动填写');
  } catch (e) {
    showToast('解析失败：' + e.message);
  } finally {
    hideStatus();
  }
}

/* ────────────────────────────────────────────────
   保存到后端
──────────────────────────────────────────────── */
async function saveToProfile() {
  const btn = $('af-save-btn');
  if (btn) btn.disabled = true;
  try {
    await ProfileStore.save({
      name:       getVal('autofill-name'),
      phone:      getVal('autofill-phone'),
      email:      getVal('autofill-email'),
      gender:     getVal('autofill-gender'),
      school:     getVal('autofill-school'),
      education:  getVal('autofill-education'),
      major:      getVal('autofill-major'),
      gradYear:   getVal('autofill-gradyear'),
      gpa:        getVal('autofill-gpa'),
      intentCity: getVal('autofill-city'),
      intentJob:  getVal('autofill-intentjob'),
    });
    showToast('信息已保存到个人中心');
  } catch (e) {
    showToast('保存失败：' + e.message);
  } finally {
    if (btn) btn.disabled = false;
  }
}

/* ────────────────────────────────────────────────
   初始化
──────────────────────────────────────────────── */
export function initAutofill() {
  /* 页面初始化时立即预加载 CDN 库（后台并行，不阻塞） */
  preloadLibs();

  /* 从后端加载已有信息 */
  ProfileStore.get().then((u) => {
    setVal('autofill-name',      u.name);
    setVal('autofill-phone',     u.phone);
    setVal('autofill-email',     u.email);
    setVal('autofill-gender',    u.gender);
    setVal('autofill-school',    u.school);
    setVal('autofill-education', u.education);
    setVal('autofill-major',     u.major);
    setVal('autofill-gradyear',  u.gradYear);
    setVal('autofill-gpa',       u.gpa);
    setVal('autofill-city',      u.intentCity);
    setVal('autofill-intentjob', u.intentJob);
  }).catch(() => {});

  /* 文件选择 */
  $('af-file-input')?.addEventListener('change', (e) => {
    const file = e.target.files?.[0];
    if (file) handleFile(file);
    e.target.value = '';
  });

  /* 拖拽上传 */
  const dropArea = $('af-drop-area');
  if (dropArea) {
    dropArea.addEventListener('dragover', (e) => { e.preventDefault(); dropArea.classList.add('af-drag-over'); });
    dropArea.addEventListener('dragleave', () => dropArea.classList.remove('af-drag-over'));
    dropArea.addEventListener('drop', (e) => {
      e.preventDefault();
      dropArea.classList.remove('af-drag-over');
      const file = e.dataTransfer.files?.[0];
      if (file) handleFile(file);
    });
  }

  /* 粘贴文本切换 */
  $('af-paste-btn')?.addEventListener('click', () => {
    $('af-drop-area').style.display = 'none';
    $('af-paste-area').style.display = '';
    $('af-paste-text')?.focus();
  });
  $('af-cancel-paste-btn')?.addEventListener('click', () => {
    $('af-drop-area').style.display = '';
    $('af-paste-area').style.display = 'none';
  });
  $('af-parse-paste-btn')?.addEventListener('click', () => {
    const text = $('af-paste-text')?.value?.trim();
    if (!text) { showToast('请先粘贴简历文本'); return; }
    showStatus('正在识别字段…');
    $('af-result-banner') && ($('af-result-banner').style.display = 'none');
    setTimeout(() => {
      try {
        const result = parseResume(text);
        const count = applyResult(result);
        showRawText(text);
        showToast(count > 0 ? `识别完成，共提取 ${count} 个字段` : '未识别到结构化信息，请手动填写');
      } catch (e) { showToast('识别出错：' + e.message); }
      finally { hideStatus(); }
    }, 100);
    $('af-drop-area').style.display = '';
    $('af-paste-area').style.display = 'none';
  });

  /* 原始文本折叠 */
  $('af-raw-toggle')?.addEventListener('click', () => {
    const pre = $('af-raw-text');
    const expanded = pre.style.display !== 'none';
    pre.style.display = expanded ? 'none' : '';
    const span = $('af-raw-toggle').querySelector('span');
    if (span) span.innerHTML = expanded ? '展开 <i class="ti ti-chevron-down"></i>' : '收起 <i class="ti ti-chevron-up"></i>';
  });

  /* 保存 */
  $('af-save-btn')?.addEventListener('click', saveToProfile);

  /* 清空 */
  $('af-clear-btn')?.addEventListener('click', () => {
    ['autofill-name','autofill-phone','autofill-email','autofill-school',
     'autofill-major','autofill-gpa','autofill-city','autofill-intentjob','autofill-skills']
      .forEach(id => { const el = $(id); if (el) el.value = ''; });
    ['autofill-gender','autofill-education','autofill-gradyear']
      .forEach(id => { const el = $(id); if (el) el.selectedIndex = 0; });
    $('af-result-banner') && ($('af-result-banner').style.display = 'none');
    $('af-raw-card')     && ($('af-raw-card').style.display = 'none');
    showToast('已清空');
  });
}
