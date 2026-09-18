package br.com.castel.sharedkernel;

import java.io.Serial;

/** A setting was read as a type other than the one it was stored as. */
public class SettingTypeMismatchException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "SETTING_TYPE_MISMATCH";

    public SettingTypeMismatchException(String settingKey, SettingValueType storedType, SettingValueType requestedType) {
        super(CODE, "Setting '" + settingKey + "' is stored as " + storedType + " and cannot be read as "
                + requestedType);
    }
}
