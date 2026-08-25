/**
 * main.js — 应用入口
 * ─────────────────────────────────────────────
 * 不登录也能浏览页面内容；未登录时：
 *   · 滚动 / 点「加载更多」翻页 —— 放行；
 *   · 点击页面其它任意内容 —— 拦截并弹出登录窗口。
 */
import { CONFIG } from './config.js';
import { Auth } from './core/auth.js';
window.Auth = Auth;  // 暴露给全局，供 inline onclick 使用
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
import { initPricing } from './views/pricing.js';
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
initPricing();

renderAccount();
wireAuthModal();
guardAnonymousClicks();
guardMemberPages();
initResumeShowcase();
// 已登录用户加载时同步会员状态（决定投递次数/会员权益）
if (Auth.isLoggedIn()) { Auth.syncMemberStatus(); }

/* 登录/注册成功后移除页面上的注册引导与 CTA 卡片 */
function removeRegPrompts() {
  ['reg-cta', 'reg-banner', 'resume-banner'].forEach((id) => {
    const el = document.getElementById(id);
    if (el) el.remove();
  });
}

/* 简历模板展示条：关闭后不再显示 */
function initResumeShowcase() {
  const bar = document.getElementById('resume-showcase');
  const closeBtn = document.getElementById('resume-showcase-close');
  if (!bar || !closeBtn) return;
  if (localStorage.getItem('jr_showcase_closed')) { bar.style.display = 'none'; return; }
  closeBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    bar.style.display = 'none';
    localStorage.setItem('jr_showcase_closed', '1');
  });
}

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

/* ── 未登录拦截：校招库可自由浏览，加投递/其他操作需登录 ── */
function guardAnonymousClicks() {
  document.addEventListener('click', (e) => {
    if (Auth.isLoggedIn()) return;
    if (e.target.closest('#auth-modal')) return;           // 弹窗内部正常交互
    if (e.target.closest('.nav')) return;                  // 导航栏：放行
    if (e.target.closest('[data-act="more"]')) return;     // 「加载更多」：放行

    // 校招信息库：浏览、筛选、翻页全部放行
    if (e.target.closest('#page-jobdb')) return;

    // 其余（加投递、改状态等操作）→ 拦截并弹登录
    e.preventDefault();
    e.stopPropagation();
    openAuth();
  }, true);
}

/* ── 会员功能页拦截：已登录非会员点「我的投递」/「添加岗位」页面内任意位置 → 弹开通会员窗 ── */
function guardMemberPages() {
  const MEMBER_PAGES = { 'page-applications': 'applications', 'page-addjob': 'addjob' };
  document.addEventListener('click', (e) => {
    if (!Auth.isLoggedIn() || Auth.isMember()) return;     // 未登录或会员放行
    if (e.target.closest('#upgrade-modal')) return;        // 弹窗内部正常交互
    if (e.target.closest('#auth-modal')) return;           // 登录弹窗
    if (e.target.closest('.nav')) return;                  // 导航栏：放行（可切换页面）
    const pid = Object.keys(MEMBER_PAGES).find((id) => e.target.closest('#' + id));
    if (!pid) return;
    // 免费用户点击会员功能页 → 弹开通会员窗
    e.preventDefault();
    e.stopPropagation();
    Auth.requireMember(MEMBER_PAGES[pid]);
  }, true);
}

