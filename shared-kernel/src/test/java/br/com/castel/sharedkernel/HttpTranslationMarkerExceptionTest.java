package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serial;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/**
 * {@link NotFoundException} and {@link ConflictException} are markers of HTTP translation
 * (task 0.5b, item 2): they exist so the global handler can answer 404 and 409 without knowing
 * any concrete domain exception. They carry no behaviour of their own.
 *
 * <p>The test defines its own subclasses on purpose: a marker that cannot be extended with an
 * explicit code is useless to the modules of the next waves.
 */
class HttpTranslationMarkerExceptionTest {

    private static final String NOT_FOUND_CODE = "SAMPLE_RESOURCE_NOT_FOUND";
    private static final String CONFLICT_CODE = "SAMPLE_RESOURCE_ALREADY_EXISTS";

    static class SampleNotFoundException extends NotFoundException {

        @Serial
        private static final long serialVersionUID = 1L;

        SampleNotFoundException() {
            super(NOT_FOUND_CODE, "Sample resource does not exist");
        }
    }

    static class SampleConflictException extends ConflictException {

        @Serial
        private static final long serialVersionUID = 1L;

        SampleConflictException() {
            super(CONFLICT_CODE, "Sample resource already exists");
        }
    }

    @Test
    void shouldTreatNotFoundExceptionAsDomainException() {
        NotFoundException exception = new SampleNotFoundException();

        assertThat(exception).isInstanceOf(DomainException.class);
    }

    @Test
    void shouldTreatConflictExceptionAsDomainException() {
        ConflictException exception = new SampleConflictException();

        assertThat(exception).isInstanceOf(DomainException.class);
    }

    @Test
    void shouldKeepExplicitCodeOfNotFoundSubclass() {
        NotFoundException exception = new SampleNotFoundException();

        assertThat(exception.code()).isEqualTo(NOT_FOUND_CODE);
    }

    @Test
    void shouldKeepExplicitCodeOfConflictSubclass() {
        ConflictException exception = new SampleConflictException();

        assertThat(exception.code()).isEqualTo(CONFLICT_CODE);
    }

    @Test
    void shouldForbidDirectInstantiationOfNotFoundMarker() {
        assertThat(Modifier.isAbstract(NotFoundException.class.getModifiers())).isTrue();
    }

    @Test
    void shouldForbidDirectInstantiationOfConflictMarker() {
        assertThat(Modifier.isAbstract(ConflictException.class.getModifiers())).isTrue();
    }

    @Test
    void shouldKeepConflictAndNotFoundAsUnrelatedMarkers() {
        assertThat(NotFoundException.class.isAssignableFrom(ConflictException.class)).isFalse();
        assertThat(ConflictException.class.isAssignableFrom(NotFoundException.class)).isFalse();
    }
}
