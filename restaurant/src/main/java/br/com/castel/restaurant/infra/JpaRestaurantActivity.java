package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.api.RestaurantActivity;
import br.com.castel.restaurant.api.RestaurantActivityView;
import br.com.castel.sharedkernel.CurrentProperty;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers the {@link RestaurantActivity} port with one aggregate read over {@code tab}.
 *
 * <p>One query and not five: the five figures come from the same rows, and asking the database
 * once keeps them consistent with each other even if a tab is opened while the report is being
 * built.
 */
@Component
class JpaRestaurantActivity implements RestaurantActivity {

    private final SpringDataTabRepository springData;
    private final CurrentProperty currentProperty;

    JpaRestaurantActivity(SpringDataTabRepository springData, CurrentProperty currentProperty) {
        this.springData = springData;
        this.currentProperty = currentProperty;
    }

    /**
     * The days come whole; {@code opened_at} is an instant, so the end of the range is the start of
     * the day after.
     */
    @Override
    @Transactional(readOnly = true)
    public RestaurantActivityView between(LocalDate from, LocalDate to) {
        Object[] row = springData.activityBetween(
                currentProperty.id(),
                from.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        if (row == null) {
            return RestaurantActivityView.NONE;
        }
        return new RestaurantActivityView(
                intOf(row[0]), intOf(row[1]), intOf(row[2]), intOf(row[3]), intOf(row[4]));
    }

    private static int intOf(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

}
