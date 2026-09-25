package br.com.castel.restaurant.web;

import br.com.castel.restaurant.application.DiningTableService;
import br.com.castel.restaurant.domain.DiningTableId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dining tables of the property (decision #3): registering, redescribing, activating and
 * deactivating is {@code ADMIN}; reading is also open to {@code WAITER}, who opens tabs on them. The
 * inactive tables are an administration concern, so asking for them is {@code ADMIN} only.
 *
 * <p>The class-level rule covers every route; the reading routes widen it with their own
 * annotation, which takes precedence. Every {@code @PathVariable} names its variable explicitly, as in
 * {@link MenuAdministrationController}.
 */
@RestController
@RequestMapping("/api/restaurant/dining-tables")
@PreAuthorize("hasRole('ADMIN')")
public class DiningTableController {

    private final DiningTableService diningTables;

    public DiningTableController(DiningTableService diningTables) {
        this.diningTables = diningTables;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiningTableResponse createDiningTable(@RequestBody DiningTableRequest request) {
        return DiningTableResponse.from(
                diningTables.create(request.getLabel(), request.getSeats(), request.getArea()));
    }

    /** Only the active tables, unless an {@code ADMIN} asks for all of them (decision #6). */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or (hasRole('WAITER') and !#includeInactive)")
    public List<DiningTableResponse> listDiningTables(
            @P("includeInactive") @RequestParam(name = "includeInactive", defaultValue = "false")
                    boolean includeInactive) {
        return diningTables.list(includeInactive).stream().map(DiningTableResponse::from).toList();
    }

    @GetMapping("/{diningTableId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAITER')")
    public DiningTableResponse getDiningTable(@PathVariable("diningTableId") String diningTableId) {
        return DiningTableResponse.from(diningTables.find(DiningTableId.of(diningTableId)));
    }

    /** Replaces the whole description (decision #7): an absent seats or area clears it. */
    @PutMapping("/{diningTableId}")
    public DiningTableResponse redescribeDiningTable(
            @PathVariable("diningTableId") String diningTableId, @RequestBody DiningTableRequest request) {
        return DiningTableResponse.from(diningTables.redescribe(
                DiningTableId.of(diningTableId), request.getLabel(), request.getSeats(), request.getArea()));
    }

    @PostMapping("/{diningTableId}/deactivate")
    public DiningTableResponse deactivateDiningTable(@PathVariable("diningTableId") String diningTableId) {
        return DiningTableResponse.from(diningTables.deactivate(DiningTableId.of(diningTableId)));
    }

    @PostMapping("/{diningTableId}/activate")
    public DiningTableResponse activateDiningTable(@PathVariable("diningTableId") String diningTableId) {
        return DiningTableResponse.from(diningTables.activate(DiningTableId.of(diningTableId)));
    }
}
