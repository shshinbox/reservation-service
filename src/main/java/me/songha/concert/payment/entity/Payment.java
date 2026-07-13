package me.songha.concert.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true, length = 100, updatable = false)
    private UUID paymentId;

    @Column(name = "order_id", unique = true, length = 100)
    private String orderId;

    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "pg_provider", length = 50)
    private String pgProvider;

    @Column(name = "pg_payment_key", unique = true, length = 200)
    private String pgPaymentKey;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Payment(UUID reservationId, Instant now) {
        this.paymentId = UUID.randomUUID();
        this.reservationId = reservationId;
        this.status = PaymentStatus.READY;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markRequested(String orderId, String pgPaymentKey, Instant now) {
        if (status != PaymentStatus.READY) {
            throw new IllegalStateException("Only READY payments can be requested.");
        }
        this.orderId = orderId;
        this.status = PaymentStatus.REQUESTED;
        this.pgPaymentKey = pgPaymentKey;
        this.requestedAt = now;
        this.updatedAt = now;
    }

    public void updatePgPaymentKey(String pgPaymentKey, Instant now) {
        this.pgPaymentKey = pgPaymentKey;
        this.updatedAt = now;
    }

    public void updateReadyDetails(BigDecimal amount, String currency, String pgProvider, Instant now) {
        if (status != PaymentStatus.READY) {
            throw new IllegalStateException("Only READY payments can be updated before request.");
        }
        this.amount = amount;
        this.currency = currency;
        this.pgProvider = pgProvider;
        this.updatedAt = now;
    }

    public void approve(Instant approvedAt) {
        if (status == PaymentStatus.CANCELLED || status == PaymentStatus.EXPIRED) {
            throw new IllegalStateException("Terminal payments cannot be approved.");
        }
        this.status = PaymentStatus.APPROVED;
        this.approvedAt = approvedAt;
        this.updatedAt = approvedAt;
    }

    public void fail(String failureCode, String failureMessage, Instant failedAt) {
        this.status = PaymentStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.failedAt = failedAt;
        this.updatedAt = failedAt;
    }

    public void cancel(Instant cancelledAt) {
        if (status == PaymentStatus.APPROVED) {
            throw new IllegalStateException("Approved payments cannot be cancelled without refund.");
        }
        if (status == PaymentStatus.CANCELLED) {
            return;
        }
        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
        this.updatedAt = cancelledAt;
    }

    public void expire(Instant expiredAt) {
        if (status == PaymentStatus.APPROVED) {
            throw new IllegalStateException("Approved payments cannot be expired.");
        }
        if (status == PaymentStatus.EXPIRED) {
            return;
        }
        this.status = PaymentStatus.EXPIRED;
        this.expiredAt = expiredAt;
        this.updatedAt = expiredAt;
    }
}
