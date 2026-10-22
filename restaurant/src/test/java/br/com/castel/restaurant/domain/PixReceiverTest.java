package br.com.castel.restaurant.domain;

import static br.com.castel.restaurant.domain.TabFixtures.assertRejectedWith;
import static br.com.castel.restaurant.domain.TabFixtures.orderDish;
import static br.com.castel.restaurant.domain.TabFixtures.tableTab;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.INVALID_PIX_RECEIVER;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.RECEIVER;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.sharedkernel.Money;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The static Pix code ("copia e cola") the phone shows (decisions #22, #23, #32 and #33 of task
 * qr-table-payment), read back field by field as the EMV standard writes it: two digits of id, two of
 * length, then the value.
 */
class PixReceiverTest {

    /** The example of the BR Code manual of the Central Bank, whose CRC is 1D3D. */
    private static final String BCB_EXAMPLE_WITHOUT_CRC =
            "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-4266554400005204000053039865802BR"
                    + "5913Fulano de Tal6008BRASILIA62070503***6304";

    private static final String KEY = "pix@castel.com.br";

    /** An e-mail key of {@code length} characters: the domain plus as many letters before it. */
    private static String emailKeyOf(int length) {
        String domain = "@castel.com.br";
        return "a".repeat(length - domain.length()) + domain;
    }

    private static String keyOf(String typed) {
        return PixReceiver.of(typed, "Castel", "Fortaleza").key();
    }

    private static Map<String, String> fields(String emv) {
        Map<String, String> fields = new LinkedHashMap<>();
        int position = 0;
        while (position < emv.length()) {
            String id = emv.substring(position, position + 2);
            int length = Integer.parseInt(emv.substring(position + 2, position + 4));
            fields.put(id, emv.substring(position + 4, position + 4 + length));
            position += 4 + length;
        }
        return fields;
    }

    private static String payload(PixReceiver receiver, String amount) {
        return receiver.pixPayloadFor(Money.of(amount), "TX1");
    }

    // ------------------------------------------------------------------ CRC

    @Test
    void shouldComputeTheCrcOfTheCentralBankExample() {
        assertThat(PixReceiver.crc16(BCB_EXAMPLE_WITHOUT_CRC)).isEqualTo("1D3D");
    }

    @Test
    void shouldEndTheCodeWithTheCrcOfEverythingBeforeIt() {
        String code = payload(RECEIVER, "36.69");

        String withoutCrc = code.substring(0, code.length() - 4);
        assertThat(withoutCrc).endsWith("6304");
        assertThat(code.substring(code.length() - 4)).isEqualTo(PixReceiver.crc16(withoutCrc));
    }

    // ------------------------------------------------------------------ fields

    @Nested
    class Fields {

        @Test
        void shouldWriteTheFixedFieldsOfAStaticPixInBrazilianReais() {
            Map<String, String> fields = fields(payload(RECEIVER, "36.69"));

            assertThat(fields).containsEntry("00", "01")
                    .containsEntry("52", "0000")
                    .containsEntry("53", "986")
                    .containsEntry("58", "BR")
                    .doesNotContainKey("01");
            assertThat(fields.keySet()).containsExactly("00", "26", "52", "53", "54", "58", "59", "60", "62", "63");
        }

        @Test
        void shouldCarryTheKeyUnderTheGuiOfThePix() {
            Map<String, String> account = fields(fields(payload(RECEIVER, "36.69")).get("26"));

            assertThat(account).containsEntry("00", "br.gov.bcb.pix").containsEntry("01", "pix@castel.com.br");
        }

        @Test
        void shouldWriteTheAmountWithADotAndTwoDecimals() {
            assertThat(fields(payload(RECEIVER, "36.69")).get("54")).isEqualTo("36.69");
            assertThat(fields(payload(RECEIVER, "5")).get("54")).isEqualTo("5.00");
            assertThat(fields(payload(RECEIVER, "1234.50")).get("54")).isEqualTo("1234.50");
        }

        @Test
        void shouldCarryTheTransactionIdUnderField62() {
            assertThat(fields(fields(payload(RECEIVER, "36.69")).get("62"))).containsEntry("05", "TX1");
        }

        @Test
        void shouldCutTheTransactionIdToTwentyFiveCharacters() {
            String code = RECEIVER.pixPayloadFor(Money.of("1.00"), "A".repeat(30));

            assertThat(fields(fields(code).get("62")).get("05")).isEqualTo("A".repeat(25));
        }
    }

    // ------------------------------------------------------------------ name and city

    @Nested
    class NameAndCity {

        @Test
        void shouldCutTheNameToTwentyFiveCharacters() {
            PixReceiver receiver = PixReceiver.of(KEY, "Restaurante Castel do Sertao Ltda", "Fortaleza");

            assertThat(fields(payload(receiver, "1.00")).get("59")).isEqualTo("Restaurante Castel do Ser");
        }

        @Test
        void shouldCutTheCityToFifteenCharacters() {
            PixReceiver receiver = PixReceiver.of(KEY, "Castel", "Juazeiro do Norte");

            assertThat(fields(payload(receiver, "1.00")).get("60")).isEqualTo("Juazeiro do Nor");
        }

        @Test
        void shouldStripTheAccentsOfNameAndCity() {
            PixReceiver receiver = PixReceiver.of(KEY, "Pousada São João", "Maracanaú");

            Map<String, String> fields = fields(payload(receiver, "1.00"));
            assertThat(fields.get("59")).isEqualTo("Pousada Sao Joao");
            assertThat(fields.get("60")).isEqualTo("Maracanau");
        }

