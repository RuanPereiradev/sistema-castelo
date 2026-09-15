package br.com.castel.identity.infra;

import br.com.castel.identity.api.UserId;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** {@link UserRepository} backed by JPA through {@link SpringDataUserRepository}. */
@Component
public class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository springDataUserRepository;

    JpaUserRepository(SpringDataUserRepository springDataUserRepository) {
        this.springDataUserRepository = springDataUserRepository;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return springDataUserRepository.findById(id);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return springDataUserRepository.findByNormalizedUsername(User.normalizedUsername(username));
    }

    @Override
    public Optional<User> findByIdForUpdate(UserId id) {
        return springDataUserRepository.findByIdForUpdate(id);
    }

    @Override
    public List<User> findAllOrderedByUsername() {
        return springDataUserRepository.findAll(Sort.by("username"));
    }

    @Override
    public User save(User user) {
        return springDataUserRepository.save(user);
    }
}
