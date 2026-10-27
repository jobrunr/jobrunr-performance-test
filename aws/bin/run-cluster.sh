#!/usr/bin/env bash
# Usage: aws/bin/run-cluster.sh [scenario] [amount] [repetitions] [key=value]...
#   aws/bin/run-cluster.sh Scenario01ProcessJobs 2000000 3
#   JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75" EXECUTION_TIMEOUT_SECONDS=10800 aws/bin/run-cluster.sh Scenario01ProcessJobs 2000000 1 max_duration=PT2H
#
# Environment overrides:
#   START_DELAY_SECONDS        time the nodes get to start up before processing begins at start_at (default 120)
#   EXECUTION_TIMEOUT_SECONDS  SSM timeout per node, must exceed max_duration + START_DELAY_SECONDS (default 7200)
#   DIAGNOSTICS                true: record CPU, round trips, connection pool and database activity (default false)
#   PG_SETTINGS                db_type=ec2 only: Postgres settings for these runs on top of the DB machine's defaults,
#                              e.g. "shared_buffers=16GB max_wal_size=16GB checkpoint_timeout=15min" (no new machine)
#
# Results:
#   logbooks/aws/<scenario>-cluster-logbook.csv   (one row per run)
#   logbooks/aws/details/<run id>.md             (configuration, database log and diagnostics)
#   logbooks/aws/runs/<run id>/                   (logbook + logs of every node)
set -euo pipefail
# read fully before running, so edits don't affect a running benchmark
{

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCENARIO="${1:-Scenario01ProcessJobs}"
AMOUNT="${2:-2000000}"
REPETITIONS="${3:-1}"
shift $(($# < 3 ? $# : 3))
EXTRA_ARGS="$*"

IMAGE_TAG="${IMAGE_TAG:-$(cat "$ROOT_DIR/aws/.image-tag" 2>/dev/null || true)}"
: "${IMAGE_TAG:?No image tag: run aws/bin/build-and-push.sh first or set IMAGE_TAG}"
JAVA_OPTS="${JAVA_OPTS:--XX:MaxRAMPercentage=75}"
DIAGNOSTICS="${DIAGNOSTICS:-false}"
# default max_duration (1h) + 1h
EXECUTION_TIMEOUT_SECONDS="${EXECUTION_TIMEOUT_SECONDS:-7200}"
START_DELAY_SECONDS="${START_DELAY_SECONDS:-120}"

tf_out() { terraform -chdir="$ROOT_DIR/aws/terraform/cluster" output -raw "$1"; }
REGION="$(tf_out region)"
BUCKET="$(tf_out results_bucket)"
IMAGE="$(tf_out ecr_repository_url):$IMAGE_TAG"
LICENSE_PARAMETER="$(tf_out license_parameter_name)"
DB_TYPE="$(tf_out db_type)"
DB_HOST="$(tf_out db_host)"
DB_PASSWORD="$(tf_out db_password)"
DB_INSTANCE_TYPE="$(tf_out db_instance_type)"
DB_INSTANCE_ID="$(tf_out db_instance_id)"
NODE_INSTANCE_TYPE="$(tf_out node_instance_type)"
NODE_COUNT="$(tf_out node_count)"
NODE0_ID="$(tf_out node0_instance_id)"
read -r -a WORKER_IDS <<< "$(tf_out worker_instance_ids)"
NODE_IDS=("$NODE0_ID" ${WORKER_IDS[@]+"${WORKER_IDS[@]}"})
AWS=(aws --region "$REGION")

source "$ROOT_DIR/aws/bin/lib/common.sh"
source "$ROOT_DIR/aws/bin/lib/postgres.sh"
source "$ROOT_DIR/aws/bin/lib/postgres-diagnostics.sh"
source "$ROOT_DIR/aws/bin/lib/diagnostics.sh"

wait_until_nodes_are_ready() {
  local -a ids=("${NODE_IDS[@]}")
  [ "$DB_TYPE" != ec2 ] || ids+=("$DB_INSTANCE_ID")
  log "Waiting for ${#ids[@]} machine(s) to be registered with SSM"
  local online
  while true; do
    online=$("${AWS[@]}" ssm describe-instance-information --filters "Key=InstanceIds,Values=$(IFS=,; echo "${ids[*]}")" \
      --query "length(InstanceInformationList[?PingStatus=='Online'])" --output text)
    [ "$online" -eq "${#ids[@]}" ] && break
    sleep 10
  done
  log "Waiting for setup to finish and pulling $IMAGE"
  local command_id
  command_id=$(send_command "cloud-init status --wait > /dev/null && aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin ${IMAGE%%/*} && docker pull -q $IMAGE && docker pull -q $DB_CLIENT_IMAGE" 900 "${NODE_IDS[@]}")
  for id in "${NODE_IDS[@]}"; do wait_for_command "$command_id" "$id"; done
  if [ "$DB_TYPE" = ec2 ]; then
    command_id=$(send_command "cloud-init status --wait > /dev/null" 900 "$DB_INSTANCE_ID")
    wait_for_command "$command_id" "$DB_INSTANCE_ID"
  fi
}

# node_script <run id> <label> <args>
node_script() {
  local run_id="$1" label="$2" args="$3"
  cat <<EOF
set -euo pipefail
OUT=/var/lib/jobrunr-perf/runs/$run_id/$label
mkdir -p \$OUT/logbooks
until timeout 1 bash -c '</dev/tcp/$DB_HOST/$DB_PORT' 2>/dev/null; do sleep 2; done
export JOBRUNRPRO_LICENSE=\$(aws ssm get-parameter --region $REGION --name $LICENSE_PARAMETER --with-decryption --query Parameter.Value --output text)
$([ "$label" = loader ] || diagnostics_node_start_script)
STATUS=0
docker run --rm --network host -v \$OUT:/work \\
  -e JOBRUNRPRO_LICENSE -e DB_JDBC_URL=$DB_JDBC_URL -e DB_PASSWORD=$DB_PASSWORD \\
  -e CLUSTER_RUN_ID=$run_id -e CLUSTER_SIZE=$NODE_COUNT \\
  -e NODE_INSTANCE_TYPE=$NODE_INSTANCE_TYPE -e DB_INSTANCE_TYPE=$DB_INSTANCE_TYPE \\
  -e JAVA_OPTS="$JAVA_OPTS" \\
  $IMAGE tool=JobRunrPro scenario=$SCENARIO datastore=$DATASTORE amount=$AMOUNT \\
  system_exit=true $args $EXTRA_ARGS > \$OUT/stdout.log 2>&1 || STATUS=\$?
$([ "$label" = loader ] || diagnostics_node_stop_script)
aws s3 cp --quiet --recursive \$OUT s3://$BUCKET/runs/$run_id/$label/ --region $REGION
tail -5 \$OUT/stdout.log
exit \$STATUS
EOF
}

# profiles <run dir>
profiles() {
  local file found=""
  for file in "$1"/*/logbooks/async-profiler/*.jfr; do
    [ -f "$file" ] || continue
    found=1
    echo "- \`${file#"$ROOT_DIR/logbooks/aws/"}\` ($(du -h "$file" | awk '{ print $1 }'))"
  done
  [ -n "$found" ] || echo "Not profiled; to profile, pass enable_profiling=true."
}

# write_details <run id> <logbook row> <start spread> <end spread> <start_at> <last succeeded at millis>
write_details() {
  local run_id="$1" spread="$3" end_spread="$4" start_at="$5" last_succeeded_at="$6"
  local dir="$ROOT_DIR/logbooks/aws/runs/$run_id" field processing_end=$((start_at + 864000))
  [ "$last_succeeded_at" = n/a ] || processing_end=$((last_succeeded_at / 1000))
  IFS=, read -r -a field <<< "$2"
  mkdir -p "$ROOT_DIR/logbooks/aws/details"
  cat > "$ROOT_DIR/logbooks/aws/details/$run_id.md" <<EOF
# $SCENARIO run $run_id

- Throughput: ${field[10]} jobs / sec
- Processing duration: ${field[9]}, jobs succeeded: ${field[7]} / ${field[6]}
- Start spread: $spread ms, end spread: $end_spread ms
- ${field[3]} ${field[4]}, ${field[5]}

## Configuration

- Image tag: \`$IMAGE_TAG\`
- Nodes: $NODE_COUNT x $NODE_INSTANCE_TYPE, workers per node: $(grep -Eo 'and [0-9]+ BackgroundJobPerformers' "$dir/node-0/stdout.log" | awk '{ print $2; exit }')
- Database: $DB_INSTANCE_TYPE
- JAVA_OPTS: \`$JAVA_OPTS\`, extra args: \`${EXTRA_ARGS:-none}\`
$(db_configuration_details)

## Profiles

async-profiler (CPU, wall clock and lock samples) with JDK Flight Recorder's events in the same file (jfrsync).

$(profiles "$dir")

$(db_log_details "$dir" "$start_at" "$processing_end")

$(diagnostics_details "$dir" "$start_at" "$processing_end" "$last_succeeded_at" "${field[14]}")
EOF
}

run_once() {
  local repetition="$1"
  local run_id run_started_at
  run_started_at=$(date +%s)
  run_id="$(date -u +%Y%m%dT%H%M%SZ)-$SCENARIO-${NODE_COUNT}x$NODE_INSTANCE_TYPE-$repetition"
  log "Run $run_id: $AMOUNT jobs on $NODE_COUNT x $NODE_INSTANCE_TYPE, db $DB_INSTANCE_TYPE, image $IMAGE_TAG"

  db_configure || return 1
  db_reset || return 1

  log "Creating jobs"
  local loader_command
  loader_command=$(send_command "$(node_script "$run_id" loader cluster_role=loader)" "$EXECUTION_TIMEOUT_SECONDS" "$NODE0_ID")
  wait_for_command "$loader_command" "$NODE0_ID" || return 1

  diagnostics_before_processing "$run_id"

  local start_at=$(($(date +%s) + START_DELAY_SECONDS))
  log "Processing jobs on $NODE_COUNT node(s), starting in ${START_DELAY_SECONDS}s"
  local node0_command
  node0_command=$(send_command "$(node_script "$run_id" node-0 "cluster_role=worker start_at=$start_at")" "$EXECUTION_TIMEOUT_SECONDS" "$NODE0_ID")

  local -a worker_commands=()
  local node_index=0
  for id in ${WORKER_IDS[@]+"${WORKER_IDS[@]}"}; do
    node_index=$((node_index + 1))
    worker_commands+=("$(send_command "$(node_script "$run_id" "node-$node_index" "cluster_role=worker start_at=$start_at")" "$EXECUTION_TIMEOUT_SECONDS" "$id")")
  done

  local failed=0
  wait_for_command "$node0_command" "$NODE0_ID" || failed=1
  node_index=0
  for id in ${WORKER_IDS[@]+"${WORKER_IDS[@]}"}; do
    wait_for_command "${worker_commands[$node_index]}" "$id" || failed=1
    node_index=$((node_index + 1))
  done

  diagnostics_after_processing "$run_id"

  local local_dir="$ROOT_DIR/logbooks/aws/runs/$run_id"
  mkdir -p "$local_dir"
  "${AWS[@]}" s3 cp --quiet --recursive "s3://$BUCKET/runs/$run_id/" "$local_dir/"
  db_fetch_log "$run_id" "$run_started_at" "$local_dir/db/postgres.log"

  local csv
  csv=$(find "$local_dir/node-0/logbooks" -name '*-cluster-logbook.csv' 2>/dev/null | head -1)
  if [ -z "$csv" ]; then
    log "Run $run_id produced no logbook, see $local_dir"
    return 1
  fi
  local spread
  spread=$(grep -EHo 'First job started at epoch millis: [0-9]+' "$local_dir"/*/stdout.log \
    | awk -v nodes="$NODE_COUNT" '{ t = $NF; n++; if (n == 1 || t < min) min = t; if (n == 1 || t > max) max = t }
                                  END { if (n == nodes) printf "%d", max - min; else printf "n/a" }')
  [ "$spread" != "n/a" ] || log "Not every node reported when it started its first job, start spread is n/a"

  # the run ends when the last job of the cluster succeeded
  local last_succeeded_at end_spread
  # -1: the node processed no job
  read -r last_succeeded_at end_spread < <(grep -EHo 'Last job succeeded at epoch millis: -?[0-9]+' "$local_dir"/*/stdout.log \
    | awk -v nodes="$NODE_COUNT" '{ n++; t = $NF; if (t < 0) next; m++; if (m == 1 || t < min) min = t; if (m == 1 || t > max) max = t }
                                  END { if (n == nodes && m > 0) printf "%d %d\n", max, max - min; else print "n/a n/a" }')
  local row
  row=$(tail -1 "$csv" | tr -d '\r')
  if [ "$last_succeeded_at" != "n/a" ]; then
    row=$(echo "$row" | awk -F, -v OFS=, -v millis=$((last_succeeded_at - start_at * 1000)) \
      '{ $10 = sprintf("PT%.3fS", millis / 1000); $11 = sprintf("%.2f", $8 / (millis / 1000)); print }')
  else
    log "Not every node reported when its last job succeeded, the processing duration is node 0's and end spread is n/a"
  fi

  local target
  target="$ROOT_DIR/logbooks/aws/$(basename "$csv")"
  [ -f "$target" ] || echo "$(head -1 "$csv" | tr -d '\r'),$LOGBOOK_EXTRA_HEADERS" > "$target"
  echo "$row,$spread,$end_spread" >> "$target"
  write_details "$run_id" "$row" "$spread" "$end_spread" "$start_at" "$last_succeeded_at"
  log "Run $run_id done: $(echo "$row" | awk -F, '{print $11 " jobs/sec (" $8 " of " $7 " jobs succeeded)"}'), start spread ${spread}ms, end spread ${end_spread}ms, details in logbooks/aws/details/$run_id.md"
  return $failed
}

db_check || exit 1

LOGBOOK_EXTRA_HEADERS="start spread (ms),end spread (ms)"
for logbook in "$ROOT_DIR"/logbooks/aws/*-cluster-logbook.csv; do
  [ -f "$logbook" ] || continue
  if [[ "$(head -1 "$logbook" | tr -d '\r')" != *",$LOGBOOK_EXTRA_HEADERS" ]]; then
    log "$logbook has older columns and its processing durations include the end-of-run detection; move it aside first"
    exit 1
  fi
done

wait_until_nodes_are_ready
FAILED_RUNS=0
for repetition in $(seq 1 "$REPETITIONS"); do
  run_once "$repetition" || FAILED_RUNS=$((FAILED_RUNS + 1))
  if [ "$repetition" -lt "$REPETITIONS" ]; then sleep 10; fi
done
log "Results: logbooks/aws/. Don't forget: terraform -chdir=aws/terraform/cluster destroy"
[ "$FAILED_RUNS" -eq 0 ] || { log "$FAILED_RUNS of $REPETITIONS run(s) failed"; exit 1; }
exit
}
