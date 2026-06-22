/**
 * core/bus.js — 极简事件总线
 * ─────────────────────────────────────────────
 * 用于模块间解耦通信：某个视图改了数据后 emit 事件，
 * 关心该数据的其它视图监听并刷新，互相不直接依赖。
 */
export const EVT = {
  APPS_CHANGED: 'data:apps-changed',   // 投递数据发生增删改
};

export function emit(name, detail) {
  document.dispatchEvent(new CustomEvent(name, { detail }));
}

export function on(name, fn) {
  document.addEventListener(name, fn);
}
