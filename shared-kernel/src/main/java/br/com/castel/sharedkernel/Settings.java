package br.com.castel.sharedkernel;

import java.time.LocalTime;

/**
 * Typed, read-only access to the parameters of operation stored in {@code setting}.
 *
 * <p>The port every module programs against: a module that reads configuration depends on this
 * interface and on nothing else, so where the value lives (a table, a cache, a file) never reaches
 * its domain.
 *
 * <p>Nothing here returns {@code null} and nothing converts. A key that is not configured, and a
 * key stored as another type, are both domain failures with a stable code.
 */
public interface Settings {

    /**
     * @throws SettingNotFoundException if no setting answers {@code key}
     * @throws SettingTypeMismatchException if the setting was not stored as {@code STRING}
     */
    String asText(String key);

    /**
     * @throws SettingNotFoundException if no setting answers {@code key}
     * @throws SettingTypeMismatchException if the setting was not stored as {@code INTEGER}
     * @throws MalformedSettingValueException if the stored text is not an integer
     */
    int asInteger(String key);

    /**
     * @throws SettingNotFoundException if no setting answers {@code key}
     * @throws SettingTypeMismatchException if the setting was not stored as {@code DECIMAL}
     * @throws InvalidMoneyException if the stored text is not a valid monetary amount
     */
    Money asMoney(String key);

    /**
     * A percentage in percent points: {@code "10.00"} is ten percent.
     *
     * @throws SettingNotFoundException if no setting answers {@code key}
     * @throws SettingTypeMismatchException if the setting was not stored as {@code DECIMAL}
     * @throws InvalidPercentageException if the stored text is not a valid percentage
     */
    Percentage asPercentage(String key);

    /**
     * @throws SettingNotFoundException if no setting answers {@code key}
     * @throws SettingTypeMismatchException if the setting was not stored as {@code BOOLEAN}
     * @throws MalformedSettingValueException if the stored text is neither {@code true} nor
     *         {@code false}
     */
    boolean asBoolean(String key);

    /**
     * @throws SettingNotFoundException if no setting answers {@code key}
     * @throws SettingTypeMismatchException if the setting was not stored as {@code TIME}
     * @throws MalformedSettingValueException if the stored text is not an ISO local time
     */
    LocalTime asLocalTime(String key);
}
