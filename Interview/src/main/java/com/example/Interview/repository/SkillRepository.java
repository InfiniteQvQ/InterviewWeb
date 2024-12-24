package com.example.Interview.repository;

import com.example.Interview.entity.Skill;
import com.example.Interview.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkillRepository extends JpaRepository<Skill, Long> {
    // 根据用户ID查询技能列表
    List<Skill> findByUserId(Long userId);
}
