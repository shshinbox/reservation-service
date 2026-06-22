# Reservation Service

좌석 선점 서비스가 발행한 Kafka 이벤트를 소비해 예약을 생성하고, 결제 웹훅을 통해 예약을 확정하는 서비스입니다.

예약 상태의 최종 기준은 PostgreSQL입니다. Redis는 결제 완료 좌석을 빠르게 조회하기 위한 보조 저장소이며, DB 트랜잭션이 성공적으로 커밋된 뒤에만 갱신합니다.

## 역할

- 좌석 선점 확정 이벤트를 소비해 `PAYMENT_PENDING` 예약을 생성합니다.
- 한 예약에서 최대 4개 좌석을 처리합니다.
- 결제 완료 웹훅을 받아 예약과 좌석을 `CONFIRMED`로 확정합니다.
- 사용자가 본인의 결제 대기 예약을 취소할 수 있습니다.
- 결제 기한이 지난 예약을 스케줄러가 `EXPIRED`로 만료 처리합니다.
- Kafka 재처리와 중복 메시지를 고려해 이벤트 처리 이력을 저장합니다.


## Kafka 이벤트 명세

소비 토픽:

```text
seat-hold-events
```

이벤트:

```text
SEAT_HOLD_CONFIRMED
```

이벤트 payload:

```json
{
  "eventId": "event-uuid",
  "eventType": "SEAT_HOLD_CONFIRMED",
  "holdId": "confirmation-uuid",
  "scheduleId": "schedule-1",
  "seatIds": ["A-12", "A-13"],
  "userId": "user-1",
  "expiresAt": null,
  "occurredAt": "2026-05-25T11:50:00Z",
  "schemaVersion": 2
}
```

처리 방식:

```text
SEAT_HOLD_CONFIRMED
  -> PAYMENT_PENDING 예약 생성
  -> paymentExpiresAt = occurredAt + 3일
```

컨슈머는 Spring Kafka 컨테이너의 ack 흐름을 사용합니다. 정상 처리되면 listener가 정상 반환하고, 예외가 발생하면 `DefaultErrorHandler`가 재시도 후 DLT로 보냅니다.

DLT 토픽은 현재 원본 토픽명에 `.DLT`를 붙입니다.

```text
seat-hold-events.DLT
```

DLT에 쌓인 메시지를 재처리하려면 별도 DLT consumer나 운영 도구가 필요합니다.

## Redis

Redis에는 결제 완료 좌석만 기록합니다. 좌석 선점 여부의 최종 기준은 PostgreSQL의 `reservation_seats`입니다.

Key:

```text
sold:schedule:{scheduleId}:seat:{seatId}
```

Value:

```text
reservationId
```

TTL:

```text
없음
```

Redis 갱신은 DB 트랜잭션 커밋 이후 `afterCommit` 콜백에서 수행합니다. DB는 확정됐지만 Redis 갱신 전에 장애가 난 경우를 보정하기 위해, 이미 `CONFIRMED`인 예약의 결제 웹훅이 다시 들어오면 Redis sold key를 다시 기록합니다.

## 예약 상태

```text
PAYMENT_PENDING
CONFIRMED
CANCELLED
EXPIRED
```

상태 전이:

```text
좌석 선점 확정 이벤트
  -> PAYMENT_PENDING

결제 웹훅 PAID
  -> PAYMENT_PENDING -> CONFIRMED

사용자 취소
  -> PAYMENT_PENDING -> CANCELLED

결제 기한 만료
  -> PAYMENT_PENDING -> EXPIRED
```

현재 사용자 취소는 `PAYMENT_PENDING` 상태만 허용합니다. `CONFIRMED` 취소는 환불/결제 취소 정책이 필요하므로 별도 흐름으로 분리할 예정입니다.

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

`status = PAID`일 때만 예약 확정을 시도합니다. 다른 상태는 정상 수신 후 무시합니다.

### 예약 취소

```http
POST /reservations/{reservationId}/cancel
X-Authenticated-User-Id: user-1
```

본인의 `PAYMENT_PENDING` 예약만 취소할 수 있습니다.

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
reservation_seats
processed_kafka_events
reservation_status_histories
```

`reservations`는 예약 헤더입니다.

```text
reservation_id: 외부 API에서 사용하는 예약 UUID
confirmation_id: 좌석 선점 서비스가 넘긴 holdId
schedule_id: 공연/회차 식별자
user_id: 예약 사용자
status: 예약 상태
payment_expires_at: 결제 가능 만료 시각
```

`reservation_seats`는 예약에 포함된 좌석 목록입니다.

```text
reservation_id
schedule_id
seat_id
status
```

활성 좌석 중복 방어:

```sql
CREATE UNIQUE INDEX uq_reservation_seats_active_schedule_seat
ON reservation_seats (schedule_id, seat_id)
WHERE status IN ('PAYMENT_PENDING', 'CONFIRMED');
```

같은 예약 안의 좌석 중복 방어:

```sql
CREATE UNIQUE INDEX uq_reservation_seats_reservation_seat
ON reservation_seats (reservation_id, seat_id);
```

`processed_kafka_events`는 Kafka 이벤트 idempotency를 위한 처리 이력입니다.

```text
event_id
event_type
topic
partition_no
offset_no
processed_at
```

## 정합성 전략

선검증:

```text
processed_kafka_events.event_id 존재 여부
reservations.confirmation_id 존재 여부
reservation_seats의 활성 schedule_id + seat_id 존재 여부
seatIds 공백/중복/최대 4개 검증
```

최종 DB 방어:

```text
processed_kafka_events.event_id primary key
reservations.confirmation_id unique
reservation_seats (reservation_id, seat_id) unique
reservation_seats (schedule_id, seat_id) partial unique
```

Kafka 중복 메시지 중 이미 정상 처리된 이벤트는 선조회 후 반환합니다. 좌석 충돌이나 잘못된 이벤트처럼 정상 처리할 수 없는 경우는 예외를 던지고, Kafka error handler의 retry 이후 DLT로 보냅니다.

결제 확정과 사용자 취소는 예약 row를 비관적 락으로 조회한 뒤 상태를 변경합니다. 같은 예약에 대한 동시 결제/취소 요청이 들어와도 한 트랜잭션씩 상태 전이가 처리됩니다.

## 만료 처리

결제 기한이 지난 `PAYMENT_PENDING` 예약은 스케줄러가 주기적으로 처리합니다. 여러 API 인스턴스를 띄우는 환경에서는 worker 역할의 인스턴스에서만 활성화합니다.

```properties
reservation.expiration-scheduler.enabled=false/true
reservation.expiration-scheduler.fixed-delay-ms=30000
```

worker 인스턴스에서는 환경변수로 활성화할 수 있습니다.

```properties
RESERVATION_EXPIRATION_SCHEDULER_ENABLED=true
```

한 번에 최대 100건을 조회합니다.

```text
status = PAYMENT_PENDING
payment_expires_at < now
order by payment_expires_at asc
limit 100
```

만료 조회를 위한 인덱스:

```sql
CREATE INDEX ix_reservations_payment_expiration
ON reservations (status, payment_expires_at);
```

만료 배치는 row-by-row 저장이 아니라 bulk SQL로 처리합니다.

```text
reservation_status_histories INSERT INTO ... SELECT
reservation_seats bulk update
reservations bulk update
```

여러 인스턴스에서 동시에 스케줄러가 실행되는 운영 환경에서는 같은 만료 row를 동시에 집지 않도록 `FOR UPDATE SKIP LOCKED` 방식의 확장을 고려할 수 있습니다.
