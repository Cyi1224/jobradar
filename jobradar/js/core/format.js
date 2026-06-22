/**
 * core/format.js — 纯展示工具（客户端计算，与后端无关）
 */

/** 把截止日期格式化为「明天截止 / 3 天后 / 已过期」等友好文案 */
export function formatDeadline(dl) {
  if (!dl || dl === '招满为止') return dl;
  const diff = Math.ceil((new Date(dl) - new Date()) / 86400000);
  if (diff < 0)   return dl + '（已过期）';
  if (diff === 0) return '今天截止';
  if (diff === 1) return '明天截止';
  if (diff === 2) return '后天截止';
  if (diff <= 7)  return `${dl}（${diff} 天后）`;
  return dl;
}

/** 截止日期是否紧急（≤3 天） */
export function isUrgent(dl) {
  if (!dl || dl === '招满为止') return false;
  return (new Date(dl) - new Date()) / 86400000 <= 3;
}
