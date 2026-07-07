package me.songha.concert.payment.dto;

public record PaymentReservationConfirmResult(
        boolean completed,
        String message
) {

    public static PaymentReservationConfirmResult success() {
        return new PaymentReservationConfirmResult(true, null);
    }

    public static PaymentReservationConfirmResult rejected(String message) {
        return new PaymentReservationConfirmResult(false, message);
    }
}
