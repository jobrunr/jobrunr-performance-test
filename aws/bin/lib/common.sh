# sourced by run-cluster.sh

log() { echo "[$(date +%H:%M:%S)] $*"; }

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
      # not registered yet right after send-command
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

# run_on <instance id> <script>: doesn't fail the run
run_on() {
  local command_id
  command_id=$(send_command "$2" 300 "$1")
  wait_for_command "$command_id" "$1" || log "Command failed, see the error above; continuing"
}

# or_not_recorded <file>
or_not_recorded() { if [ -s "$1" ]; then cat "$1"; else echo "Not recorded, see the output of run-cluster.sh."; fi; }
