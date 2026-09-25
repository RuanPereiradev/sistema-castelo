package br.com.castel.restaurant.web;

/**
 * Body of {@code POST /api/restaurant/dining-tables} and of {@code PUT} on one table.
 *
 * <p>No bean validation on purpose: a blank label or seats out of range is a rule of the domain, and
 * answers its own code with 422 instead of a generic 400. On {@code PUT} the body is the whole
 * description (decision #7): an absent {@code seats} or {@code area} clears it.
 */
public class DiningTableRequest {

    private String label;

    private Integer seats;

    private String area;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getSeats() {
        return seats;
    }

    public void setSeats(Integer seats) {
        this.seats = seats;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }
}
