package br.com.castel.sharedkernel;

import java.time.LocalTime;
import java.util.Objects;
import java.util.function.Function;

/**
 * Reads settings through a {@link SettingRepository} and hands the typed reading to the
 * {@link Setting} itself.
 *
 * <p>Plain Java, with no framework and no cache of its own: caching is a property of the
 * repository that talks to the database, not of the typed reading, so a wrong value can never be
 * blamed on two layers at once.
 */
public final class SettingsFromRepository implements Settings {

    private final SettingRepository settingRepository;

    public SettingsFromRepository(SettingRepository settingRepository) {
        this.settingRepository = Objects.requireNonNull(settingRepository, "settingRepository");
    }

    @Override
    public String asText(String key) {
        return read(key, Setting::asText);
    }

    @Override
    public int asInteger(String key) {
        return read(key, Setting::asInteger);
    }

    @Override
    public Money asMoney(String key) {
        return read(key, Setting::asMoney);
    }

    @Override
    public Percentage asPercentage(String key) {
        return read(key, Setting::asPercentage);
    }

    @Override
    public boolean asBoolean(String key) {
        return read(key, Setting::asBoolean);
    }

    @Override
    public LocalTime asLocalTime(String key) {
        return read(key, Setting::asLocalTime);
    }

    private <T> T read(String key, Function<Setting, T> reader) {
        Objects.requireNonNull(key, "key");
        Setting setting = settingRepository.findByKey(key).orElseThrow(() -> new SettingNotFoundException(key));
        return reader.apply(setting);
    }
}
