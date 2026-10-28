package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.Cpf;
import br.com.castel.sharedkernel.InvalidCpfException;
import br.com.castel.sharedkernel.Money;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Who receives the Pix of the restaurant: the key, the name of the receiver and the city, which the
 * Pix standard requires on every code (decisions #7, #15 and #23 of task qr-table-payment).
 *
 * <p>The key is kept in the form the DICT stores it (decision #53); the name and the city as the
 * administrator typed them, trimmed. What the standard asks of the name and the city — no accents, at
 * most 25 and 15 characters — is applied when the code is built, so the screen of the administrator
 * shows what they typed; a name or city that would come out empty is refused when it is saved.
 *
 * <p>{@link #pixPayloadFor} builds the static BR Code of the Pix ("copia e cola") with the amount
 * (decision #22): EMV fields 00, 26, 52, 53, 54, 58, 59, 60, 62 and the CRC16 of field 63.
 */
public final class PixReceiver {

    /** The longest key field 26 takes: 99 characters of the template less the 22 of its GUI. */
    public static final int MAXIMUM_KEY_LENGTH = 77;

    /** What the standard takes in field 59. */
    public static final int MAXIMUM_PAYLOAD_NAME_LENGTH = 25;

    /** What the standard takes in field 60. */
    public static final int MAXIMUM_PAYLOAD_CITY_LENGTH = 15;

    /** What field 62-05 takes for a static code: up to 25 letters and digits. */
    public static final int MAXIMUM_TRANSACTION_ID_LENGTH = 25;

    private static final String PIX_GUI = "br.gov.bcb.pix";
    private static final String MERCHANT_CATEGORY_CODE = "0000";
    private static final String CURRENCY_BRL = "986";
    private static final String COUNTRY_CODE = "BR";
    private static final String CRC_FIELD_HEADER = "6304";

    private static final Pattern PRINTABLE_ASCII = Pattern.compile("[\\x20-\\x7E]+");
    private static final Pattern RANDOM_KEY =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s().-]");
    private static final Pattern BRAZILIAN_PHONE = Pattern.compile("\\+55[0-9]{10,11}");
    private static final Pattern DOCUMENT_SEPARATORS = Pattern.compile("[\\s./-]");
    private static final Pattern CPF_DIGITS = Pattern.compile("[0-9]{11}");
    private static final Pattern CNPJ_DIGITS = Pattern.compile("[0-9]{14}");

    private final String key;
    private final String receiverName;
    private final String city;

    private PixReceiver(String key, String receiverName, String city) {
        this.key = key;
        this.receiverName = receiverName;
        this.city = city;
    }

    /**
     * The receiver, with its key in the form the DICT stores it (decision #53): a CPF or a CNPJ as its
     * digits, a phone as {@code +55} and its digits, an e-mail and a random key (EVP) in lowercase. The
     * name and the city are kept as typed, and refused here when the standard would leave nothing of
     * them, so a bad receiver is refused when the administrator saves it and never on the phone.
     *
     * @throws InvalidPixReceiverException if the key is missing, has a character outside ASCII, is
     *     none of the five kinds, is a CPF whose check digits do not match, or is past 77 characters;
     *     or the name or the city is missing, blank or has no character the standard accepts
     */
    public static PixReceiver of(String key, String receiverName, String city) {
        String validKey = normaliseKey(requireText(key, "key"));
        String validName = requireText(receiverName, "receiver name");
        String validCity = requireText(city, "city");
        payloadText(validName, MAXIMUM_PAYLOAD_NAME_LENGTH, "receiver name");
        payloadText(validCity, MAXIMUM_PAYLOAD_CITY_LENGTH, "city");
        return new PixReceiver(validKey, validName, validCity);
    }

    /**
     * The key as the DICT stores it, by its kind. A phone is told from a CPF by its {@code +}: eleven
     * digits without it are a CPF. Only after the key is known to be ASCII does its length in
     * characters equal its length in bytes, which is what field 26 counts.
     */
    private static String normaliseKey(String key) {
        if (!PRINTABLE_ASCII.matcher(key).matches()) {
            throw new InvalidPixReceiverException("A Pix key takes only ASCII characters");
        }
        String normalised;
        if (RANDOM_KEY.matcher(key).matches()) {
            normalised = key.toLowerCase(Locale.ROOT);
        } else if (key.indexOf('@') >= 0) {
            normalised = requireMatching(key.toLowerCase(Locale.ROOT), EMAIL, "e-mail");
        } else if (key.startsWith("+")) {
            normalised = requireMatching("+" + PHONE_SEPARATORS.matcher(key.substring(1)).replaceAll(""),
                    BRAZILIAN_PHONE, "phone");
        } else {
            normalised = taxpayerNumber(DOCUMENT_SEPARATORS.matcher(key).replaceAll(""));
        }
        if (normalised.length() > MAXIMUM_KEY_LENGTH) {
            throw new InvalidPixReceiverException("A Pix key takes at most " + MAXIMUM_KEY_LENGTH + " characters");
        }
        return normalised;
    }

    /** Eleven digits are a CPF, checked by {@link Cpf}; fourteen a CNPJ, checked by length only. */
    private static String taxpayerNumber(String digits) {
        if (CPF_DIGITS.matcher(digits).matches()) {
            try {
                return Cpf.of(digits).value();
            } catch (InvalidCpfException invalid) {
                throw new InvalidPixReceiverException("The CPF of the Pix key is not valid");
            }
        }
        return requireMatching(digits, CNPJ_DIGITS, "CPF, CNPJ, phone, e-mail or random key");
    }

    private static String requireMatching(String text, Pattern pattern, String kind) {
        if (!pattern.matcher(text).matches()) {
            throw new InvalidPixReceiverException("The Pix key is not a valid " + kind);
        }
        return text;
    }

    /**
     * The static Pix code ("copia e cola") for this amount, the text the phone shows and turns into
     * a QR code.
     *
     * @param transactionId letters and digits only, cut to 25
     * @throws InvalidPixReceiverException if the name or the city has no letter or digit the standard
     *     accepts once the accents are removed
     */
    public String pixPayloadFor(Money amount, String transactionId) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(transactionId, "transactionId");
        String withoutCrc = field("00", "01")
                + field("26", field("00", PIX_GUI) + field("01", key))
                + field("52", MERCHANT_CATEGORY_CODE)
                + field("53", CURRENCY_BRL)
                + field("54", amount.amount().toPlainString())
                + field("58", COUNTRY_CODE)
                + field("59", payloadText(receiverName, MAXIMUM_PAYLOAD_NAME_LENGTH, "receiver name"))
                + field("60", payloadText(city, MAXIMUM_PAYLOAD_CITY_LENGTH, "city"))
                + field("62", field("05", transactionIdOf(transactionId)))
                + CRC_FIELD_HEADER;
        return withoutCrc + crc16(withoutCrc);
    }

    /**
     * CRC16/CCITT-FALSE (polynomial 0x1021, initial value 0xFFFF, no reflection, no final xor) over
     * the bytes of the text, as four uppercase hexadecimal digits: what field 63 carries, computed
     * over the whole code up to and including {@code 6304}.
     */
    static String crc16(String text) {
        int crc = 0xFFFF;
        for (byte octet : text.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (octet & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format(Locale.ROOT, "%04X", crc);
    }

    private static String field(String id, String value) {
        return id + String.format(Locale.ROOT, "%02d", value.length()) + value;
    }

    /** Without accents, only the printable ASCII the standard accepts, trimmed and cut. */
    private static String payloadText(String text, int maximumLength, String what) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\x20-\\x7E]", "")
                .trim();
        if (plain.isEmpty()) {
            throw new InvalidPixReceiverException("The Pix " + what + " has no character the standard accepts");
        }
        return plain.length() > maximumLength ? plain.substring(0, maximumLength).trim() : plain;
    }

    private static String transactionIdOf(String transactionId) {
        String alphanumeric = transactionId.replaceAll("[^A-Za-z0-9]", "");
        return alphanumeric.length() > MAXIMUM_TRANSACTION_ID_LENGTH
                ? alphanumeric.substring(alphanumeric.length() - MAXIMUM_TRANSACTION_ID_LENGTH)
                : alphanumeric;
    }

    private static String requireText(String text, String what) {
        if (text == null || text.isBlank()) {
            throw new InvalidPixReceiverException("A Pix receiver needs a " + what);
        }
        return text.trim();
    }

    public String key() {
        return key;
    }

    public String receiverName() {
        return receiverName;
    }

    public String city() {
        return city;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof PixReceiver that
                        && key.equals(that.key)
                        && receiverName.equals(that.receiverName)
                        && city.equals(that.city);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, receiverName, city);
    }
}
