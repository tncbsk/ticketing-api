# Ticketing & Reservation API

Event and seat reservation API. Organizers create and publish events, customers reserve seats.

Spring Boot 4, Java 21, H2 (in-memory), Spring Security with JWT.

## Running

Requires Java 21. The Maven wrapper downloads Maven itself.

```
./mvnw spring-boot:run
```

On Windows use `mvnw.cmd spring-boot:run`.

| | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI spec | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| H2 console | http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:ticketing`, user `sa`, empty password) |

The database is in memory, so data is lost on restart. Seed users are created on every start.

The JWT signing key comes from the `JWT_SECRET` environment variable; without it a development key from `application.yml` is used.

## Seed users

| Role | Email | Password |
|---|---|---|
| ADMIN | admin@example.com | admin123 |
| ORGANIZER | organizer@example.com | organizer123 |
| CUSTOMER | customer@example.com | customer123 |

## Auth flow

1. `POST /api/auth/register` creates a user. Role is `CUSTOMER` by default, `ORGANIZER` can be requested, `ADMIN` cannot.
2. `POST /api/auth/login` returns an access token (15 minutes) and a refresh token (7 days).
3. Send the access token as `Authorization: Bearer <token>`. In Swagger, use the Authorize button.
4. `POST /api/auth/refresh` with the refresh token returns a new pair. An access token is rejected here, and a refresh token is rejected on the other endpoints.

Example login response:

```json
{
  "accessToken": "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIzIiwiZW1haWwiOiJjdXN0b21lckBleGFtcGxlLmNvbSIsInJvbGVzIjpbIkNVU1RPTUVSIl0sInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3OTUwMDUxNDEsImV4cCI6MTc5NTAwNjA0MX0.cWjCDDgu0XYPTaYGfkDSkF3oRlT43sEDZ-OKDgjB3Z_puX5pS1FXJ_eG-J2QZ6Qm",
  "refreshToken": "eyJhbGciOiJIUzM4NCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

Decoded access token payload:

```json
{
  "sub": "3",
  "email": "customer@example.com",
  "roles": ["CUSTOMER"],
  "type": "access",
  "iat": 1795005141,
  "exp": 1795006041
}
```

The refresh token only carries `sub` and `type: refresh`; roles are read from the database again when it is used.

## Endpoints

| Method | Path | Who |
|---|---|---|
| POST | /api/auth/register | anyone |
| POST | /api/auth/login | anyone |
| POST | /api/auth/refresh | anyone |
| POST | /api/events | organizer, admin |
| PUT | /api/events/{id} | owner, admin |
| POST | /api/events/{id}/publish | owner, admin |
| GET | /api/events?ownerId= | logged in (non-admins only see their own events) |
| GET | /api/events/public?from=&to=&q= | anyone |
| POST | /api/events/{id}/reservations | customer, admin (requires `Idempotency-Key` header) |
| POST | /api/reservations/{id}/confirm | reservation owner, admin |
| POST | /api/reservations/{id}/cancel | reservation owner, admin |

Errors use the same body, including malformed JSON, wrong parameter types and unexpected server errors (only a missing or invalid token returns a bare 401):

```json
{"timestamp": "...", "status": 409, "error": "Conflict", "message": "Not enough seats available", "path": "/api/events/1/reservations"}
```

## Design decisions

**No overselling.** Creating a reservation locks the event row (`SELECT ... FOR UPDATE`), sums the PENDING and CONFIRMED seats and only then saves. Requests for the same event are processed one at a time; requests for different events do not wait for each other. I chose a pessimistic lock because competing for the last seats means frequent conflicts: with optimistic locking many requests would fail even when seats are left and clients would have to retry. Updating an event takes the same lock, so capacity cannot be lowered below the reserved seats while bookings come in.

**Optimistic locking for event updates and reservation status changes.** `Event` and `Reservation` have a `@Version`. Two concurrent changes of the same row are rare, so the second one simply gets 409. For example, a confirm and a cancel arriving at the same moment for the same reservation cannot both succeed.

**Idempotency.** The key, request hash and resulting reservation id are stored in the same transaction as the reservation, so either both are saved or neither is. The event lock is taken before the key lookup; a second request with the same key waits for the first one and then returns the same reservation instead of failing on the unique constraint. Keys are scoped to the user and endpoint and expire after 24 hours. I did not use a two-phase approach (store the key as IN_PROGRESS first) because there are no external calls in the flow; it would mainly add stuck-key handling.

**No duplicate events.** An organizer cannot have two events with the same title, venue and start time; another show of the same concert at a different time is fine. The service checks this to return a clear 409, and a unique constraint on the table catches two identical requests arriving at the same moment (without it, a test with 10 parallel requests created up to 5 copies).

**Seats are counted, not stored.** Reserved seats are summed from the reservations table instead of keeping a counter on the event. A cancelled reservation frees its seats automatically. PENDING reservations hold seats.

**Entities reference each other by id.** `Reservation.eventId`, `Event.ownerId` and so on are plain ids instead of `@ManyToOne` relations, which keeps the modules loosely coupled.

**Timestamps are `Instant`.** Dates are stored and returned in UTC. An earlier version used `LocalDateTime`, which silently dropped the `Z` from requests and shifted times by the server's offset.

**Security layers.** The filter chain authenticates the JWT, `@PreAuthorize` checks roles and the services check ownership. Passwords are hashed with BCrypt. Login, register and refresh are rate limited per IP (10 requests per minute, fixed window) before the request reaches JWT parsing and password checks.

**Audit log.** Logins (successful and failed), registration and every change to events and reservations are recorded with actor, resource, IP and user agent. A failed login is written in its own transaction (`REQUIRES_NEW`), otherwise it would be rolled back together with the login.

## Assumptions

The case leaves these open, so I picked a behaviour:

- Dates must include a time zone, e.g. `2027-01-01T10:00:00Z`. Without it the request is rejected with 400.
- `Idempotency-Key` must be 1-100 characters; a UUID is a good choice. Reusing a key with a different body returns 400.
- Reservations, confirmations and cancellations are not allowed once the event has started.
- Only pending reservations can be confirmed; pending and confirmed ones can be cancelled.
- Organizers cannot make reservations. Customers cannot create events.
- Organizers can only list their own events; asking for another owner's events returns 403.
- Publishing an already published event is not an error.
- Reservations for draft events return 404, so drafts stay hidden from customers.
- The case lists no delete or get-by-id endpoints, so events are read through the list and public search endpoints and are never deleted.

## Tests

```
./mvnw test
```

- Unit tests (Mockito) for reservation, event and auth services: capacity limits, state changes, ownership and token rules.
- Integration tests on H2, including concurrency tests: 50 parallel requests for 10 seats create exactly 10 reservations, parallel requests with the same idempotency key create one reservation, lowering capacity while bookings come in never overbooks, and a parallel confirm and cancel of the same reservation never both succeed.
- Security tests with MockMvc for role and ownership checks, plus rate limiting and audit logging.

42 tests. Line coverage is 88% overall and 91% on the service and security classes, measured with JaCoCo:

```
./mvnw clean org.jacoco:jacoco-maven-plugin:0.8.16:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.16:report
```

The report is written to `target/site/jacoco/index.html`.

## Known limitations

- H2 only. For production I would use PostgreSQL with Flyway migrations instead of `ddl-auto: update`, and run the concurrency tests against it with Testcontainers.
- Rate limit counters are kept in memory per instance. With several instances this should move to Redis or an API gateway.
- Refresh tokens cannot be revoked. Storing and rotating them would fix that.
- Unconfirmed PENDING reservations hold seats forever; a scheduled job should expire them.
- Expired idempotency keys are only removed when the same key is used again.
- No pagination on list endpoints.
