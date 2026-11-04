# sourced by run-cluster.sh: the Postgres part of DIAGNOSTICS=true

SQL_DIR="$ROOT_DIR/aws/sql/postgres"
WAIT_SAMPLE_SQL=$(cat "$SQL_DIR/wait-sample.sql")
SETTINGS_SQL=$(cat "$SQL_DIR/settings.sql")
STATEMENTS_SQL=$(cat "$SQL_DIR/statements.sql")
ACTIVITY_SQL=$(cat "$SQL_DIR/activity.sql")
INDEXES_SQL=$(cat "$SQL_DIR/indexes.sql")
IO_SQL=$(cat "$SQL_DIR/io.sql")
WAL_SQL=$(cat "$SQL_DIR/wal.sql")
DB_ROUND_TRIP_QUERY="SELECT 1"
DB_RUN_DIR=/var/lib/jobrunr-perf/runs

# db_round_trips_script: 1000 round trips into $OUT/round-trips-ms.txt, run on a node
db_round_trips_script() {
  cat <<EOF
{ echo '\timing on'; for i in \$(seq 1000); do echo '$DB_ROUND_TRIP_QUERY;'; done; } \\
  | $PSQL -A -t '$DB_URL' \\
  | awk '/^Time:/ { print \$2 }' > \$OUT/round-trips-ms.txt || true
EOF
}

# db_before_processing_script <run id>: starts sampling pg_stat_activity, resets the statistics, run on node 0
db_before_processing_script() {
  cat <<EOF
mkdir -p $DB_RUN_DIR/$1/db
cat > $DB_RUN_DIR/$1/db/wait-sample.sql <<'SQL'
$WAIT_SAMPLE_SQL
SQL
docker rm -f wait-sampler > /dev/null 2>&1
docker run -d --init --name wait-sampler --network host -v $DB_RUN_DIR/$1/db:/db $DB_CLIENT_IMAGE \\
  psql -X -q --csv -t -o /db/waits.csv -f /db/wait-sample.sql '$DB_URL'
# not pg_stat_reset(): autovacuum relies on the dead row counts
$PSQL '$DB_URL' -c 'CREATE EXTENSION IF NOT EXISTS pg_stat_statements' -c 'SELECT pg_stat_statements_reset()' \\
  -c "SELECT pg_stat_reset_shared('checkpointer')" -c "SELECT pg_stat_reset_shared('io')" \\
  -c "SELECT pg_stat_reset_shared('wal')" \\
  -c "SELECT pg_stat_reset_single_table_counters(indexrelid) FROM pg_stat_user_indexes WHERE relname = 'jobrunr_jobs'" > /dev/null
EOF
}

