package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.TabOrigin;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/restaurant/tabs}.
 *
 * <p>{@code origin} is the only field checked here: absent it answers 400, and an unknown value fails
 * to parse, which also answers 400. Which of {@code diningTableId} and {@code cardNumber} the origin
 * needs, and the range of the card, are rules of the domain and answer their own codes with 422.
 */
public class OpenTabRequest {

    @NotNull
    private TabOrigin origin;

    private String diningTableId;

    private Integer cardNumber;

    public TabOrigin getOrigin() {
        return origin;
    }

    public void setOrigin(TabOrigin origin) {
        this.origin = origin;
    }

    public String getDiningTableId() {
        return diningTableId;
    }

    public void setDiningTableId(String diningTableId) {
        this.diningTableId = diningTableId;
    }

    public Integer getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(Integer cardNumber) {
        this.cardNumber = cardNumber;
    }
}
