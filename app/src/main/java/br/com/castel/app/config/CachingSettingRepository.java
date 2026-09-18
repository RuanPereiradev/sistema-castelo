package br.com.castel.app.config;

import br.com.castel.sharedkernel.Setting;
import br.com.castel.sharedkernel.SettingRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Optional;

/**
 * Keeps settings in memory so a parameter read on every order does not hit the database every time.
 *
 * <p>Two deliberate choices:
 *
 * <ul>
 *   <li>a miss is not cached. A key configured a moment after it was first asked for has to be
 *       visible; caching the absence would hide it until the process restarted.
 *   <li>a write drops the entry before returning, so no reader can observe a value older than a
 *       confirmed write.
 * </ul>
 */
class CachingSettingRepository implements SettingRepository {

    /** Far above the number of parameters the system has; the bound exists so the cache cannot grow without limit. */
    static final int MAXIMUM_CACHED_SETTINGS = 500;

    private final SettingRepository delegate;
    private final Cache<String, Setting> cache;

    CachingSettingRepository(SettingRepository delegate) {
        this.delegate = delegate;
        this.cache = Caffeine.newBuilder().maximumSize(MAXIMUM_CACHED_SETTINGS).build();
    }

    @Override
    public Optional<Setting> findByKey(String settingKey) {
        Setting cached = cache.getIfPresent(settingKey);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<Setting> found = delegate.findByKey(settingKey);
        found.ifPresent(setting -> cache.put(settingKey, setting));
        return found;
    }

    @Override
    public void save(Setting setting) {
        delegate.save(setting);
        cache.invalidate(setting.settingKey());
    }
}
