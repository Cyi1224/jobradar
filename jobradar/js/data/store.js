/**
 * data/store.js — 数据仓库门面（Facade）
 * ─────────────────────────────────────────────
 * 根据 CONFIG.USE_MOCK 决定使用本地假数据还是真实后端。
 * 视图层只 import 这一个 Store，对底层数据来源无感知 —— 这就是解耦点。
 */
import { CONFIG } from '../config.js';
import { mockRepo } from './mock.js';
import { httpRepo } from './http.js';

export const Store = CONFIG.USE_MOCK ? mockRepo : httpRepo;
