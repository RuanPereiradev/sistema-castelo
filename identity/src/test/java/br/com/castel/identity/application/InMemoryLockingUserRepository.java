package br.com.castel.identity.application;

import br.com.castel.identity.api.UserId;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Persistence port fake that behaves like the database on the two points #61 depends on.
 *
 * <ul>
 *   <li>{@link #findByIdForUpdate} takes a real per-row lock, only inside a transaction, released
 *       when the outermost transaction ends (like {@code SELECT ... FOR UPDATE}).
 *   <li>Every transaction holds one connection of a bounded pool for its whole duration.
 * </ul>
 *
 * <p>{@link #findByUsername} compares the stored (already lowercase) username exactly with the
 * argument, as {@code lower(username) = ?} does with a value normalized in Java.
 */
final class InMemoryLockingUserRepository implements UserRepository {

    private static final long CONNECTION_WAIT_SECONDS = 2;

    private final Map<UserId, User> usersById = new ConcurrentHashMap<>();
    private final Map<UserId, ReentrantLock> rowLocks = new ConcurrentHashMap<>();
    private final Set<UserId> removeBeforeRowLock = ConcurrentHashMap.newKeySet();
    private final Set<UserId> deactivateBeforeRowLock = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<Integer> transactionDepth = ThreadLocal.withInitial(() -> 0);
    private final ThreadLocal<List<ReentrantLock>> rowLocksOfTransaction = ThreadLocal.withInitial(ArrayList::new);
    private final Semaphore connections;
    private final AtomicInteger openTransactions = new AtomicInteger();
    private final AtomicInteger findByUsernameCalls = new AtomicInteger();
    private final AtomicInteger findByIdForUpdateCalls = new AtomicInteger();
    private final AtomicInteger saves = new AtomicInteger();
    private final AtomicInteger savesHoldingRowLock = new AtomicInteger();

    InMemoryLockingUserRepository(int connectionPoolSize) {
        this.connections = new Semaphore(connectionPoolSize);
    }

    void store(User user) {
        usersById.put(user.id(), user);
    }

    /** Simulates the user being deleted after the unlocked read and before the row lock. */
    void removeBeforeNextRowLock(UserId id) {
        removeBeforeRowLock.add(id);
    }

    /** Simulates the user being deactivated after the unlocked read and before the row lock. */
    void deactivateBeforeNextRowLock(UserId id) {
        deactivateBeforeRowLock.add(id);
    }

    TransactionOperations transactions() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                int depth = transactionDepth.get();
                if (depth > 0) {
                    return action.doInTransaction(new SimpleTransactionStatus(false));
                }
                acquireConnection();
                openTransactions.incrementAndGet();
                transactionDepth.set(1);
                try {
                    return action.doInTransaction(new SimpleTransactionStatus(true));
                } finally {
                    rowLocksOfTransaction.get().forEach(ReentrantLock::unlock);
                    rowLocksOfTransaction.get().clear();
                    transactionDepth.set(0);
                    openTransactions.decrementAndGet();
                    connections.release();
                }
            }
        };
    }

    private void acquireConnection() {
        try {
            if (!connections.tryAcquire(CONNECTION_WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("connection pool exhausted");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for a connection", interrupted);
        }
    }

    boolean isCurrentThreadInTransaction() {
        return transactionDepth.get() > 0;
    }

    boolean isCurrentThreadHoldingRowLock() {
        return !rowLocksOfTransaction.get().isEmpty();
    }

    int openTransactions() {
        return openTransactions.get();
    }

    int findByUsernameCalls() {
        return findByUsernameCalls.get();
    }

    int findByIdForUpdateCalls() {
        return findByIdForUpdateCalls.get();
    }

    int saves() {
        return saves.get();
    }

    int savesHoldingRowLock() {
        return savesHoldingRowLock.get();
    }

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.ofNullable(usersById.get(id));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        findByUsernameCalls.incrementAndGet();
        return usersById.values().stream().filter(user -> user.username().equals(username)).findFirst();
    }

    @Override
    public Optional<User> findByIdForUpdate(UserId id) {
        findByIdForUpdateCalls.incrementAndGet();
        if (!isCurrentThreadInTransaction()) {
            throw new IllegalStateException("row lock requested outside a transaction");
        }
        ReentrantLock lock = rowLocks.computeIfAbsent(id, key -> new ReentrantLock());
        lock.lock();
        rowLocksOfTransaction.get().add(lock);
        if (removeBeforeRowLock.remove(id)) {
            usersById.remove(id);
        }
        if (deactivateBeforeRowLock.remove(id)) {
            usersById.get(id).deactivate();
        }
        return Optional.ofNullable(usersById.get(id));
    }

    @Override
    public List<User> findAllOrderedByUsername() {
        return usersById.values().stream().sorted(Comparator.comparing(User::username)).toList();
    }

    @Override
    public User save(User user) {
        saves.incrementAndGet();
        if (isCurrentThreadHoldingRowLock()) {
            savesHoldingRowLock.incrementAndGet();
        }
        usersById.put(user.id(), user);
        return user;
    }
}
