/**
 * data/mock.js — 本地内存数据适配器
 * ─────────────────────────────────────────────
 * 实现「投递仓库（Repository）」接口，全部方法返回 Promise，
 * 与 http.js 的真实后端适配器保持完全相同的签名，可无缝替换。
 *
 *   getAll()                        → Promise<Application[]>
 *   getById(id)                     → Promise<Application | null>
 *   add(entry)                      → Promise<Application>
 *   updateStatus(id, status, fields)→ Promise<Application | null>
 *   remove(id)                      → Promise<boolean>
 *   getCounts()                     → Promise<Record<string, number>>
 */

/* ── 当前时间字符串（mock 端模拟后端写入时间戳）── */
function nowStr() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/* ── 种子数据（接入后端后由数据库提供）── */
let apps = [
  {
    id: 1, co: '字节跳动', pos: '后端开发工程师', type: '秋招', city: '上海',
    deadline: '2026-06-20', status: '已面试', note: '内推渠道，HR 叫 Alice',
    logs: [
      { s: '已投递', time: '2026-06-01 10:23', note: '通过内推链接投递' },
      { s: '已笔试', time: '2026-06-08 14:00', note: '编程题 2 道，约 90 分钟，通过' },
      { s: '已面试', time: '2026-06-15 15:30', note: '一面，技术面，聊了系统设计' },
    ],
  },
  {
    id: 2, co: '腾讯', pos: '产品经理', type: '秋招', city: '深圳',
    deadline: '2026-06-21', status: '已笔试', note: '',
    logs: [
      { s: '已投递', time: '2026-06-03 09:15', note: '' },
      { s: '已笔试', time: '2026-06-10 10:00', note: '行测 + 专项，100 分钟' },
    ],
  },
  {
    id: 3, co: '阿里巴巴', pos: '算法工程师', type: '秋招', city: '杭州',
    deadline: '2026-06-22', status: '已投递', note: '',
    logs: [{ s: '已投递', time: '2026-06-05 11:00', note: '' }],
  },
  {
    id: 4, co: '华为', pos: '硬件工程师', type: '秋招提前批', city: '深圳',
    deadline: '2026-06-26', status: '未投递', note: '', logs: [],
  },
  {
    id: 5, co: '米哈游', pos: '游戏后端', type: '秋招提前批', city: '上海',
    deadline: '招满为止', status: '未投递', note: '', logs: [],
  },
  {
    id: 6, co: '网易', pos: '游戏策划', type: '秋招', city: '杭州',
    deadline: '招满为止', status: '已挂', note: '二面挂，感觉是 HC 原因',
    logs: [
      { s: '已投递', time: '2026-05-20 09:00', note: '' },
      { s: '已笔试', time: '2026-05-25 14:00', note: '通过' },
      { s: '已面试', time: '2026-06-01 10:00', note: '一面通过，二面挂' },
      { s: '已挂',   time: '2026-06-08 18:00', note: '收到拒信' },
    ],
  },
  {
    id: 7, co: '百度', pos: '推荐算法', type: '实习', city: '北京',
    deadline: '招满为止', status: '已OC', note: '导师很好，可以转正',
    logs: [
      { s: '已投递', time: '2026-04-10 10:00', note: '' },
      { s: '已笔试', time: '2026-04-15 14:00', note: '算法题，通过' },
      { s: '已面试', time: '2026-04-22 15:00', note: '三面，全程技术' },
      { s: '已OC',   time: '2026-05-06 16:00', note: '口头 OC，薪资 XX' },
    ],
  },
  {
    id: 8, co: '美团', pos: '后端开发', type: '秋招', city: '北京',
    deadline: '招满为止', status: '已OC', note: '',
    logs: [
      { s: '已投递', time: '2026-05-01 10:00', note: '' },
      { s: '已笔试', time: '2026-05-10 14:00', note: '' },
      { s: '已面试', time: '2026-05-18 15:00', note: '两轮技术面 + HR 面' },
      { s: '已OC',   time: '2026-06-01 10:00', note: '正式 OC' },
    ],
  },
];

let nextId = apps.length + 1;

export const mockRepo = {
  async getAll() {
    return apps.map((a) => ({ ...a }));
  },

  async getById(id) {
    const app = apps.find((a) => a.id === id);
    return app ? { ...app } : null;
  },

  async add(entry) {
    const app = { id: nextId++, logs: [], note: '', ...entry };
    if (app.status && app.status !== '未投递') {
      app.logs.push({ s: app.status, time: nowStr(), note: '' });
    }
    apps.push(app);
    return { ...app };
  },

  async updateStatus(id, newStatus, extraFields = {}) {
    const app = apps.find((a) => a.id === id);
    if (!app) return null;
    const prevStatus = app.status;
    app.status = newStatus;
    if (extraFields.note !== undefined) app.note = extraFields.note;
    if (prevStatus !== newStatus) {
      let logNote = '';
      if (extraFields['exam-note'])  logNote = extraFields['exam-note'];
      if (extraFields['int-note'])   logNote = extraFields['int-note'];
      if (extraFields['oc-salary'])  logNote = '薪资：' + extraFields['oc-salary'];
      if (extraFields['fail-stage']) logNote = '挂在' + extraFields['fail-stage'];
      app.logs.push({ s: newStatus, time: nowStr(), note: logNote });
    }
    return { ...app };
  },

  async remove(id) {
    const idx = apps.findIndex((a) => a.id === id);
    if (idx === -1) return false;
    apps.splice(idx, 1);
    return true;
  },

  async getCounts() {
    const out = { '全部': apps.length };
    ['未投递', '已投递', '已笔试', '已面试', '已挂', '已OC'].forEach((s) => {
      out[s] = apps.filter((a) => a.status === s).length;
    });
    return out;
  },
};
