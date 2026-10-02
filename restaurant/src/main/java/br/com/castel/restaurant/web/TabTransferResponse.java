package br.com.castel.restaurant.web;

import br.com.castel.restaurant.application.TabTransferView;
import br.com.castel.sharedkernel.Percentage;

/**
 * What a transfer answers: both tabs as they stand after it, so the screen redraws the two without a
 * second request — money left one and arrived on the other.
 */
public record TabTransferResponse(TabResponse source, TabResponse destination, int movedItems) {

    /**
     * @param sourceLabel the label of the source tab's table, null on a self-service card
     * @param destinationLabel the label of the destination tab's table, null on a card
     * @param currentRate the rate of the setting now, read before the write (decision D25)
     */
    public static TabTransferResponse from(
            TabTransferView view, String sourceLabel, String destinationLabel, Percentage currentRate) {
        return new TabTransferResponse(
                TabResponse.from(view.source(), sourceLabel, currentRate),
                TabResponse.from(view.destination(), destinationLabel, currentRate),
                view.movedItems());
    }
}
