package me.songha.concert.reservation.redis;

import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationSeat;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class SoldSeatRedisRepository {

    private final StringRedisTemplate redisTemplate;

    public void markSold(Reservation reservation, List<ReservationSeat> seats) {
        for (ReservationSeat seat : seats) {
            redisTemplate.opsForValue().set(key(reservation, seat), reservation.getReservationId().toString());
        }
    }

    public void deleteSold(Reservation reservation, List<ReservationSeat> seats) {
        for (ReservationSeat seat : seats) {
            redisTemplate.delete(key(reservation, seat));
        }
    }

    public void deleteHold(Reservation reservation, List<ReservationSeat> seats) {
        redisTemplate.delete(userHoldsKey(reservation));
        for (ReservationSeat seat : seats) {
            redisTemplate.delete(seatHoldKey(reservation, seat));
        }
    }

    public void syncScheduleSold(String scheduleId, List<ReservationSeat> soldSeats) {
        Set<String> expectedKeys = new HashSet<>();
        for (ReservationSeat seat : soldSeats) {
            String key = key(scheduleId, seat.getSeatId());
            expectedKeys.add(key);
            redisTemplate.opsForValue().set(key, seat.getReservationId().toString());
        }

        try (Cursor<String> keys = redisTemplate.scan(ScanOptions.scanOptions()
                .match("sold:schedule:%s:seat:*".formatted(scheduleId))
                .count(1000)
                .build())) {
            while (keys.hasNext()) {
                String key = keys.next();
                if (!expectedKeys.contains(key)) {
                    redisTemplate.delete(key);
                }
            }
        }
    }

    private String key(Reservation reservation, ReservationSeat seat) {
        return key(reservation.getScheduleId(), seat.getSeatId());
    }

    private String key(String scheduleId, String seatId) {
        return "sold:schedule:%s:seat:%s".formatted(scheduleId, seatId);
    }

    private String userHoldsKey(Reservation reservation) {
        return "user:holds:{%s}:%s".formatted(reservation.getScheduleId(), reservation.getUserId());
    }

    private String seatHoldKey(Reservation reservation, ReservationSeat seat) {
        return "seat:hold:{%s}:%s".formatted(reservation.getScheduleId(), seat.getSeatId());
    }
}
