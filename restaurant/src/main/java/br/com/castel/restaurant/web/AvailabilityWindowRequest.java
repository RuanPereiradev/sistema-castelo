package br.com.castel.restaurant.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/restaurant/menu-items/{menuItemId}/availability-windows}.
 *
 * <p>{@code dayOfWeek} absent means every day. {@code endTime} before {@code startTime} is a window
 * crossing midnight, which is valid: 22:00 to 02:00 serves on both sides of the turn of the day.
 */
public class AvailabilityWindowRequest {

    private String dayOfWeek;

    @NotBlank
    private String startTime;

    @NotBlank
    private String endTime;

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
}
