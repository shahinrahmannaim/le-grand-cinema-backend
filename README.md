# Le Grand Cinema API foundation

Java 21, Spring Boot 4.1.2, Maven Wrapper and PostgreSQL 17.

Copy .env.example to .env, start Docker Compose, and set DB_PASSWORD in IntelliJ.
Run CinemaBackendApplication. Flyway creates the schema and 1700 seats.

The catalogue and screening API will be developed on the US01 and US02 feature branches.
No authentication or reservation endpoints are implemented yet.

main: releases; develop: integration; feature/usXX-description: Trello story; fix/description: bug fix.
Open pull requests into develop. Do not claim a PR or deployment before it exists.
