#!/usr/bin/env bash
# Runs the scenario for every node count (the DB machine is kept), then destroys the cluster.
#
# Usage: NODE_COUNTS="1 2 4 8" NODE_INSTANCE_TYPE=c7i.xlarge DB_INSTANCE_TYPE=m6id.4xlarge \
#          aws/bin/sweep.sh Scenario01ProcessJobs 2000000 3
# Other cluster variables via the environment, e.g. TF_VAR_db_type=aurora
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NODE_COUNTS="${NODE_COUNTS:-1 2 4}"
NODE_INSTANCE_TYPE="${NODE_INSTANCE_TYPE:-c7i.xlarge}"
DB_INSTANCE_TYPE="${DB_INSTANCE_TYPE:-m6id.4xlarge}"
TF=(terraform -chdir="$ROOT_DIR/aws/terraform/cluster")

trap '"${TF[@]}" destroy -auto-approve -var node_instance_type="$NODE_INSTANCE_TYPE" -var db_instance_type="$DB_INSTANCE_TYPE"' EXIT

for node_count in $NODE_COUNTS; do
  "${TF[@]}" apply -auto-approve -var node_count="$node_count" -var node_instance_type="$NODE_INSTANCE_TYPE" -var db_instance_type="$DB_INSTANCE_TYPE"
  "$ROOT_DIR/aws/bin/run-cluster.sh" "$@" || echo "Run with $node_count node(s) failed, continuing"
done
