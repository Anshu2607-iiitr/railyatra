package com.railyatra.railway.repository;

import com.railyatra.railway.domain.CoachLayoutSlot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachLayoutSlotRepository extends JpaRepository<CoachLayoutSlot, Long> {
    List<CoachLayoutSlot> findByTemplateIdOrderByPositionIndexAsc(Long templateId);
}
