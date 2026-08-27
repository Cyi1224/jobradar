/**
 * core/auth.js — 前端用户会话
 * ─────────────────────────────────────────────
 * 令牌存 localStorage。mock 模式任意账号可登录（本地演示）；
 * http 模式对接后端 /api/auth/login|register，返回 JWT。
 * 其它数据请求在 http.js / catalog.js 里自动带上 Authorization。
 */
import { CONFIG } from '../config.js';

const TOKEN_KEY = 'jr_token';
const USER_KEY  = 'jr_user';       // 存储 displayName 用于 UI 展示
const ACCT_KEY  = 'jr_account';    // 存储 account（登录标识）

function save(token, account, displayName) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(ACCT_KEY, account);
  localStorage.setItem(USER_KEY, displayName || account);
}

async function post(path, body) {
  const res = await fetch(CONFIG.API_BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    // 429 限流：给用户友好提示，避免误解为系统故障
    if (res.status === 429) {
      throw new Error('操作太频繁了，请稍等片刻再试（约1分钟）');
    }
    throw new Error(data.message || data.reason || data.error || `请求失败 ${res.status}`);
  }
  return data;
}

/* mock：任意账号可登录，发一个本地假令牌 */
const authMock = {
  async login(u, p) {
    if (!u || !p) throw new Error('请输入账号和密码');
    return { token: 'mock-' + u, account: u, displayName: u };
  },
  async register(a, dn, p) {
    if (!a || a.length < 3) throw new Error('账号至少 3 位');
    if (!dn || dn.length > 15) throw new Error('用户名最长 15 位');
    if (!p || p.length < 6) throw new Error('密码至少 6 位');
    return { token: 'mock-' + a, account: a, displayName: dn };
  },
};

/* http：对接 Spring Boot */
const authHttp = {
  login(a, p)         { return post('/auth/login',    { account: a, password: p }); },
  register(a, dn, p)  { return post('/auth/register', { account: a, displayName: dn, password: p }); },
};

const adapter = CONFIG.USE_MOCK ? authMock : authHttp;

/* 免费投递次数：未登录 3 次，登录非会员 3 次，会员无限。
   反薅羊毛：按「设备指纹」计数（换账号无效），并以后端 IP 计数为准（换号/清缓存无效）。
   次数 key 不区分登录态，统一按设备指纹计数——登录/退出/换号共享同一设备额度。 */
var _freeApplyKey = 'jr_free_apply';           // 统一（登录/未登录共用）
var _memberFlagKey = 'jr_member_flag';          // 会员缓存 '1'/'0'
var _deviceKey = 'jr_device_id';                // 设备指纹

/* 设备指纹：首次生成并持久化，不随账号变化 */
function deviceId() {
  var id = localStorage.getItem(_deviceKey);
  if (!id) {
    id = 'dev-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
    localStorage.setItem(_deviceKey, id);
  }
  return id;
}

/* 本地计数（按设备，换账号不重置） */
function getCount(key) {
  var d = JSON.parse(localStorage.getItem(key) || '{}');
  var today = new Date().toDateString();
  if (d.date !== today) { d = { date: today, count: 0 }; }
  return d;
}
function saveCount(key, d) {
  localStorage.setItem(key, JSON.stringify(d));
}
function freeLimit() {
  if (!Auth.isLoggedIn()) return 3;             // 未登录
  return Auth.isMember() ? Infinity : 3;        // 会员无限 / 非会员3次
}
/* 次数 key 统一使用设备ID（不区分登录态），换账号/退出登录共享同一设备额度 */
function freeKey() {
  return _freeApplyKey + '_' + deviceId();
}

