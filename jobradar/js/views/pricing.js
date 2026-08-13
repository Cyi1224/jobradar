/**
 * views/pricing.js — 「定价 / 付费会员」页面视图
 * 点击开通 → 选择支付方式 → 创建 zpay 订单：
 *   PC 端展示二维码扫码；移动端直接跳收银台。
 *   每 3 秒轮询会员状态，支付成功后自动关闭弹窗并解锁。
 */
import { Membership, PLANS } from '../core/membership.js';
import { showToast } from '../core/toast.js';

export function initPricing() {
  const el = document.getElementById('pricing-body');
  if (!el) return;
  let busy = false;

  async function render() {
    let st = { member: false, plan: '免费版', daysLeft: 0 };
    try { st = await Membership.status(); } catch { /* 匿名/未登录：按免费版展示 */ }

    el.innerHTML = `
      <div class="pricing-status ${st.member ? 'is-member' : ''}">
        ${st.member
          ? `<i class="ti ti-crown"></i> 当前：<b>会员</b> · 剩余 ${st.daysLeft} 天 · 已解锁全部校招信息`
          : `<i class="ti ti-user"></i> 当前：<b>免费版</b> · 校招信息库仅可查看前 5 页`}
      </div>
      <div class="pricing-grid">
        ${PLANS.map((p) => `
          <div class="pricing-card ${p.best ? 'best' : ''}">
            ${p.best ? '<div class="pricing-badge">最划算</div>' : ''}
            <div class="pricing-name">${p.name}</div>
            <div class="pricing-price"><span class="pricing-cur">¥</span>${p.price}</div>
            <div class="pricing-per">约 ¥${p.perMonth} / 月</div>
            <ul class="pricing-feats">
              <li><i class="ti ti-check"></i>解锁校招信息库全部岗位</li>
              <li><i class="ti ti-check"></i>无限翻页，不限前 5 页</li>
              <li><i class="ti ti-check"></i>有效期 ${p.days} 天（可叠加续费）</li>
            </ul>
            <button class="btn primary pricing-buy" data-plan="${p.key}">立即开通</button>
          </div>`).join('')}
      </div>
      <div class="pricing-note"><i class="ti ti-info-circle"></i> 支付由 Zpay 安全担保，支持微信 / 支付宝。支付成功后自动开通会员。</div>
    `;
    el.querySelectorAll('.pricing-buy').forEach((b) => b.addEventListener('click', () => choosePay(b.dataset.plan)));
  }

  /* 选择支付方式弹层 */
  async function choosePay(plan) {
    if (busy) return;
    const p = PLANS.find((x) => x.key === plan);
    const overlay = document.createElement('div');
    overlay.className = 'auth-overlay';
    overlay.style.cssText = 'position:fixed;inset:0;background:rgba(15,23,41,.5);display:flex;align-items:center;justify-content:center;z-index:999';
    overlay.innerHTML = `
      <div style="background:#fff;border-radius:16px;padding:28px 32px;max-width:400px;width:90%;box-shadow:0 18px 40px -12px rgba(15,23,41,.3)">
        <div style="font-size:16px;font-weight:700;margin-bottom:4px">${p.name} · ¥${p.price}</div>
        <div style="font-size:13px;color:var(--c-text-2);margin-bottom:20px">选择支付方式</div>
        <div style="display:flex;flex-direction:column;gap:10px">
          <button class="pay-method" data-type="alipay" style="display:flex;align-items:center;gap:10px;padding:14px;border:1px solid var(--c-border);border-radius:10px;background:#fff;cursor:pointer;font-size:14px">
            <span style="width:32px;height:32px;border-radius:8px;background:#1677ff;color:#fff;display:flex;align-items:center;justify-content:center;font-size:16px">支</span>支付宝支付
          </button>
        </div>
        <button class="pay-cancel" style="width:100%;padding:10px;margin-top:14px;background:none;border:none;color:var(--c-text-3);font-size:13px;cursor:pointer">取消</button>
      </div>`;
    document.body.appendChild(overlay);
    overlay.querySelectorAll('.pay-method').forEach((b) =>
      b.addEventListener('click', () => { overlay.remove(); doPay(plan, b.dataset.type); }));
    overlay.querySelector('.pay-cancel').addEventListener('click', () => overlay.remove());
  }

  /* 创建订单 → 扫码/跳转 + 轮询 */
  async function doPay(plan, type) {
    if (busy) return;
    busy = true;
    try {
      const order = await Membership.createOrder(plan, type);
      if (order.code !== 1) throw new Error(order.msg || '下单失败');
      const isMobile = window.innerWidth <= 768;
      if (isMobile && order.payurl) {
        // 移动端：直接跳转收银台
        location.href = order.payurl;
        return;
      }
      // PC 端：展示二维码 + 轮询
      showQrModal(order);
    } catch (e) {
      showToast(e.message || '下单失败，请重试');
    } finally {
      busy = false;
    }
  }

  /* 二维码弹窗 + 轮询 */
  function showQrModal(order) {
    const overlay = document.createElement('div');
    overlay.className = 'auth-overlay';
    overlay.style.cssText = 'position:fixed;inset:0;background:rgba(15,23,41,.55);display:flex;align-items:center;justify-content:center;z-index:999';
    overlay.innerHTML = `
      <div style="position:relative;background:#fff;border-radius:16px;padding:28px 32px;max-width:360px;width:90%;box-shadow:0 18px 40px -12px rgba(15,23,41,.3);text-align:center">
        <button class="qr-x" title="关闭" style="position:absolute;top:10px;right:10px;width:28px;height:28px;border:none;background:transparent;color:var(--c-text-3);font-size:20px;cursor:pointer;border-radius:50%;display:flex;align-items:center;justify-content:center;line-height:1">×</button>
        <div style="font-size:16px;font-weight:700;margin-bottom:4px">扫码支付 ¥${order.money}</div>
        <div style="font-size:12px;color:var(--c-text-3);margin-bottom:16px">请使用支付宝扫码支付</div>
        <div id="qr-wrap" style="width:200px;height:200px;margin:0 auto 16px;border:1px solid var(--c-border);border-radius:10px;display:flex;align-items:center;justify-content:center;background:#fff;overflow:hidden">
          <span style="color:var(--c-text-3);font-size:13px">二维码加载中…</span>
        </div>
        <div style="font-size:13px;color:var(--c-text-2);margin-bottom:16px" id="qr-status">等待支付…</div>
        <button class="qr-close" style="width:100%;padding:10px;background:var(--brand);color:#fff;border:none;border-radius:8px;font-size:14px;cursor:pointer">已完成支付</button>
        <button class="qr-cancel" style="width:100%;padding:8px;margin-top:8px;background:none;border:none;color:var(--c-text-3);font-size:13px;cursor:pointer">暂不开通，关闭窗口</button>
      </div>`;
    document.body.appendChild(overlay);
    // 关闭：右上角 × / 取消按钮 / 点击遮罩
    const closeModal = () => { clearInterval(poll); overlay.remove(); };
    overlay.querySelector('.qr-x').addEventListener('click', (e) => { e.stopPropagation(); closeModal(); });
    overlay.querySelector('.qr-cancel').addEventListener('click', () => closeModal());
    overlay.addEventListener('click', (e) => { if (e.target === overlay) closeModal(); });

    // 用 qrcode 内容或 img 生成二维码
    const wrap = overlay.querySelector('#qr-wrap');
    if (order.qrcode) {
      const img = document.createElement('img');
      img.src = 'https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=' + encodeURIComponent(order.qrcode);
      img.style.width = '100%'; img.style.height = '100%';
      wrap.innerHTML = '';
      wrap.appendChild(img);
    } else if (order.img) {
      wrap.innerHTML = `<img src="${order.img}" style="width:100%;height:100%">`;
    } else {
      wrap.innerHTML = '<span style="color:var(--c-text-3);font-size:12px;padding:10px">请点击下方链接支付<br><a href="' + order.payurl + '" target="_blank" style="color:var(--brand)">打开支付页</a></span>';
    }

    // 轮询订单支付状态（后端主动查 zpay，覆盖本地收不到回调的情况）
    let paid = false;
    const poll = setInterval(async () => {
      try {
        const r = await Membership.orderStatus(order.orderNo);
        if (r.paid) {
          paid = true;
          clearInterval(poll);
          showSuccessAndClose(overlay, poll);
        }
      } catch { /* 网络波动忽略 */ }
    }, 2500);
    // 3 分钟超时停止轮询
    setTimeout(() => clearInterval(poll), 180000);

    // 支付成功：提示 → 关闭弹窗 → 跳首页
    function showSuccessAndClose(ov, timer) {
      clearInterval(timer);
      showToast('🎉 支付成功，会员已开通！');
      setTimeout(() => {
        ov.remove();
        // 跳回首页并刷新
        const router = document.querySelector('[data-goto="jobdb"]');
        if (router) router.click();
        else location.href = '/index.html';
      }, 1200);
    }

    overlay.querySelector('.qr-close').addEventListener('click', async () => {
      try {
        const r = await Membership.orderStatus(order.orderNo);
        if (r.paid) { paid = true; clearInterval(poll); showSuccessAndClose(overlay, poll); }
        else showToast('未检测到支付，请完成付款后重试');
      } catch {
        showToast('查询失败，请点击后重试');
      }
    });
  }

  render();
}
