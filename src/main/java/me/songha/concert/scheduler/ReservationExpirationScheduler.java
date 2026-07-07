package me.songha.concert.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "reservation.expiration-scheduler.enabled",
        havingValue = "true"
)
public class ReservationExpirationScheduler {

    private final ReservationExpirationService reservationExpirationService;

    @Scheduled(fixedDelayString = "${reservation.expiration-scheduler.fixed-delay-ms}")
    public void expireHoldingReservations() {
        int expiredCount = reservationExpirationService.expireHoldingReservations();
        if (expiredCount > 0) {
            log.info("Expired PAYMENT_PENDING reservations. count={}", expiredCount);
        }
    }
}
