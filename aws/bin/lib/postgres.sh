# sourced by run-cluster.sh: Postgres on EC2 (db_type=ec2) or Aurora

PG_SETTINGS="${PG_SETTINGS:-}"
DATASTORE=PostgresExternalDataStore
DB_PORT=5432
DB_JDBC_URL="jdbc:postgresql://$DB_HOST:$DB_PORT/jobrunr_perf"
DB_CLIENT_IMAGE=postgres:18.6
DB_URL="postgresql://jobrunr:$DB_PASSWORD@$DB_HOST:$DB_PORT/jobrunr_perf"
PSQL="docker run --rm -i --network host $DB_CLIENT_IMAGE psql -X -q"

db_check() {
  if [ -n "$PG_SETTINGS" ] && [ "$DB_TYPE" != ec2 ]; then
    log "PG_SETTINGS needs db_type=ec2; Aurora takes its settings from the parameter group"
    return 1
  fi
}

# db_configure: on EC2, PG_SETTINGS replace the previous run's run.conf, ALTER SYSTEM is reset and Postgres restarts
db_configure() {
  [ "$DB_TYPE" = ec2 ] || return 0
  log "Restarting Postgres with PG_SETTINGS: ${PG_SETTINGS:-none}"
  local command_id
  command_id=$(send_command "$(configure_script)" 300 "$DB_INSTANCE_ID")
  wait_for_command "$command_id" "$DB_INSTANCE_ID"
}

configure_script() {
  printf '%s\n' "cat > /etc/jobrunr-perf/run.conf <<'EOF'" $PG_SETTINGS "EOF"
  cat <<'EOF'
docker exec postgres psql -q -U jobrunr -d jobrunr_perf -c 'ALTER SYSTEM RESET ALL'
docker restart postgres > /dev/null
for i in $(seq 120); do docker exec postgres pg_isready -q -U jobrunr -d jobrunr_perf 2> /dev/null && exit 0; sleep 1; done
echo "Postgres did not start with these settings, see: docker logs postgres" >&2
exit 1
EOF
}

db_reset() {
  log "Resetting the database"
  local command_id
  command_id=$(send_command "$PSQL '$DB_URL' -c 'DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;'" 300 "$NODE0_ID")
  wait_for_command "$command_id" "$NODE0_ID"
}

# db_fetch_log <run id> <since epoch> <file>: doesn't fail the run
db_fetch_log() {
  local run_id="$1" since="$2" file="$3" command_id log_file
  mkdir -p "$(dirname "$file")"
  case "$DB_TYPE" in
    ec2)
      command_id=$(send_command "docker logs --since $since postgres > /tmp/postgres.log 2>&1
aws s3 cp --quiet /tmp/postgres.log s3://$BUCKET/runs/$run_id/db/postgres.log --region $REGION" 300 "$DB_INSTANCE_ID")
      wait_for_command "$command_id" "$DB_INSTANCE_ID" && "${AWS[@]}" s3 cp --quiet "s3://$BUCKET/runs/$run_id/db/postgres.log" "$file" ;;
    aurora)
      # hourly files
      for log_file in $("${AWS[@]}" rds describe-db-log-files --db-instance-identifier "$DB_INSTANCE_ID" --file-last-written $((since * 1000)) \
                          --query 'DescribeDBLogFiles[].LogFileName' --output text); do
        "${AWS[@]}" rds download-db-log-file-portion --db-instance-identifier "$DB_INSTANCE_ID" --log-file-name "$log_file" --starting-token 0 --output text
      done | awk -v from="$(date -u -r "$since" '+%Y-%m-%d %H:%M:%S')" 'substr($0, 1, 19) >= from' > "$file" ;;
  esac || log "Could not fetch the Postgres log, see the error above"
}

db_configuration_details() {
  echo "- PG_SETTINGS: \`${PG_SETTINGS:-none}\`"
}

# db_log_details <run dir> <from epoch> <to epoch>
db_log_details() {
  cat <<EOF
## Postgres log

Warnings, errors and hints between \`start_at\` and the last job succeeding, and checkpoints that came too quickly. The
whole log since the run started is in \`runs/$(basename "$1")/db/postgres.log\`.

$(if [ -s "$1/db/postgres.log" ]; then log_summary "$1/db/postgres.log" "$2" "$3"; else or_not_recorded ""; fi)
EOF
}

# log_summary <postgres.log> <from epoch> <to epoch>
log_summary() {
  awk -v from="$(date -u -r "$2" '+%Y-%m-%d %H:%M:%S')" -v to="$(date -u -r "$3" '+%Y-%m-%d %H:%M:%S')" '
       substr($0, 1, 19) < from || substr($0, 1, 19) > to { next }
       match($0, /(LOG|WARNING|ERROR|FATAL|PANIC|DETAIL|HINT): +/) {
         message = substr($0, RSTART)
         if (message ~ /^LOG:/ && message !~ /too frequently/) next
         key = message; gsub(/[0-9]+/, "#", key); sub(/# second /, "# seconds ", key); gsub(/\|/, "\\|", message)
         if (!(key in count)) example[key] = substr(message, 1, 200)
         count[key]++
       }
       END { for (key in count) printf "| %d | `%s` |\n", count[key], example[key] }' "$1" | sort -t '|' -k2 -rn | {
    if read -r first; then printf '| count | message (first occurrence) |\n|---:|---|\n%s\n' "$first"; cat; else echo "No warnings or errors."; fi
  }
}
