package me.songha.concert.reservation.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public class ReservationDuplicateMessageResolver {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String PROCESSED_EVENT_PRIMARY_KEY = "processed_kafka_events_pkey";
    private static final String RESERVATION_HOLD_UNIQUE_KEY = "reservations_hold_id_key";

    public boolean isAlreadyProcessed(DataIntegrityViolationException exception) {
        SQLException sqlException = findSqlException(exception);
        if (sqlException == null || !UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
            return false;
        }

        String message = sqlException.getMessage();
        return message != null
                && (message.contains(PROCESSED_EVENT_PRIMARY_KEY) || message.contains(RESERVATION_HOLD_UNIQUE_KEY));
    }

    private SQLException findSqlException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        return null;
    }
}
