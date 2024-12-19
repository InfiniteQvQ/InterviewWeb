package com.example.Interview.repository;

import com.example.Interview.entity.Profile;
import com.example.Interview.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
    Profile findByUser(User user);
}
