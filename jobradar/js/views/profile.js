/**
 * views/profile.js — 「个人中心」页面视图
 */
import { ProfileStore } from '../data/profile.js';
import { showToast } from '../core/toast.js';

function setText(id, v) { const el = document.getElementById(id); if (el) el.textContent = v ?? ''; }
function setVal(id, v)  { const el = document.getElementById(id); if (el) el.value = v ?? ''; }
function getVal(id)     { return (document.getElementById(id)?.value ?? '').trim(); }

export function initProfile() {
  ProfileStore.get().then((u) => {
    setText('profile-avatar', (u.name || '?')[0]);
    setText('profile-name', u.name || '未设置');
    setText('profile-sub', [u.school, u.major, u.gradYear ? u.gradYear + '届' : ''].filter(Boolean).join(' · '));
    setText('profile-plan', u.plan || '免费版');
    setVal('profile-f-name',       u.name);
    setVal('profile-f-email',      u.email);
    setVal('profile-f-school',     u.school);
    setVal('profile-f-major',      u.major);
    setVal('profile-f-intentjob',  u.intentJob);
    setVal('profile-f-intentcity', u.intentCity);
  }).catch(() => {});

  const saveBtn = document.getElementById('profile-save-btn');
  const saveTip = document.getElementById('profile-save-tip');
  saveBtn?.addEventListener('click', async () => {
    saveBtn.disabled = true;
    saveTip.textContent = '保存中…';
    try {
      const saved = await ProfileStore.save({
        name:       getVal('profile-f-name'),
        email:      getVal('profile-f-email'),
        school:     getVal('profile-f-school'),
        major:      getVal('profile-f-major'),
        intentJob:  getVal('profile-f-intentjob'),
        intentCity: getVal('profile-f-intentcity'),
      });
      setText('profile-avatar', (saved.name || '?')[0]);
      setText('profile-name', saved.name || '未设置');
      setText('profile-sub', [saved.school, saved.major, saved.gradYear ? saved.gradYear + '届' : ''].filter(Boolean).join(' · '));
      saveTip.textContent = '✓ 已保存';
      showToast('个人信息已保存');
      setTimeout(() => { saveTip.textContent = ''; }, 3000);
    } catch (e) {
      saveTip.textContent = '保存失败：' + e.message;
      showToast('保存失败，请重试');
    } finally {
      saveBtn.disabled = false;
    }
  });
}
