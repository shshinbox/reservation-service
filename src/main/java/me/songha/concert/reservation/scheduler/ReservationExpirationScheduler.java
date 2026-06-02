package me.songha.concert.reservation.scheduler;

import me.songha.concert.reservation.application.ReservationCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationScheduler.class);

    private final ReservationCommandService reservationCommandService;

    public ReservationExpirationScheduler(ReservationCommandService reservationCommandService) {
        this.reservationCommandService = reservationCommandService;
    }

    @Scheduled(fixedDelayString = "${reservation.expiration-scheduler.fixed-delay-ms}")
    public void expireHoldingReservations() {
        int expiredCount = reservationCommandService.expireHoldingReservations();
        if (expiredCount > 0) {
            log.info("Expired HOLDING reservations. count={}", expiredCount);
        }
    }
}
