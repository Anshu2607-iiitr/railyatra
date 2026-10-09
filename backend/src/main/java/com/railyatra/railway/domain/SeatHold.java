package com.railyatra.railway.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "seat_holds")
public class SeatHold {
    @Id private String id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @ManyToOne(optional = false) @JoinColumn(name = "train_id") private Train train;
    @Column(name = "journey_date", nullable = false) private LocalDate journeyDate;
    @ManyToOne(optional = false) @JoinColumn(name = "train_class_id") private TrainClass trainClass;
    @Column(nullable = false) private String quota;
    @Column(name = "passenger_count", nullable = false) private int passengerCount;
    @ManyToOne(optional = false) @JoinColumn(name = "inventory_id") private SeatInventory inventory;
    @Column(name = "request_key", nullable = false) private String requestKey;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SeatHoldStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "reservation_type", nullable = false) private HoldReservationType reservationType;
    @Version private long version;

    protected SeatHold() { }
    public SeatHold(Long userId, Train train, TrainClass trainClass, LocalDate journeyDate, String quota, int passengerCount, SeatInventory inventory, String requestKey, Instant expiresAt, HoldReservationType reservationType) {
        this.id = UUID.randomUUID().toString(); this.userId = userId; this.train = train; this.trainClass = trainClass; this.journeyDate = journeyDate; this.quota = quota; this.passengerCount = passengerCount; this.inventory = inventory; this.requestKey = requestKey; this.createdAt = Instant.now(); this.expiresAt = expiresAt; this.status = SeatHoldStatus.ACTIVE; this.reservationType = reservationType;
    }
    public String getId() { return id; }
    public Long getUserId() { return userId; }
    public Train getTrain() { return train; }
    public TrainClass getTrainClass() { return trainClass; }
    public LocalDate getJourneyDate() { return journeyDate; }
    public String getQuota() { return quota; }
    public int getPassengerCount() { return passengerCount; }
    public SeatInventory getInventory() { return inventory; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public SeatHoldStatus getStatus() { return status; }
    public HoldReservationType getReservationType() { return reservationType; }
    public boolean isExpired(Instant now) { return status == SeatHoldStatus.ACTIVE && !expiresAt.isAfter(now); }
    public void expire() { this.status = SeatHoldStatus.EXPIRED; }
    public void release() { this.status = SeatHoldStatus.RELEASED; }
    public void consume() { this.status = SeatHoldStatus.CONSUMED; }
}
