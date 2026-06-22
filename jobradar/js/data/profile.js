/**
 * data/profile.js — 用户资料数据层（个人中心 & 自动填充 共用）
 */
import { CONFIG } from '../config.js';

const USER = {
  name: '张三',
  phone: '138xxxx0000',
  email: 'zs@example.com',
  gender: '男',
  school: '某知名高校',
  education: '本科',
  major: '计算机科学与技术',
  gradYear: '2027',
  gpa: '3.8 / 4.0',
  intentCity: '上海、北京、深圳',
  plan: '免费版',
};

const profileMock = {
  async get() { return { ...USER }; },
};

const profileHttp = {
  async get() {
    const res = await fetch(CONFIG.API_BASE + '/profile');
    if (!res.ok) throw new Error('请求失败 ' + res.status);
    return res.json();
  },
};

export const ProfileStore = CONFIG.USE_MOCK ? profileMock : profileHttp;
