## Project for testing JobRunr Performance

### Prerequisites
- Java 17
- A blank running Postgres DB on port 5432

### How to run
- Start Postgres: for this test, as a database, we use PostgresDB. To start it with docker, run the following command:
`docker run -p 5432:5432 -e POSTGRES_PASSWORD=postgres -d postgres -c "shared_preload_libraries=pg_stat_statements"`
- Run the `main` method from the Main class or use `mvn compile exec:java`.

This test was made as simple as possible and does not contain any other framework. This is so the test can be done in isolation.

### Results / Questions
- Why is there a difference between JobRunr and JobRunr Pro?
- What can be improved?

#### TODO
- Testing should also be done against other databases (MongoDB / Redis / ...)