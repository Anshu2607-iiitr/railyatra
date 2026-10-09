package com.railyatra.railway.repository;

import com.railyatra.railway.domain.CoachLayoutTemplate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachLayoutTemplateRepository extends JpaRepository<CoachLayoutTemplate, Long> {
    Optional<CoachLayoutTemplate> findByTemplateCodeAndActiveTrue(String templateCode);
}
