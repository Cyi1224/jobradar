package com.jobradar.service;

import com.jobradar.entity.Resume;
import com.jobradar.repository.ResumeRepository;
import com.jobradar.security.UserContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ResumeService {

    private final ResumeRepository repo;

    public ResumeService(ResumeRepository repo) {
        this.repo = repo;
    }

    /** 仅返回当前登录用户的简历。 */
    public List<Resume> list() {
        return repo.findByUserIdOrderByIdAsc(UserContext.require());
    }
}
