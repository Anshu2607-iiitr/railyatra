package com.railyatra.railway.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.railyatra.railway.domain.Coach;
public interface CoachRepository extends JpaRepository<Coach, Long> { }
