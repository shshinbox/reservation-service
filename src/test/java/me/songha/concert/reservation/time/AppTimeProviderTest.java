package me.songha.concert.reservation.time;

import me.songha.concert.time.AppTimeProvider;
import me.songha.concert.time.TimeOverrideSettings;
import me.songha.concert.time.TimeOverrideRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppTimeProviderTest {

    private final TimeOverrideRepository repository = mock(TimeOverrideRepository.class);
    private final MockEnvironment environment = new MockEnvironment();
    private final AppTimeProvider appTimeProvider = new AppTimeProvider(repository, environment);

    @Test
    void nowInstantUsesEnabledOverride() {
        Instant fixedInstant = Instant.parse("2026-05-25T11:50:00Z");
        when(repository.get()).thenReturn(new TimeOverrideSettings(true, fixedInstant, "Asia/Seoul"));

        assertThat(appTimeProvider.nowInstant()).isEqualTo(fixedInstant);
    }

    @Test
    void nowInstantIgnoresOverrideInProdProfile() {
        environment.setActiveProfiles("prod");

        appTimeProvider.nowInstant();

        verify(repository, never()).get();
    }

    @Test
    void nowZonedUsesOverrideZone() {
        Instant fixedInstant = Instant.parse("2026-05-25T11:50:00Z");
        when(repository.get()).thenReturn(new TimeOverrideSettings(true, fixedInstant, "Asia/Seoul"));

        assertThat(appTimeProvider.nowZoned().getZone().getId()).isEqualTo("Asia/Seoul");
        assertThat(appTimeProvider.nowZoned().toInstant()).isEqualTo(fixedInstant);
    }
}
