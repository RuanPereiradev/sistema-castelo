package br.com.castel.restaurant.api;

import java.time.LocalDate;

/**
 * What the floor produced in a period, for whoever reports on the business.
 *
 * <p>The first read port of the restaurant: until now the module only ever called billing, and
 * nobody called it. It answers <b>activity</b>, not money — how many tabs were opened and how many
 * people sat down. The money is billing's, and the two are put together by {@code finance}.
 *
 * <p>Narrow on purpose. A reporting module that could ask the restaurant anything would end up
 * coupled to the shape of a tab; this answers one question with one record.
 */
public interface RestaurantActivity {

    /**
     * The activity between the two days, both included, read by the moment each tab was opened.
     *
     * <p>Cancelled and merged tabs are left out: a tab opened by mistake and a tab absorbed by
     * another never served anybody, and counting them would inflate the movement of customers.
     */
    RestaurantActivityView between(LocalDate from, LocalDate to);
}
