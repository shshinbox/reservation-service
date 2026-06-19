package me.songha.concert.reservation.infrastructure.redis;

import me.songha.concert.reservation.domain.Reservation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SoldSeatRedisRepository {

    private final StringRedisTemplate redisTemplate;

    public void markSold(Reservation reservation) {
        redisTemplate.opsForValue().set(key(reservation), reservation.getReservationId().toString());
    }

    public void deleteSold(Reservation reservation) {
        redisTemplate.delete(key(reservation));
    }

    private String key(Reservation reservation) {
        return "sold:schedule:%s:seat:%s".formatted(reservation.getScheduleId(), reservation.getSeatId());
    }
}
