package br.com.castel.app.tab;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.sharedkernel.Setting;
import br.com.castel.sharedkernel.SettingRepository;
import br.com.castel.sharedkernel.SettingValueType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * A cash payment through the tab (task 3.2 over task 2.4): the tab only delegates to the folio, so it
 * inherits the rule of the cash drawer — with the drawer required and no session open, cash is
 * refused; with a session open, cash is taken and counted in it.
 *
 * <p>There is one open session per property, shared by every test class, so each test starts and ends
 * with none open and the drawer not required, as the tests of task 2.4 do.
 */
class TabClosingCashIntegrationTest extends AbstractTabClosingIntegrationTest {

    private static final String SESSIONS = "/api/billing/cash-sessions";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SettingRepository settingRepository;

    @BeforeEach
    @AfterEach
    void leaveNoSessionOpenAndCashControlOff() {
        jdbcTemplate.update("update cash_drawer_session set status = 'CLOSED', closed_at = now(), closed_by = opened_by, "
                + "expected_amount = opening_float, counted_amount = opening_float where status = 'OPEN'");
        settingRepository.save(Setting.of(CASH_DRAWER_REQUIRED_SETTING, "false", SettingValueType.BOOLEAN));
    }

    @Test
    void shouldRefuseCashWithoutASessionWhenTheDrawerIsRequiredAndTakeItOnceOneIsOpen() {
        settingRepository.save(Setting.of(CASH_DRAWER_REQUIRED_SETTING, "true", SettingValueType.BOOLEAN));
        String sodaId = createItem("Soda", "6.00", true);
        String tabId = openOnTable(createDiningTable("Dinheiro"));
        order(tabId, sodaId, 5);
        send(post(TABS + "/" + tabId + "/closing", waiterToken, ""), 200);

        JsonNode refused = send(pay(tabId, "CASH", "20.00", "cash-refused-" + suffix), 409);
        String sessionId = send(post(SESSIONS, adminToken, "{\"openingFloat\":\"50.00\"}"), 201).get("id").asString();
        JsonNode paid = send(pay(tabId, "CASH", "33.00", "cash-paid-" + suffix), 201);

        assertThat(refused.get("code").asString()).isEqualTo("CASH_DRAWER_SESSION_NOT_OPEN");
        assertThat(paid.get("bill").get("balance").asString()).isEqualTo("0.00");
        assertThat(send(get(SESSIONS + "/" + sessionId, adminToken), 200).get("expectedAmount").asString())
                .isEqualTo("83.00");
        assertThat(send(post(TABS + "/" + tabId + "/close", waiterToken, ""), 200).get("status").asString())
                .isEqualTo("CLOSED");
    }
}
