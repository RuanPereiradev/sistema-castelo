package br.com.castel.sharedkernel;

import java.io.Serial;

/**
 * No setting answers the requested key.
 *
 * <p>A missing parameter of operation is never a {@code null} handed to the caller: it means the
 * property was never configured, and the operator has to be told, not served a silent default.
 */
public class SettingNotFoundException extends NotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "SETTING_NOT_FOUND";

    public SettingNotFoundException(String settingKey) {
        super(CODE, "No setting is configured under the key '" + settingKey + "'");
    }
}
