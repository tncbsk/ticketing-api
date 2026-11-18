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
    List<Event> searchPublishedEventWithParams(@Param("from") Instant from, @Param("to") Instant to, @Param("q") String query);

    @Query("""
            SELECT e from Event e
            where e.published = true
            and e.id = :eventId
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Event> findPublishedForUpdate(@Param("eventId") Long eventId);

    @Query("select e from Event e where e.id = :eventId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Event> findByIdForUpdate(@Param("eventId") Long eventId);
}
