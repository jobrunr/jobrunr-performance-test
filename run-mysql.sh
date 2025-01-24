docker rm -f mysql-performance
docker volume prune -f
docker run --name mysql-performance -p 3306:3306 -e MYSQL_ROOT_PASSWORD=aavGaROcfd156FWfoV62LAul -e MYSQL_DATABASE=mysql --shm-size=1g -d mysql:latest
sleep 2

