docker rm -f postgres-pghero
docker rm -f postgres-performance
docker volume prune -f
# docker run --name postgres-performance -p 5432:5432 -v "/home/dev/jobrunr/jobrunr-pro-performance-test/my-postgres.conf":/etc/postgresql/postgresql.conf -e POSTGRES_PASSWORD=andrien09 --shm-size=1g -d postgres -c "config_file=/etc/postgresql/postgresql.conf"
docker run --name postgres-performance -p 5432:5432 -e POSTGRES_PASSWORD=oTsMa6h61BOFYTpIVvLs --shm-size=1g -d postgres -c "shared_preload_libraries=pg_stat_statements"
docker run --name postgres-pghero -e DATABASE_URL=postgres://postgres:oTsMa6h61BOFYTpIVvLs@host.docker.internal:5432/postgres -p 8090:8080 -d ankane/pghero
sleep 1

