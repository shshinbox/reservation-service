package me.songha.concert.reservation.api;

import me.songha.concert.time.TimeOverrideAdminController;
import me.songha.concert.time.TimeOverrideSettings;
import me.songha.concert.time.TimeOverrideRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TimeOverrideAdminControllerTest {

    private final TimeOverrideRepository repository = mock(TimeOverrideRepository.class);
    private final MockEnvironment environment = new MockEnvironment();
    private final TimeOverrideAdminController controller = new TimeOverrideAdminController(repository, environment);

    @Test
    void updateSavesTimeOverrideConfig() {
        TimeOverrideSettings request = new TimeOverrideSettings(
                true,
                Instant.parse("2026-05-25T11:50:00Z"),
                "Asia/Seoul"
        );

        controller.update(request);

        verify(repository).save(request);
    }

    @Test
    void resetDeletesTimeOverrideConfig() {
        controller.reset();

        verify(repository).reset();
    }

    @Test
    void updateThrowsInProdProfile() {
        environment.setActiveProfiles("prod");
        TimeOverrideSettings request = new TimeOverrideSettings(
                true,
                Instant.parse("2026-05-25T11:50:00Z"),
                "Asia/Seoul"
        );

        assertThatThrownBy(() -> controller.update(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Time override is not allowed in the prod environment.");
        verify(repository, never()).save(request);
    }
}
