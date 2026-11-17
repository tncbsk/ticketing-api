# Ticketing & Reservation API

Spring Boot 4 / Java 21. H2 in-memory database.

## Run

```
./mvnw spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec: http://localhost:8080/v3/api-docs
- H2 console: http://localhost:8080/h2-console (JDBC url `jdbc:h2:mem:ticketing`, user `sa`, no password)
- Health: http://localhost:8080/actuator/health

## Auth

Login with `POST /api/auth/login`, copy the `accessToken` and use the Authorize button in Swagger.
Access token lives 15 minutes, refresh token 7 days (`POST /api/auth/refresh`).

Seed users are created on startup, see `DataSeeder`.

| Role      | Email                     |
|-----------|---------------------------|
| ADMIN     | admin@ticketing.local     |
| ORGANIZER | organizer@ticketing.local |
| CUSTOMER  | customer@ticketing.local  |

## Status

Auth, event endpoints and reservation creation are implemented. Confirm and cancel are still stubs.
