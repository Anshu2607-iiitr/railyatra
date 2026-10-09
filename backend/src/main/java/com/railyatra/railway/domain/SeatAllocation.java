package com.railyatra.railway.domain;

import com.railyatra.passenger.BerthPreference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "seat_allocations")
public class SeatAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "seat_inventory_id") private SeatInventory inventory;
    @ManyToOne(optional = false) @JoinColumn(name = "seat_id") private Seat seat;
    @Column(name = "allocation_reference", nullable = false) private String allocationReference;
    @Column(name = "passenger_index", nullable = false) private int passengerIndex;
    @Enumerated(EnumType.STRING) @Column(name = "requested_berth", nullable = false) private BerthPreference requestedBerth;
    @Column(name = "allocated_at", nullable = false) private Instant allocatedAt;
    @Column(name = "released_at") private Instant releasedAt;
    protected SeatAllocation() { }
    public SeatAllocation(SeatInventory inventory, Seat seat, String allocationReference, int passengerIndex, BerthPreference requestedBerth) {
        this.inventory = inventory; this.seat = seat; this.allocationReference = allocationReference; this.passengerIndex = passengerIndex; this.requestedBerth = requestedBerth; this.allocatedAt = Instant.now();
    }
    public Long getId() { return id; }
    public SeatInventory getInventory() { return inventory; }
    public Seat getSeat() { return seat; }
    public int getPassengerIndex() { return passengerIndex; }
    public BerthPreference getRequestedBerth() { return requestedBerth; }
    public boolean isReleased() { return releasedAt != null; }
    public void release() { this.releasedAt = Instant.now(); }
}
