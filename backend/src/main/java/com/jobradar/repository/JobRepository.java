package com.jobradar.repository;

import com.jobradar.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {

    @Query("select count(distinct j.co) from Job j")
    long countDistinctCo();

    @Query("select count(j) from Job j where j.applyUrl is not null and j.applyUrl <> ''")
    long countOpen();

    @Query("select max(j.updatedAt) from Job j")
    String maxUpdatedAt();

    long countByUpdatedAt(String updatedAt);

    @Query("select distinct j.recruitType from Job j where j.recruitType is not null and j.recruitType <> '' order by j.recruitType")
    List<String> distinctRecruitTypes();

    @Query("select distinct j.industry from Job j where j.industry is not null and j.industry <> '' order by j.industry")
    List<String> distinctIndustries();
}
