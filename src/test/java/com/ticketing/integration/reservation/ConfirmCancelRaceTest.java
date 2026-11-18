package com.ticketing.integration.reservation;

import com.ticketing.dto.reservation.ReservationRequest;
import com.ticketing.entity.Reservation;
import com.ticketing.entity.ReservationStatus;
import com.ticketing.exception.ApiException;
import com.ticketing.repository.ReservationRepository;
import com.ticketing.security.AuthUser;
import com.ticketing.service.ReservationService;
import com.ticketing.support.Concurrently;
import com.ticketing.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfirmCancelRaceTest extends IntegrationTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void staleReservationCannotOverwriteANewerChange() {
        Long reservationId = newReservation();
        Reservation stale = reservationRepository.findById(reservationId).orElseThrow();

        reservationService.confirm(reservationId, customer());
        stale.setStatus(ReservationStatus.CANCELLED);

        assertThatThrownBy(() -> reservationRepository.save(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void parallelConfirmAndCancelNeverBothSucceed() throws Exception {
        AuthUser customer = customer();

        for (int round = 0; round < 20; round++) {
            Long reservationId = newReservation();

            List<Throwable> errors = Concurrently.run(2, i -> {
                if (i == 0) {
                    reservationService.confirm(reservationId, customer);
                } else {
                    reservationService.cancel(reservationId, customer);
                }
            });

            // the loser either sees the new status (409) or fails on the version check
            assertThat(errors).hasSize(1).allSatisfy(error -> assertThat(error)
                    .isInstanceOfAny(ApiException.class, ObjectOptimisticLockingFailureException.class));
        }
    }

    private Long newReservation() {
        Long eventId = createPublishedEvent(5);
        return reservationService.create(eventId, new ReservationRequest(1), UUID.randomUUID().toString(), customer()).id();
    }
}
