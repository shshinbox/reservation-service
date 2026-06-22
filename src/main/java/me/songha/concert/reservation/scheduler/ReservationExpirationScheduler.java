package me.songha.concert.reservation.scheduler;

import me.songha.concert.reservation.service.ReservationOperationService;
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

    private final ReservationOperationService reservationOperationService;

    @Scheduled(fixedDelayString = "${reservation.expiration-scheduler.fixed-delay-ms}")
    public void expireHoldingReservations() {
        int expiredCount = reservationOperationService.expireHoldingReservations();
        if (expiredCount > 0) {
            log.info("Expired PAYMENT_PENDING reservations. count={}", expiredCount);
        }
    }
}