/* 通用开通会员弹窗：默认投递文案，可传自定义标题/描述 */
function showUpgradeModal(opts) {
  opts = opts || {};
  var existing = document.getElementById('upgrade-modal');
  if (existing) existing.remove();
  var ov = document.createElement('div');
  ov.id = 'upgrade-modal';
  ov.style.cssText = 'position:fixed;inset:0;background:rgba(15,23,41,.55);display:flex;align-items:center;justify-content:center;z-index:999';
  ov.innerHTML = `
    <div style="position:relative;background:#fff;border-radius:16px;padding:32px 28px;max-width:340px;width:90%;box-shadow:0 18px 40px -12px rgba(15,23,41,.3);text-align:center">
      <div style="font-size:40px;margin-bottom:10px">${opts.icon || '💎'}</div>
      <div style="font-size:17px;font-weight:700;margin-bottom:6px">${opts.title || '今日免费投递次数已用完'}</div>
      <div style="font-size:13px;color:var(--c-text-2);margin-bottom:10px;line-height:1.6">${opts.desc || '开通会员即可<b>无限次</b>查看投递入口<br>1 个月仅 ¥9.9，终身买断 ¥99'}</div>
      ${opts.warn ? `<div style="font-size:12px;color:#B45309;background:#FEF3C7;border-radius:8px;padding:8px 12px;margin-bottom:16px;line-height:1.5">${opts.warn}</div>` : ''}
      <button class="btn primary" id="upgrade-go" style="width:100%;padding:11px;border:none;border-radius:8px;background:var(--brand-grad);color:#fff;font-size:14px;font-weight:600;cursor:pointer">${opts.btnText || '立即开通会员'}</button>
      <button id="upgrade-cancel" style="width:100%;padding:9px;margin-top:8px;background:none;border:none;color:var(--c-text-3);font-size:13px;cursor:pointer">暂不开通，稍后再看</button>
    </div>`;
  document.body.appendChild(ov);
  ov.querySelector('#upgrade-go').addEventListener('click', () => {
    ov.remove();
    // 跳转会员开通页面
    var nav = document.querySelector('[data-goto="pricing"]');
    if (nav) nav.click();
    else location.href = '/index.html#pricing';
  });
  ov.querySelector('#upgrade-cancel').addEventListener('click', () => ov.remove());
}

