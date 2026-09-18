package br.com.castel.sharedkernel;

import java.util.Optional;

/**
 * Persistence port of {@link Setting}, implemented by the composition root over the {@code setting}
 * table.
 *
 * <p>Declared here, next to {@link Settings}, because {@link SettingsFromRepository} is the plain
 * Java implementation of the read port and depends on it.
 */
public interface SettingRepository {

    /** The setting stored under {@code settingKey}, or empty when the key was never configured. */
    Optional<Setting> findByKey(String settingKey);

    /**
     * Stores a new value for a key that already exists, and drops whatever the cache held for it.
     *
     * <p>No caller in the system writes a setting yet (the administration screen is wave 4). It
     * exists because the invariant "the cache never answers a value older than a confirmed write"
     * needs a single write path to hang the invalidation on.
     *
     * @throws SettingNotFoundException if {@code setting}'s key is not configured
     */
    void save(Setting setting);
}
