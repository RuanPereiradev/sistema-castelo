package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.AvailabilityWindow;

/** A window as the administration screen reads it. {@code dayOfWeek} null means every day. */
public record AvailabilityWindowResponse(String dayOfWeek, String startTime, String endTime) {

    public static AvailabilityWindowResponse from(AvailabilityWindow window) {
        return new AvailabilityWindowResponse(
                window.dayOfWeek() == null ? null : window.dayOfWeek().name(),
                window.startTime().toString(),
                window.endTime().toString());
    }
}
