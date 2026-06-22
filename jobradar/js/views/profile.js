/**
 * views/profile.js — 「个人中心」页面视图
 */
import { ProfileStore } from '../data/profile.js';

function setText(id, v) { const el = document.getElementById(id); if (el) el.textContent = v; }
function setVal(id, v)  { const el = document.getElementById(id); if (el) el.value = v; }

export function initProfile() {
  ProfileStore.get().then((u) => {
    setText('profile-avatar', u.name[0]);
    setText('profile-name', u.name);
    setText('profile-sub', `${u.school} · ${u.major} · ${u.gradYear}届`);
    setText('profile-plan', u.plan);
    setVal('profile-f-name', u.name);
    setVal('profile-f-email', u.email);
    setVal('profile-f-school', u.school);
    setVal('profile-f-major', u.major);
  });
}
