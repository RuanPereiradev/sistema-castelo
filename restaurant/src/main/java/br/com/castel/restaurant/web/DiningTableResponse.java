package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.DiningTable;

/** A dining table as the administration screen and the waiter read it. Empty seats and area are null. */
public record DiningTableResponse(String id, String label, Integer seats, String area, boolean isActive) {

    public static DiningTableResponse from(DiningTable diningTable) {
        return new DiningTableResponse(
                diningTable.id().value().toString(),
                diningTable.label(),
                diningTable.seats().orElse(null),
                diningTable.area().orElse(null),
                diningTable.isActive());
    }
}
