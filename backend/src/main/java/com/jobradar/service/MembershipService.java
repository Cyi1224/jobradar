package com.jobradar.service;

import com.jobradar.common.ResourceNotFoundException;
import com.jobradar.dto.MembershipDTO;
import com.jobradar.entity.User;
import com.jobradar.repository.UserRepository;
import com.jobradar.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 付费会员：套餐时长、开通、状态查询、会员判定。
 * 说明：subscribe() 目前为「即时开通」（演示/占位），真实支付（微信/支付宝）
 * 应在此前先创建订单并校验支付回调成功后再调用本方法续期。
 */
@Service
public class MembershipService {

    private final UserRepository userRepo;

    public MembershipService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    /** 套餐 → 天数：月 15 / 季 40 / 半年 70 / 年 100（元）。 */
    private int daysOf(String plan) {
        return switch (plan == null ? "" : plan) {
            case "month"   -> 30;
            case "quarter" -> 90;
            case "half"    -> 180;
            case "year"    -> 365;
            default -> throw new IllegalArgumentException("未知套餐：" + plan);
        };
    }

    /** 当前登录用户是否为有效会员（用于解除校招库 5 页上限）。匿名返回 false。 */
    public boolean isCurrentUserMember() {
        Long uid = UserContext.get();
        if (uid == null) return false;
        return userRepo.findById(uid)
                .map(u -> u.getMemberUntil() != null && u.getMemberUntil().isAfter(LocalDateTime.now()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public MembershipDTO status() {
        return toDTO(currentUser());
    }

    /** 开通/续费：从「现有到期时间与当前时间的较大者」往后叠加套餐时长。 */
    @Transactional
    public MembershipDTO subscribe(String plan) {
        int days = daysOf(plan);
        User u = currentUser();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = (u.getMemberUntil() != null && u.getMemberUntil().isAfter(now)) ? u.getMemberUntil() : now;
        u.setMemberUntil(base.plusDays(days));
        userRepo.save(u);
        return toDTO(u);
    }

    private User currentUser() {
        return userRepo.findById(UserContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
    }

    private MembershipDTO toDTO(User u) {
        LocalDateTime until = u.getMemberUntil();
        boolean member = until != null && until.isAfter(LocalDateTime.now());
        long daysLeft = member ? ChronoUnit.DAYS.between(LocalDateTime.now(), until) : 0;
        return new MembershipDTO(member, until != null ? until.toString() : null, daysLeft, member ? "会员" : "免费版");
    }
}
