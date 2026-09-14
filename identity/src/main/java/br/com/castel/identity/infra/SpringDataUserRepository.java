package br.com.castel.identity.infra;

import br.com.castel.identity.api.UserId;
import br.com.castel.identity.domain.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code app_user}, used only by {@link JpaUserRepository}.
 *
 * <p>The username lookup compares {@code lower(username) = :normalizedUsername}. The column side
 * matches the {@code uk_app_user_username} functional index, and it finds a row stored with
 * uppercase letters (for instance one backfilled by V2 from an email such as
 * {@code Joao.Silva@...}). The parameter side is already normalized in Java and is deliberately not
 * wrapped in {@code lower()}: see {@link br.com.castel.identity.domain.UserRepository#findByUsername}.
 */
interface SpringDataUserRepository extends JpaRepository<User, UserId> {

    @Query("select u from User u where lower(u.username) = :normalizedUsername")
    Optional<User> findByNormalizedUsername(@Param("normalizedUsername") String normalizedUsername);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UserId id);
}
