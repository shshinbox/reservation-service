# Reservation Service

Kafka 이벤트를 소비해 예약과 결제(mock)를 진행합니다.

## 기술 스택

- Java 21
- Spring Boot
- Spring MVC
- Spring Data JPA
- PostgreSQL
- Flyway
- Spring Kafka Consumer
- Spring Data Redis
- Maven

## 기술 선택 사유

### PostgreSQL을 기준 저장소로 사용

- 예약과 좌석을 같은 트랜잭션에서 저장합니다.
- 활성 좌석 중복은 PostgreSQL의 partial unique index로 방어합니다.
- 상태 변경 이력은 별도 테이블에 남깁니다.

여기서 partial unique index는 `schedule_id`, `seat_id`를 기준으로 잡고, 활성 예약 상태에만 적용합니다.

```sql
CREATE UNIQUE INDEX uq_reservation_seats_active_schedule_seat
ON reservation_seats (schedule_id, seat_id)
WHERE status IN ('HOLD', 'RESERVED')
```

### Kafka Consumer로 예약 생성

예약 생성은 사용자의 좌석 선점 확정 이후에 시작됩니다. 이 서비스는 `seat-hold-events` 토픽의 이벤트를 입력으로 받습니다.

- 이벤트를 소비해 `PAYMENT_PENDING` 예약을 생성합니다.
- `eventId`, `confirmationId`로 중복 처리를 막습니다.
- 처리 실패 메시지는 재시도 후 `.DLT` 토픽으로 보냅니다.

### Redis를 판매 좌석 캐시로 사용

판매 완료 여부는 좌석 선택 단계에서 자주 조회되는 값입니다. 매번 예약 DB를 조회하지 않도록 결제 확정된 좌석만 Redis에 `sold` key로 기록합니다.

- DB 커밋 이후 Redis를 갱신합니다.

### 만료 처리는 스케줄러로 처리

예약이 생성된 뒤 결제가 끝나지 않으면 좌석을 계속 점유하면 안 됩니다. 그래서 `PAYMENT_PENDING` 예약은 3일 후 만료 처리합니다.

현재는 같은 애플리케이션 안에 스케줄러를 두었지만, 만료 처리는 추후 별도 worker로 분리할 요소입니다. 

worker 인스턴스에만 `RESERVATION_EXPIRATION_SCHEDULER_ENABLED=true`를 설정하여 스케줄러를 활성화합니다.

## 정책

- `SEAT_HOLD_CONFIRMED` 이벤트를 소비하면 `PAYMENT_PENDING` 예약을 생성합니다.
- 한 예약은 최대 4개 좌석까지 가질 수 있습니다.
- `seatIds`는 공백과 중복을 허용하지 않습니다.
- 결제 기한은 `occurredAt + 3일`입니다.
- 스케줄 예약 가능 여부는 `now` 기준으로 확인합니다.
- 예약 생성 시 `READY` 상태값의 결제 row를 함께 생성합니다.
- PG는 `Mock`만 존재합니다.
- 현재 사용자는 본인의 `PAYMENT_PENDING` 예약만 취소할 수 있습니다.
- 만료된 `PAYMENT_PENDING` 예약은 `EXPIRED`로 전환합니다.
- 사용자 식별자는 `X-Authenticated-User-Id` 헤더에서 추출합니다.

## 인증 사용자

사용자 예약 조회와 취소 API는 다음 헤더가 필요합니다.

```http
X-Authenticated-User-Id: user-1
```

없거나 blank이면 `401 Unauthorized`를 반환합니다.


## Kafka 이벤트

소비 토픽:

```text
seat-hold-events
```

payload:

```json
{
  "eventId": "event-uuid",
  "eventType": "SEAT_HOLD_CONFIRMED",
  "holdId": "confirmation-uuid",
  "scheduleId": "schedule-1",
  "seatIds": ["A-1", "A-2"],
  "userId": "user-1",
  "expiresAt": null,
  "occurredAt": "2026-06-23T12:00:00Z",
  "schemaVersion": 1
}
```

검증:

- `eventId`, `holdId`, `scheduleId`, `userId`, `occurredAt` 필수
- `seatIds`는 1개 이상 4개 이하

처리 실패 메시지는 재시도 후 DLT로 이동합니다.

```text
seat-hold-events.DLT
```

## Redis 키

결제 완료 좌석만 기록합니다.

```text
sold:schedule:{scheduleId}:seat:{seatId}
```

- 값은 `reservationId`
- DB 커밋 전에 먼저 기록하고, DB 롤백 시 삭제

### Redis sold 동기화 정책

결제 확정 시 Redis에 판매 완료 좌석을 먼저 기록한 뒤 DB 커밋을 진행합니다.

