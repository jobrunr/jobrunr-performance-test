docker stop postgres-performance
docker rm postgres-performance
docker run --name postgres-performance -p 5432:5432 -e POSTGRES_PASSWORD=postgres --shm-size=1g -d postgres -c "shared_preload_libraries=pg_stat_statements"
sleep 1
time mvn compile exec:java -Dexec.args="amount=500_000 jobRunrProSourceDir=../../JobRunrPro"