package com.ticketing.repository;

import com.ticketing.entity.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByOwnerId(Long ownerId);

    boolean existsByOwnerIdAndTitleAndVenueAndStartsAt(Long ownerId, String title, String venue, Instant startsAt);

    boolean existsByOwnerIdAndTitleAndVenueAndStartsAtAndIdNot(Long ownerId, String title, String venue, Instant startsAt, Long id);

    @Query("""
                select e from Event e
                where e.published = true
                  and (:from is null or e.startsAt >= :from)
                  and (:to is null or e.startsAt <= :to)
                  and (:q is null
                       or lower(e.title) like lower(concat('%', :q, '%'))
                       or lower(e.venue) like lower(concat('%', :q, '%')))
                order by e.startsAt
            """)
    List<Event> searchPublished(@Param("from") Instant from, @Param("to") Instant to, @Param("q") String query);

    /**
     * Loads a published event and locks its row until the current transaction ends,
     * so reservations for the same event are handled one at a time.
     * Must be called inside a transaction.
     */
    @Query("""
            SELECT e from Event e
            where e.published = true
            and e.id = :eventId
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Event> findPublishedForUpdate(@Param("eventId") Long eventId);

    /**
     * Same lock as {@link #findPublishedForUpdate}, but also finds draft events.
     * Used when the capacity changes, so it cannot race with new reservations.
     */
    @Query("select e from Event e where e.id = :eventId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Event> findByIdForUpdate(@Param("eventId") Long eventId);
}
