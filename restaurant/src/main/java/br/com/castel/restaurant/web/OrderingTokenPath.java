package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.DiningTableNotFoundException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The QR code of a table as the public routes receive it in the path. A code that is not even a UUID
 * is one more unknown code and answers the same 404 (decision #34 of task qr-table-ordering): a torn
 * or badly printed QR is not a malformed request of the API.
 */
final class OrderingTokenPath {

    /** The canonical form every id of the API is parsed in: 8-4-4-4-12 hex digits, either case. */
    private static final Pattern CANONICAL_UUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private OrderingTokenPath() {
    }

    /** @throws DiningTableNotFoundException if the code is not a canonical UUID */
    static UUID parse(String orderingToken) {
        if (!CANONICAL_UUID.matcher(orderingToken).matches()) {
            throw new DiningTableNotFoundException("No dining table answers this ordering code");
        }
        return UUID.fromString(orderingToken);
    }
}
