package br.com.castel.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import br.com.castel.app.support.AbstractIntegrationTest;
import br.com.castel.sharedkernel.DomainException;
import br.com.castel.sharedkernel.Money;
import br.com.castel.sharedkernel.Percentage;
import br.com.castel.sharedkernel.Setting;
import br.com.castel.sharedkernel.SettingRepository;
import br.com.castel.sharedkernel.Settings;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Typed reading of {@code setting} as specified in docs/task-0.5b-cross-cutting-foundation.md,
 * items 2, 5 and 6.8: the requested type must match the stored {@code value_type}, a missing key is
 * a domain error with a code, and nothing is ever converted silently.
 *
 * <p>Every test writes its own key with a random suffix, so no test depends on seeded data or on the
 * order of execution.
 */
@SpringBootTest
class SettingsIntegrationTest extends AbstractIntegrationTest {

    private static final String UPPER_SNAKE_CASE = "^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$";

    @Autowired
    private Settings settings;

    @Autowired
    private SettingRepository settingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String givenSetting(String valueType, String value) {
        String key = "test." + valueType.toLowerCase() + "." + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update(
                "insert into setting (id, property_id, setting_key, setting_value, value_type) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                PROPERTY_ID,
                key,
                value,
                valueType);
        return key;
    }

    private String absentKey() {
        return "test.absent." + UUID.randomUUID().toString().substring(0, 8);
    }

    // ------------------------------------------------------------- typed reading, happy path

    @Test
    void shouldReadStoredTextAsText() {
        String key = givenSetting("STRING", "America/Fortaleza");

        assertThat(settings.asText(key)).isEqualTo("America/Fortaleza");
    }

    @Test
    void shouldReadStoredIntegerAsInteger() {
        String key = givenSetting("INTEGER", "12");

        assertThat(settings.asInteger(key)).isEqualTo(12);
    }

    @Test
    void shouldReadStoredZeroAsInteger() {
        String key = givenSetting("INTEGER", "0");

        assertThat(settings.asInteger(key)).isZero();
    }

    @Test
    void shouldReadStoredNegativeIntegerAsInteger() {
        String key = givenSetting("INTEGER", "-3");

        assertThat(settings.asInteger(key)).isEqualTo(-3);
    }

    @Test
    void shouldReadStoredDecimalAsMoney() {
        String key = givenSetting("DECIMAL", "180.00");

        assertThat(settings.asMoney(key)).isEqualTo(Money.of("180.00"));
    }

    @Test
    void shouldReadStoredDecimalWithoutFractionAsMoneyWithTwoDecimals() {
        String key = givenSetting("DECIMAL", "180");

        assertThat(settings.asMoney(key).asString()).isEqualTo("180.00");
    }

    @Test
    void shouldReadStoredZeroDecimalAsZeroMoney() {
        String key = givenSetting("DECIMAL", "0.00");

        assertThat(settings.asMoney(key)).isEqualTo(Money.ZERO);
    }

    /**
     * Assumption to confirm with Breno: a percentage setting is stored in percent points
     * ({@code "10.00"} means ten percent), not as the fraction {@code 0.10}. The spec does not say.
     */
    @Test
    void shouldReadStoredDecimalAsPercentageInPercentPoints() {
        String key = givenSetting("DECIMAL", "10.00");

        assertThat(settings.asPercentage(key)).isEqualTo(Percentage.ofPercent("10.00"));
    }

    @Test
    void shouldReadStoredTrueAsBoolean() {
        String key = givenSetting("BOOLEAN", "true");

        assertThat(settings.asBoolean(key)).isTrue();
    }

    @Test
    void shouldReadStoredFalseAsBoolean() {
        String key = givenSetting("BOOLEAN", "false");

        assertThat(settings.asBoolean(key)).isFalse();
    }

    @Test
    void shouldReadStoredTimeAsLocalTime() {
        String key = givenSetting("TIME", "18:30");

        assertThat(settings.asLocalTime(key)).isEqualTo(LocalTime.of(18, 30));
    }

    @Test
    void shouldReturnSameValueOnEveryRead() {
        String key = givenSetting("INTEGER", "7");

        assertThat(settings.asInteger(key)).isEqualTo(7);
        assertThat(settings.asInteger(key)).isEqualTo(7);
    }

    // ------------------------------------------------------------- wrong type never converts

    @Test
    void shouldRejectTextSettingReadAsInteger() {
        String key = givenSetting("STRING", "12");

        DomainException failure = catchThrowableOfType(DomainException.class, () -> settings.asInteger(key));

        assertThat(failure).isNotNull();
        assertThat(failure.code()).matches(UPPER_SNAKE_CASE);
    }

