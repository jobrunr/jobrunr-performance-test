docker stop postgres-performance
docker rm postgres-performance
docker run --name postgres-performance -p 5432:5432 -e POSTGRES_PASSWORD=postgres -d postgres -c "shared_preload_libraries=pg_stat_statements"
time mvn compile exec:java