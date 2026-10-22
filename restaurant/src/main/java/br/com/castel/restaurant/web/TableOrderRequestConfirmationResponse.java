package br.com.castel.restaurant.web;

/** What a confirmation answers: the request, now {@code CONFIRMED}, and the tab its lines went on. */
public record TableOrderRequestConfirmationResponse(TableOrderRequestResponse request, String tabId) {
}
