package br.com.castel.restaurant.web;

import br.com.castel.restaurant.application.ModifierService;
import br.com.castel.restaurant.domain.ModifierId;
import br.com.castel.sharedkernel.Money;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Maintaining the catalog of modifiers of the property. Restricted to {@code ADMIN}, like the rest
 * of the menu administration. Offering a modifier on an item is in
 * {@link MenuAdministrationController}, under the item.
 *
 * <p>Every {@code @PathVariable} names its variable explicitly, as in
 * {@link MenuAdministrationController}.
 */
@RestController
@RequestMapping("/api/restaurant/modifiers")
@PreAuthorize("hasRole('ADMIN')")
public class ModifierAdministrationController {

    private final ModifierService modifiers;

    public ModifierAdministrationController(ModifierService modifiers) {
        this.modifiers = modifiers;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ModifierResponse createModifier(@RequestBody ModifierRequest request) {
        return ModifierResponse.from(modifiers.create(request.getName(), moneyOrNull(request.getPrice())));
    }

    @GetMapping
    public List<ModifierResponse> listModifiers() {
        return modifiers.listAll().stream().map(ModifierResponse::from).toList();
    }

    /** Changes the name, the price, or both. An absent field stays as it is. */
    @PatchMapping("/{modifierId}")
    public ModifierResponse changeModifier(
            @PathVariable("modifierId") String modifierId, @RequestBody ModifierRequest request) {
        return ModifierResponse.from(
                modifiers.change(ModifierId.of(modifierId), request.getName(), moneyOrNull(request.getPrice())));
    }

    /** Off every item that offers it, keeping the links for when it comes back. */
    @PostMapping("/{modifierId}/deactivate")
    public ModifierResponse deactivateModifier(@PathVariable("modifierId") String modifierId) {
        return ModifierResponse.from(modifiers.deactivate(ModifierId.of(modifierId)));
    }

    @PostMapping("/{modifierId}/activate")
    public ModifierResponse activateModifier(@PathVariable("modifierId") String modifierId) {
        return ModifierResponse.from(modifiers.activate(ModifierId.of(modifierId)));
    }

    /** A missing price reaches the aggregate as null, which refuses it with its own code. */
    private static Money moneyOrNull(String amount) {
        return amount == null ? null : Money.of(amount);
    }
}
