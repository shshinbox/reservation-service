# Reservation Service

좌석 선점 서비스가 공유 Kafka에 발행한 이벤트를 소비해 예약을 생성하고, 결제 웹훅을 통해 예약을 확정하는 서비스입니다.

이 서비스는 예약 RDB를 소유합니다. Kafka와 Redis는 다른 콘서트 모듈과 함께 사용하는 공유 인프라입니다.

## 역할

- 공유 Kafka의 좌석 선점 이벤트를 소비합니다.
- 좌석 선점 이벤트를 `PAYMENT_PENDING` 예약으로 저장합니다.
- 결제 웹훅을 받아 예약을 `CONFIRMED`로 확정합니다.
- 결제 완료 좌석을 PostgreSQL과 공유 Redis에 기록합니다.
- 사용자가 본인 예약을 조회하고 취소할 수 있게 합니다.
- 만료된 결제대기 예약을 `EXPIRED`로 정리합니다.

## 외부 계약

### Kafka

공유 토픽:

```text
seat-hold-events
```

지원 이벤트 타입:

```text
SEAT_HOLD_HELD
SEAT_HOLD_RELEASED
```

이벤트 payload:

```json
{
  "eventId": "event-uuid",
  "eventType": "SEAT_HOLD_HELD",
  "holdId": "hold-uuid",
  "scheduleId": "schedule-1",
  "seatId": "A-12",
  "userId": "user-1",
  "expiresAt": "2026-05-25T11:55:00Z",
  "occurredAt": "2026-05-25T11:50:00Z"
}
```

처리 방식:

```text
SEAT_HOLD_HELD
  -> PAYMENT_PENDING 예약 생성

SEAT_HOLD_RELEASED
  -> PAYMENT_PENDING 예약이면 CANCELLED 처리
  -> CONFIRMED 예약이면 무시
```

### Redis

공유 Redis에는 결제 완료 좌석 정보를 저장합니다. 좌석 선점 서비스는 이 키를 보고 이미 판매된 좌석인지 판단합니다.

키:

```text
sold:schedule:{scheduleId}:seat:{seatId}
```

값:

```text
reservationId
```

TTL:

```text
없음
```

확정된 예약이 취소되면 해당 Redis key를 삭제합니다. 따라서 취소된 좌석은 다시 판매할 수 있습니다.

## 예약 상태

```text
PAYMENT_PENDING
CONFIRMED
CANCELLED
EXPIRED
```

상태 전이:

```text
SEAT_HOLD_HELD
  -> PAYMENT_PENDING

결제 웹훅 PAID
  -> PAYMENT_PENDING -> CONFIRMED

사용자 취소
  -> PAYMENT_PENDING -> CANCELLED
  -> CONFIRMED -> CANCELLED

만료 스케줄러
  -> PAYMENT_PENDING -> EXPIRED

SEAT_HOLD_RELEASED
  -> PAYMENT_PENDING -> CANCELLED
```

## API

### 결제 웹훅

```http
POST /webhooks/payments
X-Payment-Webhook-Secret: local-payment-webhook-secret
Content-Type: application/json
```

```json
{
  "eventId": "payment-event-1",
  "paymentId": "payment-1",
  "reservationId": "reservation-uuid",
  "status": "PAID",
  "occurredAt": "2026-05-25T11:50:00Z"
}
```

`status = PAID`일 때만 예약을 확정합니다. 다른 상태는 정상 수신 후 무시합니다.

### 예약 취소

```http
POST /reservations/{reservationId}/cancel
X-Authenticated-User-Id: user-1
```

### 예약 단건 조회

```http
GET /reservations/{reservationId}
X-Authenticated-User-Id: user-1
```

본인 예약만 조회할 수 있습니다.

### 내 예약 목록 조회

```http
GET /me/reservations
X-Authenticated-User-Id: user-1
```

## 저장 구조

주요 테이블:

```text
reservations
processed_kafka_events
reservation_status_histories
sold_seats
```

활성 예약 중복 방지:

```sql
CREATE UNIQUE INDEX uq_reservations_active_schedule_seat
ON reservations (schedule_id, seat_id)
WHERE status IN ('PAYMENT_PENDING', 'CONFIRMED');
```

활성 판매 좌석 중복 방지:

```sql
CREATE UNIQUE INDEX uq_sold_seats_active_schedule_seat
ON sold_seats (schedule_id, seat_id)
WHERE cancelled_at IS NULL;
```

이 제약 덕분에 `CANCELLED`, `EXPIRED` 좌석은 다시 판매할 수 있습니다.

## 정합성 전략

최초 예약 생성은 DB unique constraint로 방어합니다.

```text
processed_kafka_events.event_id
reservations.hold_id
활성 reservations scheduleId + seatId
```

상태 전이는 reservation row에 비관적 쓰기 락을 잡고 처리합니다.

```text
결제 확정
사용자 취소
예약 만료
좌석 선점 해제 이벤트
```

`sold_seats`가 결제 완료 좌석의 기준 저장소입니다. Redis sold key는 DB commit 이후 반영하며, 좌석 선점 서비스의 빠른 판매 여부 확인에 사용합니다.

## 설정

local profile:

```properties
LOCAL_DB_URL=jdbc:postgresql://localhost:5432/reservation
LOCAL_DB_USERNAME=reservation
LOCAL_DB_PASSWORD=reservation
LOCAL_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
LOCAL_REDIS_HOST=localhost
LOCAL_REDIS_PORT=6379
PAYMENT_WEBHOOK_SECRET=local-payment-webhook-secret
```

prod profile:

```properties
DB_URL=jdbc:postgresql://...
DB_USERNAME=...
DB_PASSWORD=...
KAFKA_BOOTSTRAP_SERVERS=...
REDIS_HOST=...
REDIS_PORT=6379
PAYMENT_WEBHOOK_SECRET=...
```

Kafka와 Redis는 콘서트 시스템의 공유 인프라 주소를 사용해야 합니다. 운영과 유사한 환경에서 이 서비스만을 위한 별도 Kafka나 Redis를 띄우지 않습니다.

## 테스트

```bash
./mvnw test
```

Repository 통합 테스트는 Testcontainers를 사용합니다. Docker를 사용할 수 없으면 해당 테스트는 skip됩니다.
