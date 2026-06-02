# Reservation Service

`seat-holding-service`가 Kafka로 발행한 `SeatHeldEvent`를 소비해 RDBMS에 예약 초안을 저장하는 서비스입니다.

예약 충돌 기준은 **`scheduleId + seatId`** 입니다. 

## 목표

- Kafka `SeatHeldEvent`를 소비합니다.
- 선점된 좌석을 `HOLDING` 예약 초안으로 저장합니다.
- 같은 이벤트가 여러 번 전달되어도 한 번만 처리합니다.
- 같은 회차의 같은 좌석이 중복 예약되지 않도록 막습니다.
- 예약 확정 시 선점 만료 여부를 검사합니다.

## 기술 스택

- Java 21
- Spring Boot
- Spring MVC
- Spring Data JPA
- PostgreSQL
- Kafka
- Flyway
- Docker
- Maven

## 전체 흐름

```text
Client
  -> seat-holding-service에 좌석 선점 요청
  -> Redis hold 저장
  -> Kafka SeatHeldEvent 발행
  -> reservation-service Kafka consumer
  -> RDB transaction 시작
      1. eventId 중복 처리 여부 확인
      2. holdId 중복 예약 여부 확인
      3. scheduleId + seatId 중복 예약 여부 확인
      4. HOLDING 예약 초안 생성
      5. 처리된 event 기록
  -> transaction commit
  -> Kafka offset commit
```

## 선점 기준

좌석 선점과 예약의 충돌 기준은 다음입니다.

```text
scheduleId + seatId
```

`scheduleId`는 공연, 상영, 예약 가능한 시간대, 회차를 식별합니다.

```text
같은 scheduleId + 같은 seatId
  -> 중복 선점/예약 불가

다른 scheduleId + 같은 seatId
  -> 별도 선점/예약 가능
```

seat-holding-service의 Redis key도 다음 구조가 적합합니다.

```text
lock:schedule:{scheduleId}:seat:{seatId}
hold:schedule:{scheduleId}:seat:{seatId}
```

## Kafka 메시지

이 서비스가 소비하는 이벤트는 `SeatHeldEvent`입니다.

```json
{
  "eventId": "event-uuid",
  "eventType": "SEAT_HELD",
  "holdId": "hold-uuid",
  "scheduleId": "schedule-1",
  "venueId": "venue-1",
  "seatId": "A-12",
  "userId": "user-1",
  "holdExpiresAt": "2026-05-25T11:55:00Z",
  "occurredAt": "2026-05-25T11:50:00Z"
}
```

필드 의미:

```text
eventId
  Kafka 이벤트 고유 ID
  중복 소비 방지에 사용

eventType
  이벤트 종류
  예: SEAT_HELD

holdId
  좌석 선점 ID
  예약 초안과 선점 상태를 연결하는 키

scheduleId
  공연/상영/예약 시간대 식별자
  예약 충돌 판단의 핵심 키

venueId
  장소 ID

seatId
  좌석 ID

userId
  선점한 사용자 ID

holdExpiresAt
  사용자가 결제 전까지 좌석을 잡아둘 수 있는 선점 만료 시간

occurredAt
  이벤트 발생 시간
```

`holdExpiresAt`은 공연 시간이나 예약 이용 시간이 아니라 **선점 만료 시간**입니다.

## 예약 상태

```text
HOLDING
CONFIRMED
CANCELLED
EXPIRED
```

`SeatHeldEvent`를 소비하면 예약 초안은 `HOLDING` 상태로 생성됩니다.

```text
SeatHeldEvent
  -> reservation.status = HOLDING
  -> reservation.holdId = event.holdId
  -> reservation.scheduleId = event.scheduleId
  -> reservation.seatId = event.seatId
  -> reservation.holdExpiresAt = event.holdExpiresAt
```

사용자가 결제 또는 예약 확정 액션을 수행하면 상태를 변경합니다.

```text
HOLDING -> CONFIRMED
```

확정 시점에 `holdExpiresAt`이 지났다면 확정하면 안 됩니다.