export const Auth = window.Auth = {
  getToken()   { return localStorage.getItem(TOKEN_KEY) || ''; },
  getUser()    { return localStorage.getItem(USER_KEY) || ''; },
  getAccount() { return localStorage.getItem(ACCT_KEY) || ''; },
  isLoggedIn() { return !!localStorage.getItem(TOKEN_KEY); },
  logout()     { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(ACCT_KEY); localStorage.removeItem(USER_KEY); localStorage.removeItem(_memberFlagKey); location.reload(); },
  async login(a, p)          { const r = await adapter.login(a, p);          save(r.token, r.account, r.displayName); return r; },
  async register(a, dn, p)   { const r = await adapter.register(a, dn, p);  save(r.token, r.account, r.displayName); return r; },

  /** 会员缓存：登录后调用 syncMemberStatus() 从后端拉取并缓存 */
  setMemberStatus(member) { localStorage.setItem(_memberFlagKey, member ? '1' : '0'); },
  isMember() { return localStorage.getItem(_memberFlagKey) === '1'; },
  async syncMemberStatus() {
    try {
      const { Membership } = await import('./membership.js');
      const st = await Membership.status();
      Auth.setMemberStatus(!!st.member);
      return !!st.member;
    } catch { Auth.setMemberStatus(false); return false; }
  },

  /** 会员功能门禁：非会员弹开通窗口并返回 false；会员/匿名（仅预览）返回 true */
  requireMember(feature) {
    if (Auth.isMember()) return true;
    var desc, warn;
    if (feature === 'resume') {
      desc = '开通会员即可<b>无限次</b>使用简历编辑器、导出 PDF、AI 简历解析<br>1 个月仅 ¥9.9，终身买断 ¥99';
      warn = '💡 免费用户可预览简历编辑器，保存与导出需开通会员';
    } else if (feature === 'applications') {
      desc = '开通会员即可<b>无限次</b>管理投递进度、批量更新状态、投递复盘分析<br>1 个月仅 ¥9.9，终身买断 ¥99';
      warn = '💡 免费用户可查看投递列表，操作与编辑需开通会员';
    } else if (feature === 'addjob') {
      desc = '开通会员即可<b>无限次</b>手动添加岗位、管理投递记录<br>1 个月仅 ¥9.9，终身买断 ¥99';
      warn = '💡 免费用户可查看添加表单，保存岗位需开通会员';
    } else if (feature === 'jobdb') {
      desc = '开通会员即可<b>无限翻页</b>，解锁全部校招岗位与投递入口<br>1 个月仅 ¥9.9，终身买断 ¥99';
      warn = '💡 免费版仅开放前 5 页，升级后解锁剩余全部岗位，投递入口也不再限次';
    } else {
      desc = '开通会员即可<b>无限次</b>使用该功能<br>1 个月仅 ¥9.9，终身买断 ¥99';
      warn = '';
    }
    showUpgradeModal({ title: '此功能仅会员可用', desc: desc, warn: warn });
    return false;
  },

  /** 投递入口次数控制：会员无限 / 登录非会员5次 / 未登录3次（设备指纹+IP双重限制） */
  tryFreeApply(url) {
    var isGuest = !Auth.isLoggedIn();
    var limit = freeLimit();
    if (limit === Infinity) {                       // 会员：直接打开
      if (url) window.open(url, '_blank', 'noopener');
      return true;
    }
    // 本地设备计数预检
    var key = freeKey();
    var d = getCount(key);
    if (d.count >= limit) {
      Auth.onFreeUsedUp(isGuest);
      return false;
    }
    // 后端 IP 计数校验（防换号/清缓存）——同步计数，用 fetch 同步返回
    var ipOk = Auth.checkIpLimit();
    if (!ipOk) {
      Auth.onFreeUsedUp(isGuest);
      return false;
    }
    d.count++;
    saveCount(key, d);
    var left = limit - d.count;
    var toast = document.getElementById('toast');
    if (toast) {
      toast.textContent = '已使用 ' + d.count + '/' + limit + ' 次免费查看' + (left > 0 ? '（还剩 ' + left + ' 次）' : '');
      toast.className = 'toast show';
      setTimeout(function() { toast.className = 'toast'; }, 2500);
    }
    if (url) window.open(url, '_blank', 'noopener');
    return true;
  },

  /** 次数用尽：未登录弹登录，登录非会员弹开通会员 */
  onFreeUsedUp(isGuest) {
    if (isGuest) {
      var m = document.getElementById('auth-modal');
      if (m) { m.style.display = 'flex'; }
      var err = document.getElementById('auth-error');
      if (err) err.textContent = '今日免费次数已用完（设备/IP 限制），登录后每天可免费查看 3 次';
    } else {
      showUpgradeModal({
        title: '今日免费投递次数已用完',
        desc: '开通会员即可<b>无限次</b>查看投递入口<br>1 个月仅 ¥9.9，终身买断 ¥99',
        warn: '⚠️ 免费次数按<b>设备与 IP</b> 统计，用完即锁定，<b>更换账号或退出登录均无效</b>。请开通会员后畅享无限投递。',
      });
    }
  },

  /** 后端 IP 计数校验：同一 IP 每日最多 limit 次（登录非会员5 / 未登录3） */
  checkIpLimit() {
    try {
      var xhr = new XMLHttpRequest();
      xhr.open('GET', CONFIG.API_BASE + '/api/apply-limit/check', false);  // 同步，等后端判定
      xhr.setRequestHeader('User-Agent', 'Mozilla/5.0');
      xhr.send(null);
      if (xhr.status === 200) {
        var r = JSON.parse(xhr.responseText || '{}');
        if (r.used !== undefined) {
          // 同步后端已用次数到本地，保证两边一致
          var key = freeKey();
          var d = { date: new Date().toDateString(), count: r.used };
          saveCount(key, d);
          return r.allowed === true;
        }
      }
    } catch (e) { /* 后端不可用则退化为本地计数 */ }
    return true;
  },

  /** 使用一次后通知后端计数（消耗一次 IP 额度） */
  async reportIpUse() {
    try {
      const res = await fetch(CONFIG.API_BASE + '/api/apply-limit/use', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      });
      await res.text();
    } catch (e) { /* 静默 */ }
  },

  /** 剩余免费次数（用于按钮提示文案） */
  freeApplyLeft() {
    var limit = freeLimit();
    if (limit === Infinity) return 0;
    var d = getCount(freeKey());
    return Math.max(0, limit - d.count);
  }
};
