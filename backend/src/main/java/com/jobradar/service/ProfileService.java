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

    /** 保存/更新当前登录用户的资料（只更新可编辑字段，userId/plan 由后端控制）。 */
    @Transactional
    public Profile save(Profile body) {
        Profile p = get();
        if (body.getName()       != null) p.setName(body.getName());
        if (body.getEmail()      != null) p.setEmail(body.getEmail());
        if (body.getPhone()      != null) p.setPhone(body.getPhone());
        if (body.getGender()     != null) p.setGender(body.getGender());
        if (body.getSchool()     != null) p.setSchool(body.getSchool());
        if (body.getEducation()  != null) p.setEducation(body.getEducation());
        if (body.getMajor()      != null) p.setMajor(body.getMajor());
        if (body.getGradYear()   != null) p.setGradYear(body.getGradYear());
        if (body.getGpa()        != null) p.setGpa(body.getGpa());
        if (body.getIntentCity() != null) p.setIntentCity(body.getIntentCity());
        if (body.getIntentJob()  != null) p.setIntentJob(body.getIntentJob());
        return repo.save(p);
    }
}
