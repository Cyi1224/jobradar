/**
 * views/autofill.js — 「自动填充」页面视图（与个人中心共用用户数据）
 */
import { ProfileStore } from '../data/profile.js';

function setVal(id, v) { const el = document.getElementById(id); if (el) el.value = v; }

export function initAutofill() {
  ProfileStore.get().then((u) => {
    setVal('autofill-name', u.name);
    setVal('autofill-phone', u.phone);
    setVal('autofill-email', u.email);
    setVal('autofill-gender', u.gender);
    setVal('autofill-school', u.school);
    setVal('autofill-education', u.education);
    setVal('autofill-major', u.major);
    setVal('autofill-gradyear', u.gradYear);
    setVal('autofill-gpa', u.gpa);
    setVal('autofill-city', u.intentCity);
  });
}
