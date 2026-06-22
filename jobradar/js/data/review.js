/**
 * data/review.js — 「投递复盘」数据层（派生分析）
 * ─────────────────────────────────────────────
 * mock 模式：直接从投递数据（Store）实时派生通过率 / 城市分布 / 建议；
 * http 模式：GET /api/review/summary，由后端 ReviewService 计算。
 */
import { CONFIG } from '../config.js';
import { Store } from './store.js';

const COLORS = ['#1A56DB', '#0E9F6E', '#FF5A1F', '#534AB7', '#5B8AF5'];

/** 是否到达过某阶段（看当前状态或历史时间线） */
function reached(app, stage) {
  return app.status === stage || app.logs.some((l) => l.s === stage);
}

function pct(a, b) {
  return b ? Math.round((a / b) * 1000) / 10 : 0;
}

export function buildSummary(apps) {
  const total     = apps.length;
  const submitted = apps.filter((a) => a.status !== '未投递').length;
  const exam      = apps.filter((a) => reached(a, '已笔试')).length;
  const interview = apps.filter((a) => reached(a, '已面试')).length;
  const oc        = apps.filter((a) => a.status === '已OC').length;

  const rates = [
    { label: '简历通过率', cls: 'blue',   value: pct(exam, submitted),  note: `投递 ${submitted} → 笔试 ${exam}` },
    { label: '笔试通过率', cls: 'green',  value: pct(interview, exam),  note: `笔试 ${exam} → 面试 ${interview}` },
    { label: '面试通过率', cls: 'orange', value: pct(oc, interview),    note: `面试 ${interview} → OC ${oc}` },
    { label: '最终 OC 率', cls: 'green',  value: pct(oc, submitted),    note: '全流程综合转化' },
  ];

  /* 城市分布 */
  const cityMap = {};
  apps.forEach((a) => { const c = a.city || '其它'; cityMap[c] = (cityMap[c] || 0) + 1; });
  const entries = Object.entries(cityMap).sort((x, y) => y[1] - x[1]).slice(0, 5);
  const max = Math.max(1, ...entries.map((e) => e[1]));
  const distribution = entries.map(([label, count], i) => ({
    label, count, width: Math.round((count / max) * 100), color: COLORS[i % COLORS.length],
  }));

  /* 智能建议 */
  const suggestions = [];
  const ocRate = pct(oc, interview);
  if (interview && ocRate < 40) suggestions.push(`面试通过率偏低（${ocRate}%），建议加强系统设计与项目深挖练习。`);
  if (entries.length && total) {
    const [topCity, topCount] = entries[0];
    const share = Math.round((topCount / total) * 100);
    if (share >= 40) suggestions.push(`投递集中在 ${topCity}（${share}%），可适当拓展其它城市分散风险。`);
  }
  if (!submitted) suggestions.push('还没有已投递的记录，先去「添加岗位」开始投递吧。');
  if (!suggestions.length) suggestions.push('整体节奏不错，继续保持，并及时跟进面试反馈。');

  return { stages: { total, submitted, exam, interview, oc }, rates, distribution, suggestions };
}

const reviewMock = {
  async getSummary() { return buildSummary(await Store.getAll()); },
};

const reviewHttp = {
  async getSummary() {
    const res = await fetch(CONFIG.API_BASE + '/review/summary');
    if (!res.ok) throw new Error('请求失败 ' + res.status);
    return res.json();
  },
};

export const ReviewStore = CONFIG.USE_MOCK ? reviewMock : reviewHttp;
