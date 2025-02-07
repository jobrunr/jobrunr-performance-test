#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$(dirname "$(dirname "$SCRIPT_DIR")")")"

time mvn -f "$ROOT_DIR/pom.xml" compile exec:java@performance-test -P PRO -Djobrunr.version=7.4.0 -Dexec.args="jvm-per-scenario=true datastore=allButSlow scenario=Scenario01ProcessJobs amount=500_000 dashboard_port=8010"