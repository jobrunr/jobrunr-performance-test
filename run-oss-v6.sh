/bin/bash ./run-db.sh

time mvn compile exec:java -Dexec.args="amount=500_000" -P OSS-v6