    @Test
    void shouldRejectIntegerSettingReadAsBoolean() {
        String key = givenSetting("INTEGER", "1");

        DomainException failure = catchThrowableOfType(DomainException.class, () -> settings.asBoolean(key));

        assertThat(failure).isNotNull();
    }

    @Test
    void shouldRejectTimeSettingReadAsText() {
        String key = givenSetting("TIME", "18:30");

        DomainException failure = catchThrowableOfType(DomainException.class, () -> settings.asText(key));

        assertThat(failure).isNotNull();
    }

    @Test
    void shouldRejectBooleanSettingReadAsMoney() {
        String key = givenSetting("BOOLEAN", "true");

        DomainException failure = catchThrowableOfType(DomainException.class, () -> settings.asMoney(key));

        assertThat(failure).isNotNull();
    }

    @Test
    void shouldRejectTextSettingReadAsLocalTime() {
        String key = givenSetting("STRING", "18:30");

        DomainException failure = catchThrowableOfType(DomainException.class, () -> settings.asLocalTime(key));

        assertThat(failure).isNotNull();
    }

    @Test
    void shouldRejectDecimalWithMoreThanTwoDecimalsReadAsMoney() {
        String key = givenSetting("DECIMAL", "180.005");

        DomainException failure = catchThrowableOfType(DomainException.class, () -> settings.asMoney(key));

        assertThat(failure).isNotNull();
        assertThat(failure.code()).matches(UPPER_SNAKE_CASE);
    }

    // ------------------------------------------------------------- cache never answers a stale value

    @Test
    void shouldAnswerTheNewValueAfterAWrite() {
        String key = givenSetting("INTEGER", "5");
        assertThat(settings.asInteger(key)).isEqualTo(5);

        settingRepository.save(settingRepository.findByKey(key).orElseThrow().withValue("9"));

        assertThat(settings.asInteger(key)).isEqualTo(9);
    }

    @Test
    void shouldPersistTheNewValueOfAWrite() {
        String key = givenSetting("INTEGER", "5");

        settingRepository.save(settingRepository.findByKey(key).orElseThrow().withValue("9"));

        assertThat(jdbcTemplate.queryForObject(
                        "select setting_value from setting where setting_key = ?", String.class, key))
                .isEqualTo("9");
    }

    @Test
    void shouldInsertTheKeyWhenAWriteFindsNoRow() {
        Setting neverConfigured = settingRepository
                .findByKey(givenSetting("INTEGER", "5"))
                .orElseThrow();
        jdbcTemplate.update("delete from setting where setting_key = ?", neverConfigured.settingKey());

        settingRepository.save(neverConfigured.withValue("9"));

        assertThat(settings.asInteger(neverConfigured.settingKey())).isEqualTo(9);
    }

    // ------------------------------------------------------------- missing key

    @Test
    void shouldRejectMissingKeyWithDomainErrorCode() {
        String key = absentKey();

        DomainException failure = catchThrowableOfType(DomainException.class, () -> settings.asText(key));

        assertThat(failure).isNotNull();
        assertThat(failure.code()).matches(UPPER_SNAKE_CASE);
    }

    @Test
    void shouldRejectMissingKeyOnEveryTypedReader() {
        String key = absentKey();

        assertThat(catchThrowableOfType(DomainException.class, () -> settings.asText(key))).isNotNull();
        assertThat(catchThrowableOfType(DomainException.class, () -> settings.asInteger(key))).isNotNull();
        assertThat(catchThrowableOfType(DomainException.class, () -> settings.asMoney(key))).isNotNull();
        assertThat(catchThrowableOfType(DomainException.class, () -> settings.asPercentage(key))).isNotNull();
        assertThat(catchThrowableOfType(DomainException.class, () -> settings.asBoolean(key))).isNotNull();
        assertThat(catchThrowableOfType(DomainException.class, () -> settings.asLocalTime(key))).isNotNull();
    }

    @Test
    void shouldUseDifferentCodesForMissingKeyAndWrongType() {
        String storedAsText = givenSetting("STRING", "12");

        DomainException missing = catchThrowableOfType(DomainException.class, () -> settings.asText(absentKey()));
        DomainException wrongType = catchThrowableOfType(DomainException.class, () -> settings.asInteger(storedAsText));

        assertThat(missing.code()).isNotEqualTo(wrongType.code());
    }
}
