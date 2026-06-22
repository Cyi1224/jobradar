/**
 * data/resume.js — 简历列表数据层
 */
import { CONFIG } from '../config.js';

const RESUMES = [
  { id: 1, name: '互联网通用版', updatedAt: '2026-06-10', size: '1.2 MB', color: 'blue',  tags: ['后端', '算法'], active: true },
  { id: 2, name: '金融行业版',   updatedAt: '2026-05-28', size: '0.9 MB', color: 'green', tags: ['量化', '风控'], active: false },
];

const resumeMock = {
  async getAll() { return RESUMES.map((r) => ({ ...r })); },
};

const resumeHttp = {
  async getAll() {
    const res = await fetch(CONFIG.API_BASE + '/resumes');
    if (!res.ok) throw new Error('请求失败 ' + res.status);
    return res.json();
  },
};

export const ResumeStore = CONFIG.USE_MOCK ? resumeMock : resumeHttp;
