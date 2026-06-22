/**
 * views/pricing.js — 「定价 / 付费会员」页面视图
 * ─────────────────────────────────────────────
 * 展示 4 档套餐 + 当前会员状态；点击开通 → Membership.subscribe → 解除校招库 5 页上限。
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
      <div class="pricing-note"><i class="ti ti-info-circle"></i> 当前为演示开通（点击即生效）。正式上线需接入微信 / 支付宝支付，支付成功后再开通会员。</div>
    `;
    el.querySelectorAll('.pricing-buy').forEach((b) => b.addEventListener('click', () => buy(b.dataset.plan)));
  }

  async function buy(plan) {
    if (busy) return;
    busy = true;
    try {
      await Membership.subscribe(plan);
      showToast('开通成功，已解锁全部校招信息');
      setTimeout(() => location.reload(), 700);   // 刷新使校招库解除上限
    } catch (e) {
      showToast(e.message || '开通失败，请重试');
      busy = false;
    }
  }

  render();
}
