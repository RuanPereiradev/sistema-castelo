package br.com.castel.app.config;

import br.com.castel.identity.api.AuthenticatedUser;
import br.com.castel.identity.api.CurrentUserProvider;
import br.com.castel.identity.api.UserId;
import br.com.castel.sharedkernel.AuditorAware;
import java.util.UUID;

/**
 * Tells JPA auditing who is writing: the user authenticated on the current request, read through
 * the {@code identity/api} port, or {@link SystemAuthor#SYSTEM_USER_ID} when there is none.
 *
 * <p>Always returns a value. A {@code null} here would leave {@code created_by} empty, and "nobody
 * knows who did this" is not an answer the audit columns are allowed to give.
 */
public class CurrentUserAuditorAware implements AuditorAware {

    private final CurrentUserProvider currentUserProvider;

    public CurrentUserAuditorAware(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public UUID currentAuditorId() {
        return currentUserProvider
                .currentUser()
                .map(AuthenticatedUser::id)
                .map(UserId::value)
                .orElse(SystemAuthor.SYSTEM_USER_ID);
    }
}
