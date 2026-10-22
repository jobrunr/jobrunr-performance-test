#!/usr/bin/env bash
# Usage: aws/bin/run-cluster.sh [scenario] [amount] [repetitions] [key=value]...
#   aws/bin/run-cluster.sh Scenario01ProcessJobs 2000000 3
#   JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75" EXECUTION_TIMEOUT_SECONDS=10800 aws/bin/run-cluster.sh Scenario01ProcessJobs 2000000 1 max_duration=PT2H
#
# Environment overrides:
#   START_DELAY_SECONDS        time the nodes get to start up before processing begins at start_at (default 120)
#   EXECUTION_TIMEOUT_SECONDS  SSM timeout per node, must exceed max_duration + START_DELAY_SECONDS (default 7200)
#
# Each run: node 0 resets the database and a loader creates the jobs, then all nodes start processing at start_at
# and node 0 measures until all jobs succeeded. The start spread is the time between the first and the last node
# processing its first job. Results:
#   logbooks/aws/<scenario>-cluster-logbook.csv   (one row per run)
#   logbooks/aws/runs/<run id>/                   (logbook + logs of every node)
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCENARIO="${1:-Scenario01ProcessJobs}"
AMOUNT="${2:-2000000}"
REPETITIONS="${3:-1}"
shift $(($# < 3 ? $# : 3))
EXTRA_ARGS="$*"

IMAGE_TAG="${IMAGE_TAG:-$(cat "$ROOT_DIR/aws/.image-tag" 2>/dev/null || true)}"
: "${IMAGE_TAG:?No image tag: run aws/bin/build-and-push.sh first or set IMAGE_TAG}"
JAVA_OPTS="${JAVA_OPTS:--XX:MaxRAMPercentage=75}"

tf_out() { terraform -chdir="$ROOT_DIR/aws/terraform/cluster" output -raw "$1"; }
REGION="$(tf_out region)"
BUCKET="$(tf_out results_bucket)"
IMAGE="$(tf_out ecr_repository_url):$IMAGE_TAG"
LICENSE_PARAMETER="$(tf_out license_parameter_name)"
DB_HOST="$(tf_out db_host)"
DB_PASSWORD="$(tf_out db_password)"
DB_INSTANCE_TYPE="$(tf_out db_instance_type)"
NODE_INSTANCE_TYPE="$(tf_out node_instance_type)"
NODE_COUNT="$(tf_out node_count)"
NODE0_ID="$(tf_out node0_instance_id)"
read -r -a WORKER_IDS <<< "$(tf_out worker_instance_ids)"
NODE_IDS=("$NODE0_ID" ${WORKER_IDS[@]+"${WORKER_IDS[@]}"})

AWS=(aws --region "$REGION")
# default max_duration (1h) + 1h
EXECUTION_TIMEOUT_SECONDS="${EXECUTION_TIMEOUT_SECONDS:-7200}"
START_DELAY_SECONDS="${START_DELAY_SECONDS:-120}"

log() { echo "[$(date +%H:%M:%S)] $*"; }

wait_until_nodes_are_ready() {
  log "Waiting for ${#NODE_IDS[@]} node(s) to be registered with SSM"
  local online
  while true; do
    online=$("${AWS[@]}" ssm describe-instance-information --filters "Key=InstanceIds,Values=$(IFS=,; echo "${NODE_IDS[*]}")" \
      --query "length(InstanceInformationList[?PingStatus=='Online'])" --output text)
    [ "$online" -eq "${#NODE_IDS[@]}" ] && break
    sleep 10
  done
  log "Waiting for setup to finish and pulling $IMAGE"
  local command_id
  command_id=$(send_command "cloud-init status --wait > /dev/null && aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin ${IMAGE%%/*} && docker pull -q $IMAGE && docker pull -q postgres:18.6" 900 "${NODE_IDS[@]}")
  for id in "${NODE_IDS[@]}"; do wait_for_command "$command_id" "$id"; done
}

# send_command <script> <timeout seconds> <instance id>... -> prints the command id
send_command() {
  local script="$1" timeout="$2"
  shift 2
  local encoded
  encoded=$(printf '%s' "$script" | base64 | tr -d '\n')
  "${AWS[@]}" ssm send-command --document-name AWS-RunShellScript --instance-ids "$@" \
    --parameters "{\"commands\":[\"echo $encoded | base64 -d | bash\"],\"executionTimeout\":[\"$timeout\"]}" \
    --query Command.CommandId --output text
}

# wait_for_command <command id> <instance id> -> fails if the command did not succeed
wait_for_command() {
  local command_id="$1" instance_id="$2" status
  while true; do
    if ! status=$("${AWS[@]}" ssm get-command-invocation --command-id "$command_id" --instance-id "$instance_id" --query Status --output text 2>&1); then
      # right after send-command the invocation may not be registered yet; any other error (e.g. expired credentials) is fatal
      case "$status" in
        *InvocationDoesNotExist*) status=Pending ;;
        *) log "Could not get the status of command $command_id on $instance_id: $status"; exit 1 ;;
      esac
    fi
    case "$status" in
      Pending|InProgress|Delayed) sleep 15 ;;
      Success) return 0 ;;
      *)
        log "Command $command_id on $instance_id ended with status $status:"
        "${AWS[@]}" ssm get-command-invocation --command-id "$command_id" --instance-id "$instance_id" --query StandardErrorContent --output text | tail -20
        return 1 ;;
    esac
  done
}

