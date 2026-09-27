package br.com.castel.app.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.restaurant.application.KitchenQueue;
import br.com.castel.restaurant.application.KitchenTicket;
import br.com.castel.restaurant.domain.TabItemId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Nothing is confirmed without being stored. When the kitchen ticket cannot be read before the commit,
 * the order is rolled back and the route does not answer 2xx; swallowing that failure answered 201 for
 * an order PostgreSQL then rolled back in silence (review, round 3).
 *
 * <p>Its own context, because the read port is replaced by one that always fails.
 */
@Import(KitchenDisplayReadFailureIntegrationTest.FailingKitchenQueueConfig.class)
class KitchenDisplayReadFailureIntegrationTest extends AbstractKitchenDisplayIntegrationTest {

    @Test
    void shouldNotStoreTheOrderWhenTheKitchenTicketCannotBeRead() {
        String pizzaId = createItem(createCategory(), "Falha", "PIZZA", false);
        String tabId = openTabOnNewTable("Falha");

        int status = exchange(post(TABS + "/" + tabId + "/items", waiterToken,
                        "{\"menuItemId\":\"%s\"}".formatted(pizzaId)))
                .statusCode();

        assertThat(status).isGreaterThanOrEqualTo(400);
        assertThat(send(get(TABS + "/" + tabId, waiterToken), 200).get("items")).isEmpty();
    }

    @TestConfiguration
    static class FailingKitchenQueueConfig {

        @Bean
        @Primary
        KitchenQueue failingKitchenQueue() {
            return new KitchenQueue() {
                @Override
                public List<KitchenTicket> ticketsOf(UUID propertyId, PrepStation station) {
                    throw new IllegalStateException("kitchen queue unavailable");
                }

                @Override
                public Optional<KitchenTicket> ticket(TabItemId itemId) {
                    throw new IllegalStateException("kitchen queue unavailable");
                }
            };
        }
    }
}
