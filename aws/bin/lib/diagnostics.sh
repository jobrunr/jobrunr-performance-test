# sourced by run-cluster.sh: DIAGNOSTICS=true; the database part comes from db_* functions

diagnostics_on() { [ "$DIAGNOSTICS" = true ]; }

# diagnostics_node_start_script / diagnostics_node_stop_script: around the worker's docker run, on every node
diagnostics_node_start_script() {
  diagnostics_on || return 0
  db_round_trips_script
  echo 'vmstat -n -t 1 > $OUT/vmstat.txt & VMSTAT=$!'
}

diagnostics_node_stop_script() {
  diagnostics_on || return 0
  echo 'kill $VMSTAT'
}

# diagnostics_before_processing <run id>
diagnostics_before_processing() {
  diagnostics_on || return 0
  log "Diagnostics: sampling the database"
  if [ "$DB_TYPE" = ec2 ]; then
    send_command "pkill -x vmstat; mkdir -p /var/lib/jobrunr-perf; setsid nohup vmstat -n -t 1 > /var/lib/jobrunr-perf/vmstat.txt 2>&1 < /dev/null &" 300 "$DB_INSTANCE_ID" > /dev/null
  fi
  run_on "$NODE0_ID" "$(db_before_processing_script "$1")"
}

# diagnostics_after_processing <run id>: before the run's files are downloaded
diagnostics_after_processing() {
  diagnostics_on || return 0
  log "Diagnostics: recording what the database did"
  run_on "$NODE0_ID" "$(db_after_processing_script "$1")"
  if [ "$DB_TYPE" = ec2 ]; then
    run_on "$DB_INSTANCE_ID" "pkill -x vmstat; aws s3 cp --quiet /var/lib/jobrunr-perf/vmstat.txt s3://$BUCKET/runs/$1/db/vmstat.txt --region $REGION"
  fi
}

# diagnostics_details <run dir> <start_at> <processing end epoch> <last succeeded at millis> <vCPUs per node>
diagnostics_details() {
  if ! diagnostics_on; then
    echo "## Diagnostics"
    echo
    echo "Off; pass DIAGNOSTICS=true for CPU, connection pool and database details."
    return 0
  fi
  cat <<EOF
## Throughput and round trips

- Steady state (20% to 80% of every node's processing): $(steady_state_throughput "$1"/node-*/stdout.log) jobs / sec
- Round trip to the database (median / p99 of 1,000 x \`$DB_ROUND_TRIP_QUERY\` per node, before processing):
$(round_trips "$1")

## CPU usage

From \`vmstat\` every second between \`start_at\` and the last job succeeding, in % of all vCPUs. The run queue counts
the processes running or waiting for a CPU: compare it with the vCPUs ($5 per node).

$(cpu_table "$1" "$2" "$3")

## Connection pool

How long getting a connection from every node's Hikari pool took while processing (the wait for a free one included)
and how long a connection was held. Connections in use on average = connections acquired per second x hold time.

$(if grep -qs 'Connections while processing' "$1"/node-*/stdout.log; then pool_table "$1"; else or_not_recorded ""; fi)

$(db_diagnostics_details "$1" $(($2 * 1000)) "$4")
EOF
}

# steady_state_throughput <stdout.log>...: summed jobs / sec between 20% and 80% of every node's processing
steady_state_throughput() {
  awk '
    function finish(   i, a, b) {
      for (i = 1; i <= n; i++) { if (!a && t[i] >= 0.2 * t[n]) a = i; if (t[i] <= 0.8 * t[n]) b = i }
      if (a && b > a) total += (c[b] - c[a]) * 1000 / (t[b] - t[a])
    }
    FNR == 1 { finish(); n = 0 }
    / - processed [0-9]+ jobs / { for (i = 1; i < NF; i++) { if ($(i + 1) == "/" && $i ~ /ms$/) t[n + 1] = $i + 0; if ($i == "processed") c[n + 1] = $(i + 1) } n++ }
    END { finish(); printf "%.2f", total }' "$@"
}

# round_trips <run dir>
round_trips() {
  local file
  for file in "$1"/node-*/round-trips-ms.txt; do
    [ -s "$file" ] || continue
    sort -n "$file" | awk -v node="$(basename "$(dirname "$file")")" \
      '{ t[NR] = $1 } END { printf "  - %s: %.3f / %.3f ms\n", node, t[int(NR * 0.5) + 1], t[int(NR * 0.99) + 1] }'
  done
}

# cpu_table <run dir> <from epoch> <to epoch>
cpu_table() {
  local from to file machine
  from=$(date -u -r "$2" '+%Y-%m-%d %H:%M:%S')
  to=$(date -u -r "$3" '+%Y-%m-%d %H:%M:%S')
  echo "| machine | user % | system % | iowait % | steal % | peak busy % | run queue |"
  echo "|---|---:|---:|---:|---:|---:|---:|"
  for file in "$1"/node-*/vmstat.txt "$1"/db/vmstat.txt; do
    [ -s "$file" ] || continue
    machine=$(basename "$(dirname "$file")")
    [ "$machine" != db ] || machine=database
    awk -v machine="$machine" -v from="$from" -v to="$to" '
      $1 == "r" { for (i = 1; i <= NF; i++) col[$i] = i; next }
      !("us" in col) || $1 !~ /^[0-9]+$/ { next }
      {
        at = $(NF - 1) " " $NF
        if (at < from || at > to) next
        n++; us += $col["us"]; sy += $col["sy"]; wa += $col["wa"]; st += $col["st"]; r += $col["r"]
        if ($col["us"] + $col["sy"] > peak) peak = $col["us"] + $col["sy"]
      }
      END { if (n) printf "| %s | %.0f | %.0f | %.0f | %.0f | %d | %.1f |\n", machine, us / n, sy / n, wa / n, st / n, peak, r / n }' "$file"
  done
}

# pool_table <run dir>
pool_table() {
  local file
  echo "| node | pool size | connections acquired | avg wait (ms) | max wait (ms) | avg held (ms) | timeouts |"
  echo "|---|---:|---:|---:|---:|---:|---:|"
  for file in "$1"/node-*/stdout.log; do
    sed -nE "s/.*Connections while processing \(pool of ([0-9]+)\): ([0-9]+) acquired, ([0-9.]+) ms average wait, ([0-9.]+) ms max wait, ([0-9.]+) ms average hold, ([0-9]+) timeouts.*/| $(basename "$(dirname "$file")") | \1 | \2 | \3 | \4 | \5 | \6 |/p" "$file"
  done
}
