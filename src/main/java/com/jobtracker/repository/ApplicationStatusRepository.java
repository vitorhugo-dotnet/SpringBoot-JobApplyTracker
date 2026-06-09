package com.jobtracker.repository;

import com.jobtracker.entity.ApplicationStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationStatusRepository extends JpaRepository<ApplicationStatusEntity, Long> {
    List<ApplicationStatusEntity> findAllByOrderByDisplayOrderAsc();
    boolean existsByName(String name);
}
