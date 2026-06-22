package me.songha.concert.reservation.redis;

import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationSeat;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

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

    private String key(Reservation reservation, ReservationSeat seat) {
        return "sold:schedule:%s:seat:%s".formatted(reservation.getScheduleId(), seat.getSeatId());
    }
}
