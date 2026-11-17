package com.ticketing.repository;

import com.ticketing.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByEventId(Long eventId);
    @Query("""
            Select coalesce(sum(r.seats), 0) FROM Reservation r
            where r.status in (com.ticketing.entity.ReservationStatus.PENDING , com.ticketing.entity.ReservationStatus.CONFIRMED)
            and r.eventId = :eventId
            """)
    Long totalEventReservation(@Param("eventId") Long eventId);
}
