package com.jobradar.service;

import com.jobradar.entity.Profile;
import com.jobradar.repository.ProfileRepository;
import com.jobradar.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final ProfileRepository repo;

    public ProfileService(ProfileRepository repo) {
        this.repo = repo;
    }

    /** 返回当前登录用户的资料；首次访问时自动建一份空白资料。 */
    @Transactional
    public Profile get() {
        Long uid = UserContext.require();
        return repo.findByUserId(uid).orElseGet(() -> {
            Profile p = new Profile();
            p.setUserId(uid);
            p.setPlan("免费版");
            return repo.save(p);
        });
    }
}
