package com.railyatra.passenger;

public record SavedPassengerResponse(Long id, String name, int age, Gender gender, BerthPreference berthPreference) {
    static SavedPassengerResponse from(SavedPassenger passenger) {
        return new SavedPassengerResponse(passenger.getId(), passenger.getFullName(), passenger.getAge(), passenger.getGender(), passenger.getBerthPreference());
    }
}
