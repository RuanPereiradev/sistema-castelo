package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.AuditedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A table of the restaurant, where a table-service tab opens: "Mesa 5", "V3".
 *
 * <p>Seats and area are optional and free text is enough for the area, with no catalog of areas
 * (decision #4). A dining table is never deleted, only deactivated (decision #6): the tab keeps
 * pointing at it after it leaves the floor. Whether it is occupied is not stored here; it follows
 * from having an open tab, which arrives in task 2.2.
 *
 * <p>Label uniqueness in the property is a rule about the set of tables, so it is checked by the use
 * case and held by {@code uk_dining_table_label}, not by this aggregate.
 */
@Entity
@Table(name = "dining_table")
public class DiningTable extends AuditedEntity {

    /** Maximum length of a label after trimming, matching the column. */
    public static final int MAXIMUM_LABEL_LENGTH = 20;

    /** Maximum length of an area after trimming, matching the column. */
    public static final int MAXIMUM_AREA_LENGTH = 50;

    public static final int MINIMUM_SEATS = 1;

    public static final int MAXIMUM_SEATS = 999;

    /**
     * How a person reading Portuguese orders text: letter case and accents do not matter, so "Área
     * externa" comes before "Varanda" and "varanda" is the same area as "Varanda".
     */
    private static final Collator READING = readingCollator();

    /**
     * Order of the list (decisions #8 and #11): by area, tables without an area last, then by label in
     * natural order, so "Mesa 2" comes before "Mesa 10". Areas that differ only in case or accents are
     * one group; the last step only makes the order total.
     */
    private static final Comparator<DiningTable> LISTING_ORDER = Comparator
            .comparing(
                    (DiningTable table) -> table.area().orElse(null),
                    Comparator.nullsLast(DiningTable::compareAsRead))
            .thenComparing(DiningTable::label, DiningTable::compareNaturally)
            .thenComparing(
                    (DiningTable table) -> table.area().orElse(null),
                    Comparator.nullsLast(Comparator.<String>naturalOrder()));

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id"))
    private DiningTableId id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "label", nullable = false, length = MAXIMUM_LABEL_LENGTH)
    private String label;

    @Column(name = "seats")
    private Short seats;

    @Column(name = "area", length = MAXIMUM_AREA_LENGTH)
    private String area;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected DiningTable() {
        // for JPA
    }

    private DiningTable(DiningTableId id, UUID propertyId, Description description) {
        this.id = id;
        this.propertyId = propertyId;
        this.active = true;
        apply(description);
    }

    /**
     * A new table, born active.
     *
     * @throws InvalidDiningTableLabelException if the label is missing, blank or longer than 20
     *     characters once trimmed
     * @throws InvalidDiningTableSeatsException if seats are given outside 1 to 999
     * @throws InvalidDiningTableAreaException if the area is longer than 50 characters once trimmed
     */
    public static DiningTable create(UUID propertyId, String label, Integer seats, String area) {
        Objects.requireNonNull(propertyId, "propertyId");
        return new DiningTable(DiningTableId.newId(), propertyId, Description.of(label, seats, area));
    }

    /**
     * Replaces label, seats and area at once (decision #7): a missing seats or area clears it. Every
     * field is validated before any is changed, so a refused description leaves the table as it
     * was. An inactive table can be redescribed.
     *
     * @throws InvalidDiningTableLabelException if the label is missing, blank or too long
     * @throws InvalidDiningTableSeatsException if seats are given outside 1 to 999
     * @throws InvalidDiningTableAreaException if the area is too long
     */
    public void redescribe(String label, Integer seats, String area) {
        apply(Description.of(label, seats, area));
    }

    /** Off the list the waiter reads. Deactivating an inactive table is accepted silently. */
    public void deactivate() {
        this.active = false;
    }

    /** Back on the list. Activating an active table is accepted silently. */
    public void activate() {
        this.active = true;
    }

    /** The order in which the list of tables is shown (decision #8). */
    public static Comparator<DiningTable> listingOrder() {
        return LISTING_ORDER;
    }

    private void apply(Description description) {
        this.label = description.label();
        this.seats = description.seats();
        this.area = description.area();
    }

    public DiningTableId id() {
        return id;
    }

    public UUID propertyId() {
        return propertyId;
    }

    public String label() {
        return label;
    }

    public Optional<Integer> seats() {
        return Optional.ofNullable(seats).map(Short::intValue);
    }

    public Optional<String> area() {
        return Optional.ofNullable(area);
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Label, seats and area already validated and normalised, in the order label, seats, area. Built
     * whole before the aggregate changes, so a refused field never leaves a partial change behind.
     */
    private record Description(String label, Short seats, String area) {

        static Description of(String label, Integer seats, String area) {
            String validLabel = requireValidLabel(label);
            Short validSeats = requireValidSeats(seats);
            String validArea = normaliseArea(area);
            return new Description(validLabel, validSeats, validArea);
        }

        private static String requireValidLabel(String label) {
            if (label == null || label.isBlank()) {
                throw new InvalidDiningTableLabelException("A dining table needs a label");
            }
            String trimmed = label.trim();
            if (trimmed.length() > MAXIMUM_LABEL_LENGTH) {
                throw new InvalidDiningTableLabelException(
                        "A dining table label takes at most " + MAXIMUM_LABEL_LENGTH + " characters");
            }
            return trimmed;
        }

        private static Short requireValidSeats(Integer seats) {
            if (seats == null) {
                return null;
            }
            if (seats < MINIMUM_SEATS || seats > MAXIMUM_SEATS) {
                throw new InvalidDiningTableSeatsException(
                        "A dining table takes from " + MINIMUM_SEATS + " to " + MAXIMUM_SEATS + " seats");
            }
            return seats.shortValue();
        }

        /** A blank area is no area (decision #4). */
        private static String normaliseArea(String area) {
            if (area == null || area.isBlank()) {
                return null;
            }
            String trimmed = area.trim();
            if (trimmed.length() > MAXIMUM_AREA_LENGTH) {
                throw new InvalidDiningTableAreaException(
                        "A dining table area takes at most " + MAXIMUM_AREA_LENGTH + " characters");
            }
            return trimmed;
        }
    }

    // ------------------------------------------------------------- natural order

    private static Collator readingCollator() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
        collator.setStrength(Collator.PRIMARY);
        return collator;
    }

    /**
     * {@link #compareAsRead}, falling back to plain comparison when two texts differ only in letter
     * case, accents or leading zeros, so the order is total and stable.
     */
    private static int compareNaturally(String left, String right) {
        int asRead = compareAsRead(left, right);
        return asRead != 0 ? asRead : left.compareTo(right);
    }

    /**
     * Runs of digits by their numeric value, the rest as a Portuguese reader orders it, ignoring case
     * and accents. "Mesa 2" comes before "Mesa 10", and "mesa 3" sits between them.
     */
    private static int compareAsRead(String left, String right) {
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            int leftEnd = endOfRun(left, leftIndex);
            int rightEnd = endOfRun(right, rightIndex);
            String leftRun = left.substring(leftIndex, leftEnd);
            String rightRun = right.substring(rightIndex, rightEnd);
            int comparison = isDigitRun(leftRun) && isDigitRun(rightRun)
                    ? compareNumerically(leftRun, rightRun)
                    : READING.compare(leftRun, rightRun);
            if (comparison != 0) {
                return comparison;
            }
            leftIndex = leftEnd;
            rightIndex = rightEnd;
        }
        return Integer.compare(left.length() - leftIndex, right.length() - rightIndex);
    }

    /** End of the run of digits, or of non-digits, that starts at {@code start}. */
    private static int endOfRun(String text, int start) {
        boolean digits = isDigit(text.charAt(start));
        int end = start + 1;
        while (end < text.length() && isDigit(text.charAt(end)) == digits) {
            end++;
        }
        return end;
    }

    private static boolean isDigitRun(String run) {
        return isDigit(run.charAt(0));
    }

    /** ASCII digits only, so every digit run compares by length and then character by character. */
    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    /** Compares digit runs of any length without parsing them, so no label can overflow a number. */
    private static int compareNumerically(String left, String right) {
        String leftDigits = withoutLeadingZeros(left);
        String rightDigits = withoutLeadingZeros(right);
        int byLength = Integer.compare(leftDigits.length(), rightDigits.length());
        return byLength != 0 ? byLength : leftDigits.compareTo(rightDigits);
    }

    private static String withoutLeadingZeros(String digits) {
        int first = 0;
        while (first < digits.length() - 1 && digits.charAt(first) == '0') {
            first++;
        }
        return digits.substring(first);
    }
}
