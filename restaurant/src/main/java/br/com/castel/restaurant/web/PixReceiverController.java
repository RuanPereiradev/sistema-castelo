package br.com.castel.restaurant.web;

import br.com.castel.restaurant.application.TablePaymentRequestService;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Pix key, receiver name and city the phone of the table pays to (decisions #15 and #23 of task
 * qr-table-payment): {@code ADMIN} only.
 */
@RestController
@RequestMapping("/api/admin/pix-receiver")
@PreAuthorize("hasRole('ADMIN')")
public class PixReceiverController {

    private final TablePaymentRequestService payments;

    public PixReceiverController(TablePaymentRequestService payments) {
        this.payments = payments;
    }

    /** Every field null while nothing is configured. */
    @GetMapping
    public PixReceiverResponse readPixReceiver() {
        return PixReceiverResponse.from(payments.findPixReceiver());
    }

    @PutMapping
    public PixReceiverResponse configurePixReceiver(@RequestBody PixReceiverRequest request) {
        return PixReceiverResponse.from(Optional.of(
                payments.configurePixReceiver(request.getKey(), request.getReceiverName(), request.getCity())));
    }
}
