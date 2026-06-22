/**
 * data/meta.js — 状态元数据（前后端共享的领域常量）
 * 纯常量，无副作用，供 store 与 views 复用。
 */

/* ── 状态元数据（图标 / 徽章样式 / 主色）── */
export const STATUS_META = {
  '未投递': { icon: 'ti-clock',   badge: 'b-gray',   color: '#888780' },
  '已投递': { icon: 'ti-send',    badge: 'b-blue',   color: '#1A56DB' },
  '已笔试': { icon: 'ti-pencil',  badge: 'b-amber',  color: '#BA7517' },
  '已面试': { icon: 'ti-users',   badge: 'b-purple', color: '#534AB7' },
  '已OC':   { icon: 'ti-trophy',  badge: 'b-green',  color: '#3B6D11' },
  '已挂':   { icon: 'ti-x',       badge: 'b-red',    color: '#A32D2D' },
  '暂缓':   { icon: 'ti-pause',   badge: 'b-gray',   color: '#5F5E5A' },
};

/* ── 状态选项（用于状态选择 UI）── */
export const STATUS_OPTS = [
  { s: '未投递', icon: 'ti-clock',  desc: '尚未提交'   },
  { s: '已投递', icon: 'ti-send',   desc: '已提交简历'  },
  { s: '已笔试', icon: 'ti-pencil', desc: '完成笔试'   },
  { s: '已面试', icon: 'ti-users',  desc: '进入面试'   },
  { s: '已OC',   icon: 'ti-trophy', desc: '拿到 Offer' },
  { s: '已挂',   icon: 'ti-x',      desc: '未通过'     },
  { s: '暂缓',   icon: 'ti-pause',  desc: '暂不投递'   },
];

/* ── 流程节点（不含 已挂 / 暂缓）── */
export const FLOW = ['未投递', '已投递', '已笔试', '已面试', '已OC'];
