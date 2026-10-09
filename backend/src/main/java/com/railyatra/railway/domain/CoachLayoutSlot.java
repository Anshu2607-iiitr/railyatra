package com.railyatra.railway.domain;

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

@Entity
@Table(name = "coach_layout_slots")
public class CoachLayoutSlot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "template_id") private CoachLayoutTemplate template;
    @Column(name = "seat_number", nullable = false) private String seatNumber;
    @Enumerated(EnumType.STRING) @Column(name = "seat_type", nullable = false) private SeatType seatType;
    @Column(name = "position_index", nullable = false) private int positionIndex;
    protected CoachLayoutSlot() { }
    public CoachLayoutSlot(CoachLayoutTemplate template, String seatNumber, SeatType seatType, int positionIndex) { this.template = template; this.seatNumber = seatNumber; this.seatType = seatType; this.positionIndex = positionIndex; }
    public String getSeatNumber() { return seatNumber; }
    public SeatType getSeatType() { return seatType; }
    public int getPositionIndex() { return positionIndex; }
}
