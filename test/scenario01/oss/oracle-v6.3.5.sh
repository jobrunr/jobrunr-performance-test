#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$(dirname "$(dirname "$SCRIPT_DIR")")")"

time mvn -f "$ROOT_DIR/pom.xml" compile exec:java@performance-test -P OSS -Djobrunr.version=6.3.5 -Dexec.args="datastore=OracleDataStore scenario=Scenario01ProcessJobs amount=500_000 dashboard_port=8010"