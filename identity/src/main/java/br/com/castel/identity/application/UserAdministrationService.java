package br.com.castel.identity.application;

import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only administration of users. Authorization is enforced at the web boundary. */
@Service
public class UserAdministrationService {

    private final UserRepository userRepository;

    public UserAdministrationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Every user, ordered by username. */
    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return userRepository.findAllOrderedByUsername();
    }
}