        @Test
        void shouldCountTheLengthOfTheStrippedTextNotOfTheAccentedOne() {
            PixReceiver receiver = PixReceiver.of(KEY, "Castel", "São José de Ribamar");

            assertThat(fields(payload(receiver, "1.00")).get("60")).isEqualTo("Sao Jose de Rib");
        }

        @Test
        void shouldKeepWhatTheAdministratorTypedTrimmed() {
            PixReceiver receiver = PixReceiver.of("  " + KEY + "  ", "  Pousada São João  ", "  Maracanaú ");

            assertThat(receiver.key()).isEqualTo(KEY);
            assertThat(receiver.receiverName()).isEqualTo("Pousada São João");
            assertThat(receiver.city()).isEqualTo("Maracanaú");
        }
    }

    // ------------------------------------------------------------------ validation (#33)

    @Nested
    class Validation {

        @Test
        void shouldAcceptAKeyOfExactlySeventySevenCharacters() {
            assertThat(keyOf(emailKeyOf(77))).hasSize(77);
        }

        @Test
        void shouldRefuseAKeyOfSeventyEightCharacters() {
            assertRejectedWith(() -> keyOf(emailKeyOf(78)), INVALID_PIX_RECEIVER);
        }

        @Test
        void shouldRefuseABlankKey() {
            assertRejectedWith(() -> PixReceiver.of("   ", "Castel", "Fortaleza"), INVALID_PIX_RECEIVER);
        }

        @Test
        void shouldRefuseAMissingName() {
            assertRejectedWith(() -> PixReceiver.of(KEY, null, "Fortaleza"), INVALID_PIX_RECEIVER);
        }

        @Test
        void shouldRefuseABlankCity() {
            assertRejectedWith(() -> PixReceiver.of(KEY, "Castel", " "), INVALID_PIX_RECEIVER);
        }
    }

    // ------------------------------------------------------------------ the key as the DICT stores it (#53)

    @Nested
    class KeyKinds {

        @Test
        void shouldKeepOnlyTheDigitsOfAMaskedCpf() {
            assertThat(keyOf("529.982.247-25")).isEqualTo("52998224725");
        }

        @Test
        void shouldRefuseACpfWhoseCheckDigitDoesNotMatch() {
            assertRejectedWith(() -> keyOf("529.982.247-26"), INVALID_PIX_RECEIVER);
        }

        @Test
        void shouldKeepTheFourteenDigitsOfAMaskedCnpj() {
            assertThat(keyOf("12.345.678/0001-95")).isEqualTo("12345678000195");
        }

        @Test
        void shouldKeepAPhoneAsPlusFiftyFiveAndItsDigits() {
            assertThat(keyOf("+55 (85) 99999-0000")).isEqualTo("+5585999990000");
        }

        /** Without the {@code +}, eleven digits are read as a CPF, and these check digits do not match. */
        @Test
        void shouldReadElevenDigitsWithoutPlusAsACpfAndRefuseThemWhenTheyAreNotOne() {
            assertRejectedWith(() -> keyOf("(85) 99999-0000"), INVALID_PIX_RECEIVER);
        }

        @Test
        void shouldRefuseTenDigitsWithoutPlusAsNoKindOfKey() {
            assertRejectedWith(() -> keyOf("(85) 9999-0000"), INVALID_PIX_RECEIVER);
        }

        @Test
        void shouldLowercaseAnEmail() {
            assertThat(keyOf("Pix@Castel.COM.br")).isEqualTo("pix@castel.com.br");
        }

        @Test
        void shouldLowercaseARandomKey() {
            assertThat(keyOf("123E4567-E12B-12D1-A456-426655440000")).isEqualTo("123e4567-e12b-12d1-a456-426655440000");
        }

        @Test
        void shouldRefuseAKeyWithACharacterOutsideAscii() {
            assertRejectedWith(() -> keyOf("pix@castél.com.br"), INVALID_PIX_RECEIVER);
        }

        @Test
        void shouldRefuseAKeyOfNoKnownKind() {
            assertRejectedWith(() -> keyOf("chave"), INVALID_PIX_RECEIVER);
        }

        @Test
        void shouldRefuseAtSavingANameTheStandardWouldLeaveEmpty() {
            assertRejectedWith(() -> PixReceiver.of(KEY, "東京食堂", "Fortaleza"), INVALID_PIX_RECEIVER);
        }

        @Test
        void shouldRefuseAtSavingACityTheStandardWouldLeaveEmpty() {
            assertRejectedWith(() -> PixReceiver.of(KEY, "Castel", "東京"), INVALID_PIX_RECEIVER);
        }
    }

    // ------------------------------------------------------------------ the code of a request

    @Test
    void shouldFreezeOnTheRequestACodeOfItsAmountKeyedByTheLastTwentyFiveHexDigitsOfItsId() {
        Tab tab = tableTab();
        TablePaymentRequest request = request(tab, orderDish(tab, "33.35"));

        Map<String, String> fields = fields(request.pixPayload());
        String hex = request.id().value().toString().replace("-", "");
        assertThat(fields.get("54")).isEqualTo("36.69");
        assertThat(fields(fields.get("62")).get("05")).isEqualToIgnoringCase(hex.substring(hex.length() - 25));
    }
}