# node_script <run id> <label> <args>
node_script() {
  local run_id="$1" label="$2" args="$3"
  cat <<EOF
set -euo pipefail
OUT=/var/lib/jobrunr-perf/runs/$run_id/$label
mkdir -p \$OUT/logbooks
until timeout 1 bash -c '</dev/tcp/$DB_HOST/5432' 2>/dev/null; do sleep 2; done
export JOBRUNRPRO_LICENSE=\$(aws ssm get-parameter --region $REGION --name $LICENSE_PARAMETER --with-decryption --query Parameter.Value --output text)
STATUS=0
docker run --rm --network host -v \$OUT:/work \\
  -e JOBRUNRPRO_LICENSE -e DB_JDBC_URL=jdbc:postgresql://$DB_HOST:5432/jobrunr_perf -e DB_PASSWORD=$DB_PASSWORD \\
  -e CLUSTER_RUN_ID=$run_id -e CLUSTER_SIZE=$NODE_COUNT \\
  -e NODE_INSTANCE_TYPE=$NODE_INSTANCE_TYPE -e DB_INSTANCE_TYPE=$DB_INSTANCE_TYPE \\
  -e JAVA_OPTS="$JAVA_OPTS" \\
  $IMAGE tool=JobRunrPro scenario=$SCENARIO datastore=PostgresExternalDataStore amount=$AMOUNT \\
  system_exit=true $args $EXTRA_ARGS > \$OUT/stdout.log 2>&1 || STATUS=\$?
aws s3 cp --quiet --recursive \$OUT s3://$BUCKET/runs/$run_id/$label/ --region $REGION
tail -5 \$OUT/stdout.log
exit \$STATUS
EOF
}

run_once() {
  local repetition="$1"
  local run_id
  run_id="$(date -u +%Y%m%dT%H%M%SZ)-$SCENARIO-${NODE_COUNT}x$NODE_INSTANCE_TYPE-$repetition"
  log "Run $run_id: $AMOUNT jobs on $NODE_COUNT x $NODE_INSTANCE_TYPE, db $DB_INSTANCE_TYPE, image $IMAGE_TAG"

  log "Resetting the database"
  local reset_command
  reset_command=$(send_command "docker run --rm --network host postgres:18.6 psql -q 'postgresql://jobrunr:$DB_PASSWORD@$DB_HOST:5432/jobrunr_perf' -c 'DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;'" 300 "$NODE0_ID")
  wait_for_command "$reset_command" "$NODE0_ID" || return 1

  log "Creating jobs"
  local loader_command
  loader_command=$(send_command "$(node_script "$run_id" loader cluster_role=loader)" "$EXECUTION_TIMEOUT_SECONDS" "$NODE0_ID")
  wait_for_command "$loader_command" "$NODE0_ID" || return 1

  local start_at=$(($(date +%s) + START_DELAY_SECONDS))
  log "Processing jobs on $NODE_COUNT node(s), starting in ${START_DELAY_SECONDS}s"
  local node0_command
  node0_command=$(send_command "$(node_script "$run_id" node-0 "cluster_role=worker start_at=$start_at")" "$EXECUTION_TIMEOUT_SECONDS" "$NODE0_ID")

  # every node is a worker; label the output folders node-0, node-1, ... so they line up with the run's nodes
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

  local local_dir="$ROOT_DIR/logbooks/aws/runs/$run_id"
  mkdir -p "$local_dir"
  "${AWS[@]}" s3 cp --quiet --recursive "s3://$BUCKET/runs/$run_id/" "$local_dir/"

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

  local target
  target="$ROOT_DIR/logbooks/aws/$(basename "$csv")"
  [ -f "$target" ] || echo "$(head -1 "$csv" | tr -d '\r'),start spread (ms)" > "$target"
  echo "$(tail -1 "$csv" | tr -d '\r'),$spread" >> "$target"
  log "Run $run_id done: $(tail -1 "$csv" | awk -F, '{print $11 " jobs/sec (" $8 " of " $7 " jobs succeeded)"}'), start spread ${spread}ms"
  return $failed
}

wait_until_nodes_are_ready
FAILED_RUNS=0
for repetition in $(seq 1 "$REPETITIONS"); do
  run_once "$repetition" || FAILED_RUNS=$((FAILED_RUNS + 1))
  if [ "$repetition" -lt "$REPETITIONS" ]; then sleep 10; fi
done
log "Results: logbooks/aws/. Don't forget: terraform -chdir=aws/terraform/cluster destroy"
[ "$FAILED_RUNS" -eq 0 ] || { log "$FAILED_RUNS of $REPETITIONS run(s) failed"; exit 1; }
