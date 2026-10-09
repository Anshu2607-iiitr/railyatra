package com.railyatra.passenger;

import com.railyatra.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "saved_passengers")
public class SavedPassenger {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "full_name", nullable = false) private String fullName;
    @Column(nullable = false) private int age;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Gender gender;
    @Enumerated(EnumType.STRING) @Column(name = "berth_preference", nullable = false) private BerthPreference berthPreference;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected SavedPassenger() { }
    public SavedPassenger(User user, PassengerDetailsRequest request) { this.user = user; update(request); this.createdAt = Instant.now(); }
    public void update(PassengerDetailsRequest request) { this.fullName = request.name().trim(); this.age = request.age(); this.gender = request.gender(); this.berthPreference = request.berthPreference(); this.updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public int getAge() { return age; }
    public Gender getGender() { return gender; }
    public BerthPreference getBerthPreference() { return berthPreference; }
}