# db_after_processing_script <run id>: run on node 0
db_after_processing_script() {
  cat <<EOF
docker stop -t 10 wait-sampler > /dev/null 2>&1; docker rm wait-sampler > /dev/null 2>&1
mkdir -p $DB_RUN_DIR/$1/db
$(psql_script "$1" settings.md "$SETTINGS_SQL")
$(psql_script "$1" statements.md "$STATEMENTS_SQL")
$(psql_script "$1" activity.md "$ACTIVITY_SQL")
$(psql_script "$1" indexes.md "$INDEXES_SQL")
$(psql_script "$1" io.md "$IO_SQL")
# the WAL position of the middle 'load' sample whose update count is strictly between the first and the last one
MID=\$(awk -F, '\$2 == "load" { n++; u[n] = \$4; l[n] = \$6 }
  END { for (i = 1; i <= n; i++) if (u[i] > u[1] && u[i] < u[n]) m[++k] = l[i]; if (k) print m[int(k / 2) + 1] }' $DB_RUN_DIR/$1/db/waits.csv)
$(psql_script "$1" wal.md "$WAL_SQL" '-v mid="$MID"')
aws s3 cp --quiet --recursive $DB_RUN_DIR/$1/db s3://$BUCKET/runs/$1/db/ --region $REGION
EOF
}

# psql_script <run id> <file> <sql> [psql options]
psql_script() {
  cat <<EOF
$PSQL -A -t $4 '$DB_URL' > $DB_RUN_DIR/$1/db/$2 <<'SQL'
$3
SQL
EOF
}

# db_diagnostics_details <run dir> <from millis> <to millis>
db_diagnostics_details() {
  cat <<EOF
## Postgres settings that differ from the defaults

$(or_not_recorded "$1/db/settings.md")

## What the database connections were doing

Sampled every second from \`pg_stat_activity\`. CPU means not waiting on anything; \`idle in transaction\` connections
wait for the application in the middle of a transaction.

$(waits_section waits_table "$@")

## Who blocks the lock waits

Every connection waiting on a lock (wait event type \`Lock\`) per connection holding it, from \`pg_blocking_pids()\`
in the same samples. The lock waits are read just before and just after the \`pg_stat_activity\` snapshot: a wait that
did not span the snapshot has an \`unknown\` state and statements. Statements are \`query id: start\`; the query id
is \`queryid\` in \`pg_stat_statements\`. An idle in transaction blocker shows its last statement.

$(waits_section lock_waits_table "$@")

## What the other Postgres processes were doing

Processes that are not client backends, sampled like the connections above, without the ones idle in their main
loop. CPU means not waiting on anything (running, or waiting for a CPU).

$(waits_section backends_table "$@")

## What every statement waits on

The busy connections above per statement (\`query id: start\`; for \`idle in transaction\`, the last statement).

$(waits_section statement_waits_table "$@")

## Throughput while the stats query runs

Every second by the number of \`jobrunr_jobs_stats\` queries running at the sample. WAL bytes are exact per second;
\`jobrunr_jobs\` updates (claim and success, 2 per job) come from the table statistics, which backends report up to a
second late.

$(waits_section load_table "$@")

## Who holds back the xmin horizon

Every second, the backend with the oldest \`backend_xmin\`: no backend can prune row versions or mark index entries
dead that this snapshot can still see. Backends running a lazy VACUUM are left out, their snapshot doesn't hold back
the horizon; autovacuum shows up during the ANALYZE of a VACUUM ANALYZE. Age is in transaction ids, duration since its
statement started.

$(waits_section horizon_table "$@")

## Autovacuum and checkpoints

Sampled every second like the connections above, then the statistics of the jobs table and the checkpointer at the
end of the run.

$(waits_section background_activity "$@")
$(or_not_recorded "$1/db/activity.md")

## Indexes of jobrunr_jobs

\`pg_stat_user_indexes\` while processing. Entries read per scan shows how far a scan walks; every non-HOT update also
inserts into every index, used or not.

$(or_not_recorded "$1/db/indexes.md")

## Buffer I/O

\`pg_stat_io\` while processing. Evictions and writes by client backends mean they had to make room in
\`shared_buffers\` themselves.

$(or_not_recorded "$1/db/io.md")

## WAL

\`pg_stat_wal\` while processing, then up to 1 GB of WAL from the middle of processing by resource manager
(\`pg_walinspect\`): Btree is the index entries, Heap the row versions.

$(or_not_recorded "$1/db/wal.md")

## Database work while processing

Top-level statements from \`pg_stat_statements\` by execution time on the database (without network or connection
pool time).

$(or_not_recorded "$1/db/statements.md")
EOF
}

# waits_section <table function> <run dir> <from millis> <to millis>
waits_section() {
  if [ -s "$2/db/waits.csv" ]; then "$1" "$2/db/waits.csv" "$3" "$4"; else or_not_recorded ""; fi
}

# waits_between <waits.csv> <from millis> <to millis>
waits_between() {
  awk -F, -v from="$2" -v to="$3" '$1 >= from && (to !~ /^[0-9]+$/ || $1 <= to + 0)' "$1"
}

# waits_table <waits.csv> <from millis> <to millis>
waits_table() {
  waits_between "$@" | awk -F, '
    $2 == "sample" { samples++ }
    $2 == "client" { busy += $5; connections["| " $7 " | " $3 " | " $4] += $5 }
    END {
      printf "%d samples, on average %.1f busy client connections.\n", samples, samples ? busy / samples : 0
      for (key in connections) printf "%s | %.2f | %.1f |\n", key, connections[key] / samples, 100 * connections[key] / busy
    }' | {
    read -r summary
    printf '%s\n\n| state | wait event type | wait event | avg connections | share of busy (%%) |\n|---|---|---|---:|---:|\n' "$summary"
    sort -t '|' -k5 -rn
  }
}

# lock_waits_table <waits.csv> <from millis> <to millis>
lock_waits_table() {
  waits_between "$@" | awk -F, '
    $2 == "sample" { samples++ }
    $2 == "lock" { waiting += $5; waits["| " $9 " | `" $6 "` | " $8 " | " $3 " | " $4 " | `" $7 "`"] += $5 }
    END { for (key in waits) printf "%s | %.2f | %.1f |\n", key, waits[key] / samples, 100 * waits[key] / waiting }' |
    sort -t '|' -k9 -rn |
    table_or "No lock waits sampled." \
      '| lock | waiting statement | blocker | blocker state | blocker wait | blocking statement | avg waiting | share (%) |' \
      '|---|---|---|---|---|---|---:|---:|'
}

# background_activity <waits.csv> <from millis> <to millis>
background_activity() {
  waits_between "$@" | awk -F, '
    $2 == "sample" { samples++ }
    $2 == "checkpoint" { checkpoint++ }
    $2 == "autovacuum" { autovacuum[$3]++ }
    END {
      printf "- Checkpoint being written: %d of %d samples\n", checkpoint, samples
      for (activity in autovacuum) printf "- Autovacuum %s: %d of %d samples\n", activity, autovacuum[activity], samples
    }'
}

# statement_waits_table <waits.csv> <from millis> <to millis>
statement_waits_table() {
  waits_between "$@" | awk -F, '
    $2 == "sample" { samples++ }
    $2 == "client" { busy += $5; waits["| `" $6 "` | " $7 " | " $3 " | " $4] += $5 }
    END { for (key in waits) printf "%s | %.2f | %.1f |\n", key, waits[key] / samples, 100 * waits[key] / busy }' |
    sort -t '|' -k6 -rn | head -25 |
    table_or "Not recorded." \
      '| statement | state | wait event type | wait event | avg connections | share of busy (%) |' \
      '|---|---|---|---|---:|---:|'
}

# load_table <waits.csv> <from millis> <to millis>
load_table() {
  waits_between "$@" | awk -F, '
    $2 == "load" { n++; at[n] = $1; stats[n] = $3; updates[n] = $4; workers[n] = $5; wal[n] = $6 }
    $2 == "autovacuum" { vacuuming[$1] = 1 }
    $2 == "client" { busy[$1] += $5 }
    END {
      for (i = 2; i <= n; i++) {
        if (updates[i] == updates[i - 1] && wal[i] - wal[i - 1] < 1048576) continue
        b = stats[i] >= 3 ? "3+" : stats[i]
        seconds[b] += (at[i] - at[i - 1]) / 1000; count[b]++
        upd[b] += updates[i] - updates[i - 1]; bytes[b] += wal[i] - wal[i - 1]
        pw[b] += workers[i]; conns[b] += busy[at[i]]; av[b] += (at[i] in vacuuming)
      }
      for (b in count) printf "| %s | %d | %.0f | %.1f | %.1f | %.1f | %.0f |\n", b, count[b], upd[b] / seconds[b],
        bytes[b] / seconds[b] / 1048576, pw[b] / count[b], conns[b] / count[b], 100 * av[b] / count[b]
    }' | sort -t '|' -k2 |
    table_or "Not recorded." \
      '| stats queries running | seconds | jobrunr_jobs updates / s | WAL MB / s | parallel workers | busy client connections | autovacuum running (%) |' \
      '|---:|---:|---:|---:|---:|---:|---:|'
}

# horizon_table <waits.csv> <from millis> <to millis>
horizon_table() {
  waits_between "$@" | awk -F, '
    $2 == "sample" { samples++ }
    $2 == "horizon" {
      key = "| " $6 " | `" $3 "`"; count[key]++; age[key] += $5; if ($5 > max[key]) max[key] = $5; ms[key] += $4
    }
    END {
      for (key in count) printf "%s | %.1f | %.0f | %d | %.0f |\n", key, 100 * count[key] / samples, age[key] / count[key],
        max[key], ms[key] / count[key]
    }' | sort -t '|' -k4 -rn | head -15 |
    table_or "No snapshots sampled." \
      '| backend type | statement | share of samples (%) | avg age | max age | avg ms since statement start |' \
      '|---|---|---:|---:|---:|---:|'
}

# backends_table <waits.csv> <from millis> <to millis>
backends_table() {
  waits_between "$@" | awk -F, '
    $2 == "sample" { samples++ }
    $2 == "backend" { processes["| " $3 " | " $4] += $5 }
    END { for (key in processes) printf "%s | %.2f |\n", key, processes[key] / samples }' |
    sort -t '|' -k4 -rn |
    table_or "Not recorded." \
      '| backend type | CPU or wait event type | avg processes |' \
      '|---|---|---:|'
}