/* ── 新用户 3 步引导 ── */
function showOnboarding() {
  const steps = [
    { icon: 'ti-search', title: '浏览校招信息', desc: '在「校招信息库」中按行业、城市、招聘类型筛选心仪岗位，点击「加入我的投递」' },
    { icon: 'ti-send', title: '管理投递进度', desc: '在「我的投递」中跟踪每个岗位的状态：待投递 → 已投递 → 笔试 → 面试 → OC' },
    { icon: 'ti-file-cv', title: '完善简历资料', desc: '在「我的简历」上传简历或使用编辑器，AI 匹配会根据你的专业推荐岗位' },
  ];
  let step = 0;
  const overlay = document.createElement('div');
  overlay.className = 'auth-overlay';
  overlay.style.cssText = 'position:fixed;inset:0;background:rgba(15,23,41,0.5);backdrop-filter:blur(4px);display:flex;align-items:center;justify-content:center;z-index:999';
  overlay.innerHTML = `
    <div style="background:#fff;border-radius:16px;padding:36px 40px;max-width:420px;width:90%;text-align:center;box-shadow:0 18px 40px -12px rgba(15,23,41,0.2)">
      <div style="font-size:48px;margin-bottom:12px" id="ob-icon"><i class="ti ti-search"></i></div>
      <h2 style="font-size:20px;font-weight:700;margin-bottom:6px" id="ob-title">浏览校招信息</h2>
      <p style="font-size:14px;color:#6b7280;margin-bottom:24px;min-height:40px" id="ob-desc">在「校招信息库」中按行业、城市筛选心仪岗位</p>
      <div style="display:flex;gap:8px;justify-content:center;margin-bottom:20px" id="ob-dots">
        <span class="ob-dot active" style="width:8px;height:8px;border-radius:50%;background:var(--brand)"></span>
        <span class="ob-dot" style="width:8px;height:8px;border-radius:50%;background:#E5E7EB"></span>
        <span class="ob-dot" style="width:8px;height:8px;border-radius:50%;background:#E5E7EB"></span>
      </div>
      <button id="ob-next" style="width:100%;padding:10px;background:var(--brand-grad);color:#fff;border:none;border-radius:8px;font-size:14px;font-weight:600;cursor:pointer">下一步</button>
      <button id="ob-skip" style="width:100%;padding:8px;margin-top:8px;background:none;border:none;color:#9CA3AF;font-size:13px;cursor:pointer">跳过，开始使用</button>
    </div>`;
  document.body.appendChild(overlay);

  function update() {
    const s = steps[step];
    document.getElementById('ob-icon').innerHTML = `<i class="ti ${s.icon}" style="font-size:48px;color:var(--brand)"></i>`;
    document.getElementById('ob-title').textContent = s.title;
    document.getElementById('ob-desc').textContent = s.desc;
    document.getElementById('ob-next').textContent = step < 2 ? '下一步' : '开始使用';
    overlay.querySelectorAll('.ob-dot').forEach((d, i) => {
      d.style.background = i <= step ? 'var(--brand)' : '#E5E7EB';
    });
  }

  document.getElementById('ob-next').addEventListener('click', () => {
    if (step < 2) { step++; update(); }
    else { overlay.remove(); }
  });
  document.getElementById('ob-skip').addEventListener('click', () => {
    overlay.remove();
  });
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
      const p2 = pass2El.value;
      if (p.length < 6 || p.length > 64) { errEl.textContent = '密码长度需在 6–64 位之间'; passEl.focus(); return; }
      if (!/^[\x21-\x7E]+$/.test(p)) { errEl.textContent = '密码只能使用数字、英文或符号'; passEl.focus(); return; }
      if (p !== p2) { errEl.textContent = '两次输入的密码不一致'; pass2El.focus(); return; }
    }

    busy = true; submit.disabled = true;
    const prev = submit.textContent; submit.textContent = '请稍候…';
    try {
      if (mode === 'login') {
        await Auth.login(a, p);
        await Auth.syncMemberStatus();   // 缓存会员状态，决定投递次数
        closeAuth();
        renderAccount();
        removeRegPrompts();              // 移除注册引导/CTA
        showToast('欢迎回来，' + (Auth.getUser() || a));
      } else {
        // 注册：账号即昵称
        await Auth.register(a, a, p);
        await Auth.syncMemberStatus();
        closeAuth();
        renderAccount();
        removeRegPrompts();
        showToast('注册成功！欢迎，' + a);
        showOnboarding();
      }
    } catch (err) {
      errEl.textContent = err.message || '操作失败，请重试';
      // 失败后 3 秒冷却，防止连点触发限流
      submit.disabled = true; submit.textContent = '请稍候…';
      busy = true;
      setTimeout(() => { submit.disabled = false; submit.textContent = prev; busy = false; }, 3000);
    }
  }
  submit.addEventListener('click', submitForm);
  // 所有输入框回车都可提交
  modal.querySelectorAll('input').forEach(el =>
    el.addEventListener('keydown', (e) => { if (e.key === 'Enter') submitForm(); })
  );
}
