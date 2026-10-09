package com.railyatra.railway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "coach_layout_templates")
public class CoachLayoutTemplate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "template_code", nullable = false) private String templateCode;
    @Column(name = "display_name", nullable = false) private String displayName;
    @Column(name = "class_code", nullable = false) private String classCode;
    @Column(nullable = false) private boolean active;
    protected CoachLayoutTemplate() { }
    public Long getId() { return id; }
    public String getTemplateCode() { return templateCode; }
    public String getDisplayName() { return displayName; }
    public String getClassCode() { return classCode; }
    public boolean isActive() { return active; }
}
