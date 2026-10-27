# sourced by run-cluster.sh: the Postgres part of DIAGNOSTICS=true

SQL_DIR="$ROOT_DIR/aws/sql/postgres"
WAIT_SAMPLE_SQL=$(cat "$SQL_DIR/wait-sample.sql")
SETTINGS_SQL=$(cat "$SQL_DIR/settings.sql")
STATEMENTS_SQL=$(cat "$SQL_DIR/statements.sql")
ACTIVITY_SQL=$(cat "$SQL_DIR/activity.sql")
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
  -c "SELECT pg_stat_reset_shared('checkpointer')" > /dev/null
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
aws s3 cp --quiet --recursive $DB_RUN_DIR/$1/db s3://$BUCKET/runs/$1/db/ --region $REGION
EOF
}

# psql_script <run id> <file> <sql>
psql_script() {
  cat <<EOF
$PSQL -A -t '$DB_URL' > $DB_RUN_DIR/$1/db/$2 <<'SQL'
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

$(if [ -s "$1/db/waits.csv" ]; then waits_table "$1/db/waits.csv" "$2" "$3"; else or_not_recorded ""; fi)

## Who blocks the lock waits

Every connection waiting on a lock (wait event type \`Lock\`) per connection holding it, from \`pg_blocking_pids()\`
in the same samples. The lock waits are read just before and just after the \`pg_stat_activity\` snapshot: a wait that
did not span the snapshot has an \`unknown\` state and statements. Statements are \`query id: start\`; the query id
is \`queryid\` in \`pg_stat_statements\`. An idle in transaction blocker shows its last statement.

$([ ! -s "$1/db/waits.csv" ] || lock_waits_table "$1/db/waits.csv" "$2" "$3")

## Autovacuum and checkpoints

Sampled every second like the connections above, then the statistics of the jobs table and the checkpointer at the
end of the run.

$([ ! -s "$1/db/waits.csv" ] || background_activity "$1/db/waits.csv" "$2" "$3")
$(or_not_recorded "$1/db/activity.md")

## Database work while processing

Top-level statements from \`pg_stat_statements\` by execution time on the database (without network or connection
pool time).

$(or_not_recorded "$1/db/statements.md")
EOF
}

# waits_table <waits.csv> <from millis> <to millis>
waits_table() {
  awk -F, -v from="$2" -v to="$3" '
    $1 < from || (to ~ /^[0-9]+$/ && $1 > to + 0) { next }
    $2 == "sample" { samples++; next }
    $2 == "autovacuum" || $2 == "checkpoint" || $2 == "lock" { next }
    $2 != "idle" { busy += $5; connections["| " $2 " | " $3 " | " $4] += $5 }
    END {
      printf "%d samples, on average %.1f busy client connections.\n", samples, samples ? busy / samples : 0
      for (key in connections) printf "%s | %.2f | %.1f |\n", key, connections[key] / samples, 100 * connections[key] / busy
    }' "$1" | {
    read -r summary
    printf '%s\n\n| state | wait event type | wait event | avg connections | share of busy (%%) |\n|---|---|---|---:|---:|\n' "$summary"
    sort -t '|' -k5 -rn
  }
}

# lock_waits_table <waits.csv> <from millis> <to millis>
lock_waits_table() {
  awk -F, -v from="$2" -v to="$3" '
    $1 < from || (to ~ /^[0-9]+$/ && $1 > to + 0) { next }
    $2 == "sample" { samples++ }
    $2 == "lock" { waiting += $5; waits["| " $9 " | `" $6 "` | " $8 " | " $3 " | " $4 " | `" $7 "`"] += $5 }
    END { for (key in waits) printf "%s | %.2f | %.1f |\n", key, waits[key] / samples, 100 * waits[key] / waiting }' "$1" |
    sort -t '|' -k9 -rn | {
    if read -r first; then
      printf '| lock | waiting statement | blocker | blocker state | blocker wait | blocking statement | avg waiting | share (%%) |\n'
      printf '|---|---|---|---|---|---|---:|---:|\n%s\n' "$first"
      cat
    else
      echo "No lock waits sampled."
    fi
  }
}

# background_activity <waits.csv> <from millis> <to millis>
background_activity() {
  awk -F, -v from="$2" -v to="$3" '
    $1 < from || (to ~ /^[0-9]+$/ && $1 > to + 0) { next }
    $2 == "sample" { samples++ }
    $2 == "checkpoint" { checkpoint++ }
    $2 == "autovacuum" { autovacuum[$3]++ }
    END {
      printf "- Checkpoint being written: %d of %d samples\n", checkpoint, samples
      for (activity in autovacuum) printf "- Autovacuum %s: %d of %d samples\n", activity, autovacuum[activity], samples
    }' "$1"
}
