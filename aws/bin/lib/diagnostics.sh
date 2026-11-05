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

# db_host_start_script: vmstat, and every second the jiffies of every vCPU and the CPU of the postmaster's exited
# children, on the database instance
db_host_start_script() {
  cat <<'EOF'
pkill -x vmstat; pkill -f cpu-sampler.sh
mkdir -p /var/lib/jobrunr-perf
cd /var/lib/jobrunr-perf
cat > cpu-sampler.sh <<'SH'
# lines: '<epoch ms> cpu<n> <jiffies as in /proc/stat>' per vCPU, '<epoch ms> children <cutime> <cstime>' of the postmaster
postmaster=$(pgrep -o -x postgres)
while :; do
  awk -v now="$(date +%s%3N)" 'FNR == NR { if (/^cpu[0-9]/) print now, $0; next } { sub(/^.*\) /, ""); print now, "children", $14, $15 }' \
    /proc/stat ${postmaster:+/proc/$postmaster/stat}
  sleep 1
done
SH
lscpu -p=CPU,CORE | grep -v '^#' > cores.txt
setsid nohup vmstat -n -t 1 > vmstat.txt 2>&1 < /dev/null &
setsid nohup bash cpu-sampler.sh > cpu.txt 2>&1 < /dev/null &
EOF
}

# diagnostics_before_processing <run id>
diagnostics_before_processing() {
  diagnostics_on || return 0
  log "Diagnostics: sampling the database"
  if [ "$DB_TYPE" = ec2 ]; then
    send_command "$(db_host_start_script)" 300 "$DB_INSTANCE_ID" > /dev/null
  fi
  run_on "$NODE0_ID" "$(db_before_processing_script "$1")"
}

# diagnostics_after_processing <run id>: before the run's files are downloaded
diagnostics_after_processing() {
  diagnostics_on || return 0
  log "Diagnostics: recording what the database did"
  run_on "$NODE0_ID" "$(db_after_processing_script "$1")"
  if [ "$DB_TYPE" = ec2 ]; then
    run_on "$DB_INSTANCE_ID" "pkill -x vmstat; pkill -f cpu-sampler.sh; for f in vmstat.txt cpu.txt cores.txt; do aws s3 cp --quiet /var/lib/jobrunr-perf/\$f s3://$BUCKET/runs/$1/db/\$f --region $REGION; done"
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

The database's vCPUs per physical core (two hyperthreads share one core) from \`/proc/stat\`, every second. Cores in
use counts a core as busy as its busiest vCPU, both hyperthreads busy as its least busy one. Exited children is the CPU
of the processes the postmaster reaped, most of all parallel and autovacuum workers.

$(if [ -s "$1/db/cpu.txt" ] && [ -s "$1/db/cores.txt" ]; then core_table "$1/db" "$2" "$3"; else or_not_recorded ""; fi)

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

# core_table <db dir> <from epoch> <to epoch>
core_table() {
  awk -v from="$(($2 * 1000))" -v to="$(($3 * 1000))" -F'[ ,]+' '
    FNR == NR { core[$1] = $2; if (!($2 in cores)) n++; cores[$2] = 1; next }
    $1 < from || $1 > to { next }
    $2 == "children" { if (has_children) children += $3 + $4 - last_children; has_children = 1; last_children = $3 + $4; next }
    {
      cpu = substr($2, 4); total = 0; for (i = 3; i <= 10; i++) total += $i
      idle = $6 + $7
      if (cpu in last_total && total > last_total[cpu]) {
        b = 1 - (idle - last_idle[cpu]) / (total - last_total[cpu]); vcpus += b; key = $1 SUBSEP core[cpu]
        if (b > hi[key]) hi[key] = b
        if (!(key in lo) || b < lo[key]) lo[key] = b
        if (!($1 in seen)) { seen[$1] = 1; seconds++; if (!first) first = $1; last = $1 }
      }
      last_total[cpu] = total; last_idle[cpu] = idle
    }
    END {
      if (!seconds) exit
      for (at in seen) {
        full = 0
        for (c in cores) { sum_hi += hi[at, c]; sum_lo += lo[at, c]; if (hi[at, c] >= 0.9) full++ }
        if (full == n) saturated++
      }
      printf "| %d | %.1f | %.1f | %.1f | %.0f | %.1f |\n", n, vcpus / seconds, sum_hi / seconds, sum_lo / seconds,
        100 * saturated / seconds, (last > first ? children / 100 / ((last - first) / 1000) : 0)
    }' "$1/cores.txt" "$1/cpu.txt" |
    table_or "Not recorded." \
      '| physical cores | vCPUs busy | cores in use | cores with both hyperthreads busy | seconds with every core >= 90% busy (%) | exited children (vCPUs) |' \
      '|---:|---:|---:|---:|---:|---:|'
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