```text
markSold
  -> 실패 시 즉시 3회 재시도
  -> 그래도 실패하면 DB의 RESERVED 좌석 기준으로 해당 schedule sold key 재구성
  -> 재구성까지 실패하면 결제 확정 흐름 실패 처리
  -> 이후 DB 커밋 실패 시 현재 예약 좌석의 sold key 보상 삭제
```


## 예약 생성 흐름

```text
Kafka seat-hold-events
  -> SeatHoldEventConsumer.consume
  -> ReservationCreationService.createPending
      1. 이벤트 검증
      2. 스케줄 예약 가능 여부 확인
      3. 중복 이벤트 확인
      4. 중복 예약 확인
      5. 활성 좌석 충돌 확인
      6. PAYMENT_PENDING 예약 생성
      7. 예약 좌석 생성
      8. READY 결제 생성
      9. 상태 이력 저장
```

좌석 충돌이나 잘못된 이벤트는 retry/DLT 흐름으로 넘깁니다.

## 결제 시작 흐름

```text
POST /api/payments
  -> 예약 소유자 확인
  -> 스케줄 예약 가능 여부 확인
  -> READY payment 조회
  -> orderId 생성
  -> Mock PG 결제 요청
  -> REQUESTED 전환
```

## 결제 확정 흐름

```text
POST /api/payments/webhooks/mock
  -> X-Signature 검증
  -> orderId로 payment 조회
  -> pgPaymentKey로 Mock PG 결제 조회
  -> 결제 정보 검증
  -> payment APPROVED 전환
  -> 예약과 좌석 확정
  -> Redis sold key 기록
  -> DB commit 실패 시 Redis sold key 보상 삭제
```

결제 기한이 지났으면 예약과 좌석을 `EXPIRED`로 전환하고 `409 Conflict`를 반환합니다.

## 예약 취소 흐름

```text
POST /reservations/{reservationId}/cancel
  -> 사용자 헤더 추출
  -> 예약 row for update 조회
  -> 소유자 검증
  -> PAYMENT_PENDING이면 CANCELLED 전환
  -> 상태 이력 저장
```

`CONFIRMED` 취소는 현재 지원하지 않습니다.

취소가 완료되면 Redis의 선점 키를 삭제합니다.

```text
user:holds:{scheduleId}:{userId}
seat:hold:{scheduleId}:{seatId}
```

## 만료 처리 흐름

```text
ReservationExpirationScheduler
  -> 만료된 PAYMENT_PENDING 예약 최대 100건 조회
  -> 상태 이력 bulk insert
  -> 예약 좌석 bulk update
  -> 예약 bulk update
```

추후 만료 처리 worker를 여러 대로 확장할 경우, `FOR UPDATE SKIP LOCKED` 방식으로 같은 예약을 중복 집계하지 않도록 개선할 수 있습니다.

설정:

```properties
reservation.expiration-scheduler.enabled=false
reservation.expiration-scheduler.fixed-delay-ms=30000
```

worker 인스턴스에서 활성화:

```properties
RESERVATION_EXPIRATION_SCHEDULER_ENABLED=true
```

## API

### 결제 시작

```http
POST /api/payments
X-Authenticated-User-Id: user-1
```

```json
{
  "reservationId": "reservation-uuid",
  "amount": 12000,
  "currency": "KRW",
  "pgProvider": "MOCK"
}
```

### 결제 웹훅

```http
POST /api/payments/webhooks/mock
X-Signature: hmac-sha256-hex
```

### 예약 취소

```http
POST /reservations/{reservationId}/cancel
X-Authenticated-User-Id: user-1
```


### 예약 상세 조회

```http
GET /reservations/{reservationId}
X-Authenticated-User-Id: user-1
```

### 판매 완료 좌석 확인

```http
GET /internal/schedules/{scheduleId}/seats/{seatId}/sold
```

DB의 `RESERVED` 좌석 기준으로 `sold` 여부를 반환합니다.

### 내 예약 목록 조회

```http
GET /me/reservations
X-Authenticated-User-Id: user-1
```

## 예약 상태

```text
PAYMENT_PENDING
CONFIRMED
CANCELLED
EXPIRED
```

상태 전이:

```text
SEAT_HOLD_CONFIRMED -> PAYMENT_PENDING
APPROVED payment -> CONFIRMED
사용자 취소 -> CANCELLED
결제 기한 만료 -> EXPIRED
```

## 예약 좌석 상태

```text
HOLD
RESERVED
CANCELLED
EXPIRED
```

## 결제 상태

```text
READY
REQUESTED
APPROVED
FAILED
CANCELLED
```
