#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$(dirname "$(dirname "$SCRIPT_DIR")")")"

time mvn -f "$ROOT_DIR/pom.xml" compile exec:java@performance-test -P PRO -Dexec.args="datastore=PostgresDataStore scenario=Scenario04BatchJobs log_storage_provider_timings=true amount=500_000 dashboard_port=8010"