```text
HOLDING -> EXPIRED
```

## RDB 저장 구조

예약 테이블:

```text
reservations
  id
  reservation_id
  hold_id
  schedule_id
  venue_id
  seat_id
  user_id
  status
  hold_expires_at
  confirmed_at
  cancelled_at
  expired_at
  created_at
  updated_at
```

처리 이벤트 테이블:

```text
processed_kafka_events
  event_id
  event_type
  topic
  partition_no
  offset_no
  processed_at
```

상태 변경 이력 테이블:

```text
reservation_status_histories
  id
  reservation_id
  from_status
  to_status
  reason
  changed_at
```

## 정합성 전략

Kafka는 at-least-once 방식으로 같은 메시지를 두 번 이상 전달할 수 있습니다.

필수 제약:

```text
reservations.hold_id UNIQUE
processed_kafka_events.event_id UNIQUE
```

활성 예약 중복 방지:

```sql
CREATE UNIQUE INDEX uq_reservations_active_schedule_seat
ON reservations (schedule_id, seat_id)
WHERE status IN ('HOLDING', 'CONFIRMED');
```

이 제약은 다음을 보장합니다.

```text
HOLDING / CONFIRMED 상태:
  같은 scheduleId + seatId 중복 불가

EXPIRED / CANCELLED 상태:
  같은 scheduleId + seatId 재예약 가능
```

## Consumer 처리 원칙

```text
정상 처리:
  RDB commit
  Kafka offset commit

중복 이벤트:
  이미 처리된 것으로 판단
  offset commit

일시적 장애:
  retry

계속 실패:
  dead letter topic으로 이동
```

Kafka offset은 RDB transaction이 성공한 뒤 commit해야 합니다.

현재 consumer 저장 순서:

```text
1. SeatHeldEvent 필수 필드 검증
2. processed_kafka_events에 eventId 저장
3. holdId로 기존 예약 초안 존재 여부 확인
4. 없으면 reservations에 HOLDING 예약 생성
5. reservation_status_histories에 상태 이력 저장
6. transaction commit
7. Kafka acknowledgment
```

중복 처리 정책:

```text
eventId 중복:
  이미 처리된 이벤트로 간주
  acknowledgment

holdId 중복:
  이미 예약 초안이 생성된 hold로 간주
  acknowledgment

scheduleId + seatId 활성 예약 충돌:
  정합성 위반으로 간주
  retry 이후 DLT로 이동
```

DLT topic:

```text
{source-topic}.DLT
```

예:

```text
seat-held-events.DLT
```

## 만료 처리

좌석 hold의 원천 상태는 seat-holding-service의 Redis TTL입니다.

reservation-service도 `holdExpiresAt`을 저장하므로 만료된 예약 초안을 자체적으로 정리해야 합니다.

```text
holdExpiresAt이 지난 HOLDING 예약
  -> EXPIRED 처리
```

만료 처리는 두 방식으로 보완합니다.

```text
Scheduler:
  주기적으로 만료된 HOLDING 예약을 EXPIRED로 변경

예약 확정 API:
  확정 요청 시 holdExpiresAt을 다시 확인
```

## API

### 예약 확정

```http
POST /reservations/{reservationId}/confirm
X-Authenticated-User-Id: user-1
```

성공 시:

```text
HOLDING -> CONFIRMED
```

만료된 경우:

```text
HOLDING -> EXPIRED
409 Conflict
```

### 예약 취소

```http
POST /reservations/{reservationId}/cancel
X-Authenticated-User-Id: user-1
```

성공 시:

```text
HOLDING -> CANCELLED
```

`confirm`, `cancel` API는 API Gateway가 검증한 사용자 ID를 `X-Authenticated-User-Id` 헤더로 전달한다고 가정합니다.

```text
reservation.userId != X-Authenticated-User-Id
  -> 403 Forbidden

X-Authenticated-User-Id 누락
  -> 401 Unauthorized
```

### 예약 단건 조회

```http
GET /reservations/{reservationId}
```

### 사용자 예약 목록 조회

```http
GET /users/{userId}/reservations
```
