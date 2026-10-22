package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.domain.PixReceiver;
import br.com.castel.restaurant.domain.PixReceiverRepository;
import br.com.castel.sharedkernel.Setting;
import br.com.castel.sharedkernel.SettingRepository;
import br.com.castel.sharedkernel.SettingValueType;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Answers the {@link PixReceiverRepository} port over the {@code setting} table, three text settings
 * written together (decision #23 of task qr-table-payment). The repository of the settings drops its
 * cache on every write, so the next code generated reads the new receiver.
 */
@Repository
class SettingPixReceiverRepository implements PixReceiverRepository {

    static final String KEY_SETTING = "pix.key";
    static final String RECEIVER_NAME_SETTING = "pix.receiver-name";
    static final String CITY_SETTING = "pix.city";

    private final SettingRepository settings;

    SettingPixReceiverRepository(SettingRepository settings) {
        this.settings = settings;
    }

    @Override
    public Optional<PixReceiver> find() {
        return text(KEY_SETTING).flatMap(key -> text(RECEIVER_NAME_SETTING)
                .flatMap(name -> text(CITY_SETTING).map(city -> PixReceiver.of(key, name, city))));
    }

    @Override
    public void save(PixReceiver receiver) {
        settings.save(Setting.of(KEY_SETTING, receiver.key(), SettingValueType.STRING));
        settings.save(Setting.of(RECEIVER_NAME_SETTING, receiver.receiverName(), SettingValueType.STRING));
        settings.save(Setting.of(CITY_SETTING, receiver.city(), SettingValueType.STRING));
    }

    private Optional<String> text(String settingKey) {
        return settings.findByKey(settingKey).map(Setting::asText);
    }
}
