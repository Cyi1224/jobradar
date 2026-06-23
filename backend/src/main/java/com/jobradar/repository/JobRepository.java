package com.jobradar.repository;

import com.jobradar.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {

    /** 按业务主键查找，供 upsert 时判断新增还是更新。 */
    @Query("select j from Job j where j.co=:co and j.positions=:pos and j.recruitType=:rt and j.city=:city and j.deadline=:dl")
    Optional<Job> findByBizKey(@Param("co") String co, @Param("pos") String pos,
                                @Param("rt") String rt, @Param("city") String city,
                                @Param("dl") String dl);

    @Query("select count(distinct j.co) from Job j")
    long countDistinctCo();

    /** 有投递链接且未过期的岗位数（deadline 非日期格式视为长期有效）。 */
    @Query("select count(j) from Job j where j.applyUrl is not null and j.applyUrl <> '' " +
           "and (j.deadline not like '20%' or j.deadline >= :today)")
    long countOpen(@Param("today") String today);

    /** 已过截止日期的岗位总数（deadline 为 YYYY-MM-DD 且早于今天）。 */
    @Query("select count(j) from Job j where j.deadline like '20%' and j.deadline < :today")
    long countExpired(@Param("today") String today);

    @Query("select max(j.updatedAt) from Job j")
    String maxUpdatedAt();

    long countByUpdatedAt(String updatedAt);

    @Query("select distinct j.recruitType from Job j where j.recruitType is not null and j.recruitType <> '' order by j.recruitType")
    List<String> distinctRecruitTypes();

    @Query("select distinct j.industry from Job j where j.industry is not null and j.industry <> '' order by j.industry")
    List<String> distinctIndustries();
}
