/**
 * main.js — 应用入口
 * ─────────────────────────────────────────────
 * 不登录也能浏览页面内容；未登录时：
 *   · 滚动 / 点「加载更多」翻页 —— 放行；
 *   · 点击页面其它任意内容 —— 拦截并弹出登录窗口。
 */
import { CONFIG } from './config.js';
import { Auth } from './core/auth.js';
import { initRouter } from './core/router.js';
import { initDashboard } from './views/dashboard.js';
import { initApplications } from './views/applications.js';
import { initJobdb } from './views/jobdb.js';
import { initAddjob } from './views/addjob.js';
import { initAimatch } from './views/aimatch.js';
import { initReview } from './views/review.js';
import { initResumeEditor } from './views/resumeeditor.js';
import { initAutofill } from './views/autofill.js';
import { initProfile } from './views/profile.js';
// import { initPricing } from './views/pricing.js'; // 待支付接入后恢复
import { showToast } from './core/toast.js';

/* ── 百度统计 — 仅线上生效 ── */
if (CONFIG.BAIDU_TONGJI_ID && (location.hostname === 'jobradar.xin' || location.hostname.endsWith('.jobradar.xin'))) {
  window._hmt = window._hmt || [];
  (function() {
    var hm = document.createElement('script');
    hm.src = 'https://hm.baidu.com/hm.js?' + CONFIG.BAIDU_TONGJI_ID;
    hm.async = true;
    var s = document.getElementsByTagName('script')[0];
    s.parentNode.insertBefore(hm, s);
  })();
}

/* ── 装配业务模块（登录与否都先把页面渲染出来）── */
initRouter();
initDashboard();
initApplications();
initJobdb();
initAddjob();
initAimatch();
initReview();
initResumeEditor();
initAutofill();
initProfile();
// initPricing(); // 待支付接入后恢复

renderAccount();
wireAuthModal();
guardAnonymousClicks();

/* ── 侧栏底部：登录态切换 ── */
function renderAccount() {
  const box = document.getElementById('nav-account');
  if (!box) return;
  if (Auth.isLoggedIn()) {
    box.innerHTML = `<span class="nav-user-name">${Auth.getUser() || '已登录'}</span>
      <button class="nav-logout" id="nav-logout" title="退出登录"><i class="ti ti-logout"></i>退出</button>`;
    box.querySelector('#nav-logout').addEventListener('click', () => Auth.logout());
  } else {
    box.innerHTML = `<button class="nav-login" data-auth-open><i class="ti ti-login"></i>登录 / 注册</button>`;
  }
}

/* ── 未登录拦截：除翻页外，点击任意内容弹登录 ── */
function guardAnonymousClicks() {
  document.addEventListener('click', (e) => {
    if (Auth.isLoggedIn()) return;
    if (e.target.closest('#auth-modal')) return;           // 弹窗内部正常交互
    if (e.target.closest('.nav')) return;                  // 左侧导航栏：放行（可自由切换页面、登录/退出）
    if (e.target.closest('[data-act="more"]')) return;     // 「加载更多」= 翻页，放行
    // 其余（主内容区）任意点击 → 拦截并弹登录
    e.preventDefault();
    e.stopPropagation();
    openAuth();
  }, true); // 捕获阶段，抢在各业务点击处理前
}

/* ── 登录 / 注册弹窗 ── */
function openAuth()  { document.getElementById('auth-modal').style.display = 'flex'; document.getElementById('auth-account')?.focus(); }
function closeAuth() { document.getElementById('auth-modal').style.display = 'none'; }

function wireAuthModal() {
  const modal = document.getElementById('auth-modal');
  if (!modal) return;

  const title     = document.getElementById('auth-title');
  const sub       = document.getElementById('auth-sub');
  const dnEl      = document.getElementById('auth-displayname');   // 用户名（仅注册）
  const accountEl = document.getElementById('auth-account');       // 账号
  const passEl    = document.getElementById('auth-password');      // 密码
  const pass2El   = document.getElementById('auth-password2');     // 确认密码（仅注册）
  const errEl     = document.getElementById('auth-error');
  const submit    = document.getElementById('auth-submit');
  const switchText = document.getElementById('auth-switch-text');
  const switchBtn  = document.getElementById('auth-switch-btn');

  // 仅在注册模式显示的字段
  const regOnlyFields = modal.querySelectorAll('.auth-reg-only');

  let mode = 'login';
  let busy = false;

  function applyMode() {
    const isLogin = mode === 'login';
    title.textContent = isLogin ? '登录' : '注册';
    sub.textContent = isLogin ? '登录后即可管理你的投递、简历与资料' : '创建账号，开始管理你的校招投递';
    submit.textContent = isLogin ? '登录' : '注册';
    switchText.textContent = isLogin ? '还没有账号？' : '已有账号？';
    switchBtn.textContent = isLogin ? '注册一个' : '去登录';
    errEl.textContent = '';
    // 登录模式隐藏"用户名"和"确认密码"字段
    regOnlyFields.forEach(el => el.style.display = isLogin ? 'none' : 'block');
    // 清空表单
    [dnEl, accountEl, passEl, pass2El].forEach(el => { if (el) el.value = ''; });
  }
  applyMode();

  // data-auth-open（登录按钮）/ data-auth-close（关闭、遮罩）
  document.addEventListener('click', (e) => {
    if (e.target.closest('[data-auth-open]')) { e.preventDefault(); openAuth(); }
    else if (e.target.closest('[data-auth-close]')) { e.preventDefault(); closeAuth(); }
  });
  switchBtn.addEventListener('click', (e) => { e.preventDefault(); mode = mode === 'login' ? 'register' : 'login'; applyMode(); });

  async function submitForm() {
    if (busy) return;
    const a = accountEl.value.trim();
    const p = passEl.value;
    errEl.textContent = '';

    // ── 客户端前置校验 ──
    if (!a) { errEl.textContent = '请输入账号'; accountEl.focus(); return; }
    if (!/^[a-zA-Z0-9]+$/.test(a)) { errEl.textContent = '账号只能包含数字和英文字母'; accountEl.focus(); return; }
    if (!p) { errEl.textContent = '请输入密码'; passEl.focus(); return; }

    if (mode === 'register') {
      const dn = dnEl.value.trim();
      const p2 = pass2El.value;
      if (!dn) { errEl.textContent = '请输入用户名'; dnEl.focus(); return; }
      if (dn.length > 15) { errEl.textContent = '用户名长度不能超过 15 位'; dnEl.focus(); return; }
      if (p.length < 6 || p.length > 64) { errEl.textContent = '密码长度需在 6–64 位之间'; passEl.focus(); return; }
      if (!/^[\x21-\x7E]+$/.test(p)) { errEl.textContent = '密码只能使用数字、英文或符号'; passEl.focus(); return; }
      if (p !== p2) { errEl.textContent = '两次输入的密码不一致'; pass2El.focus(); return; }
    }

    busy = true; submit.disabled = true;
    const prev = submit.textContent; submit.textContent = '请稍候…';
    try {
      if (mode === 'login') {
        await Auth.login(a, p);
      } else {
        await Auth.register(a, dnEl.value.trim(), p);
      }
      showToast('欢迎，' + (Auth.getUser() || a));
      location.reload();
    } catch (err) {
      errEl.textContent = err.message || '操作失败，请重试';
      submit.disabled = false; submit.textContent = prev; busy = false;
    }
  }
  submit.addEventListener('click', submitForm);
  // 所有输入框回车都可提交
  modal.querySelectorAll('input').forEach(el =>
    el.addEventListener('keydown', (e) => { if (e.key === 'Enter') submitForm(); })
  );
}
