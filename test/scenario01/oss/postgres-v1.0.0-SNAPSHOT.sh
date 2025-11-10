#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$(dirname "$(dirname "$SCRIPT_DIR")")")"
mvn -f "$ROOT_DIR/pom.xml" clean install -DskipTests
time mvn -f "$ROOT_DIR/start/pom.xml" compile exec:java@performance-test -Dexec.args="tool=jobrunr datastore=PostgresDataStore scenario=Scenario01ProcessJobs amount=20_000 dashboard_port=8010"