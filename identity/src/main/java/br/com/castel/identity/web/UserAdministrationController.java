package br.com.castel.identity.web;

import br.com.castel.identity.application.UserAdministrationService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only user administration, restricted to {@code ADMIN}. */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdministrationController {

    private final UserAdministrationService userAdministrationService;

    public UserAdministrationController(UserAdministrationService userAdministrationService) {
        this.userAdministrationService = userAdministrationService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> listUsers() {
        return userAdministrationService.listUsers().stream().map(UserResponse::from).toList();
    }
}
