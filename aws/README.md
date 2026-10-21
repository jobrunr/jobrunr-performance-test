# Running the JobRunr Pro performance tests on AWS

Runs a JobRunr Pro scenario on N application nodes sharing one Postgres database, so you can compare instance types and
measure how throughput scales in cluster mode.

> This setup is new: report what breaks and see [Troubleshooting](#troubleshooting).

## How a run works

- **Database**, chosen with `db_type`:
  - `ec2` (default): `postgres:18.6` in Docker with the same settings as the local Testcontainers setup. The data
    directory is on local NVMe instance storage, so `db_instance_type` must have it (`m6id`, `i4i`, `c6id`, ...). Comparable
    with the Hetzner numbers.
  - `aurora`: Aurora PostgreSQL, I/O-Optimized, one writer in the nodes' AZ. Closer to a typical AWS production setup.
    Uses the latest Aurora release of `aurora_postgres_version` (default 18; set 17 if 18 isn't offered yet).
- **Reset** (from node 0): drops and recreates the `public` schema with `psql`, so every run starts empty.
- **Loader** (on node 0): creates the jobs and exits.
- **All nodes**: start a worker that initializes JobRunr Pro with its `BackgroundJobServer` paused and starts processing
  at the same wall-clock time (`start_at`, 60 seconds after the loader finished; EC2 clocks are kept in sync by Amazon
  Time Sync). Each worker stops once all jobs succeeded; node 0's logbook, measured from `start_at`, is the result.

The flow itself doesn't depend on the database (only JobRunr touches it), but the AWS setup provisions Postgres
only. Runs are started with SSM Run Command, so the machines have no SSH or other inbound access.

## Prerequisites

- Terraform >= 1.6, AWS CLI v2 and Docker with `buildx`, with credentials for the target account
- A machine that can build the project against JobRunr Pro (private repo credentials or the snapshot in `~/.m2`)
- `JOBRUNRPRO_LICENSE` set to a license that allows running N servers

## One-time setup

```sh
terraform -chdir=aws/terraform/shared init
terraform -chdir=aws/terraform/shared apply   # ECR repository, results bucket, IAM role (default region eu-central-1)
aws ssm put-parameter --region eu-central-1 --name /jobrunr-perf/license --type SecureString --overwrite \
  --value "$JOBRUNRPRO_LICENSE"               # nodes read the license from SSM Parameter Store at run time
```

## Every time the code or the JobRunr Pro version changes

```sh
aws/bin/build-and-push.sh                         # against 1.0.0-SNAPSHOT
TOOL_VERSION=9.0.2 aws/bin/build-and-push.sh      # or a released version
```

The image tag contains the git commit (and `-dirty` for uncommitted changes) and the JobRunr Pro version.

## Running

```sh
terraform -chdir=aws/terraform/cluster init
terraform -chdir=aws/terraform/cluster apply -var node_count=3   # c7i.xlarge nodes by default
# or against Aurora (creating the cluster takes about 15 minutes):
# terraform -chdir=aws/terraform/cluster apply -var node_count=3 -var db_type=aurora -var aurora_instance_class=db.r7g.4xlarge
aws/bin/run-cluster.sh Scenario01ProcessJobs 2000000 3        # scenario, amount of jobs, repetitions
terraform -chdir=aws/terraform/cluster destroy
```

To sweep over cluster sizes (keeps the DB machine, destroys everything at the end):

```sh
NODE_COUNTS="1 2 4 8" aws/bin/sweep.sh Scenario01ProcessJobs 2000000 3
TF_VAR_db_type=aurora NODE_COUNTS="1 2 4 8" aws/bin/sweep.sh Scenario01ProcessJobs 2000000 3
```

Results:
- `logbooks/aws/<scenario>-cluster-logbook.csv`: one row per run, with run id, cluster size, instance types and vCPUs
  next to the usual columns
- `logbooks/aws/runs/<run id>/`: logbook, `performance-tests.log` and stdout of every node (not committed)

Useful variables of the cluster stack (see `aws/terraform/cluster/main.tf`): `node_count`, `node_instance_type`,
`db_type`, `db_instance_type`, `aurora_instance_class`, `aurora_postgres_version`, `availability_zone`, `use_placement_group`, `postgres_extra_args` (e.g. `["shared_buffers=8GB"]`),
`postgres_max_connections` and `ttl_hours`. The machines run in the default VPC; they only accept traffic from each
other, which is why the database uses fixed credentials.

## Safety

- Every EC2 machine powers off and terminates after `ttl_hours` (default 6) in case you forget `terraform destroy`.
  Running `terraform destroy` afterwards still cleans up the remaining resources.
- **Aurora has no such safety net**: it keeps running (and billing) until `terraform destroy`.
- Terraform state is kept locally in `aws/terraform/*/terraform.tfstate` (ignored by git). If you lose it, you have to
  find and remove the resources (tagged `Project=jobrunr-perf`) by hand.

## Troubleshooting

- **`No valid credential sources found`**: Terraform doesn't see your AWS credentials. Check `aws sts get-caller-identity`
  works in the same terminal (and `AWS_PROFILE` is exported). With `aws login`, run `terraform init -upgrade` or
  `eval "$(aws configure export-credentials --format env)"`; exported credentials expire, so refresh them for long runs.
- **`apply` hangs on `Still creating...` for an instance**: AWS can't launch it, usually for lack of capacity. Press
  Ctrl-C once, let Terraform stop, then retry with another instance type, `-var availability_zone=...` or (if you
  enabled it) without `use_placement_group`.
- **`not offered in AZ`**: the instance types aren't available in that AZ; the error lists the AZs that offer them.
- **`Error acquiring the state lock`**: a previous Terraform run was killed. Make sure none is running, then
  `terraform -chdir=aws/terraform/cluster force-unlock <lock id>`.
- **Undo everything**: `terraform -chdir=aws/terraform/cluster destroy`, then check that no instance tagged
  `Project=jobrunr-perf` is still running.

## Getting comparable numbers

- Use at least 3 repetitions per configuration and avoid burstable (`t*`) instances.
- Use enough jobs that processing takes several minutes; with many nodes 500k jobs finish too quickly.
- The database is usually the limit in cluster mode, so record and vary `db_instance_type` as well.
- With `db_type=ec2`, defaults match the local runs (Postgres settings, Hikari pool of 40 per node) so results are
  comparable with the Hetzner numbers. Change `postgres_extra_args` or `JAVA_OPTS` deliberately and note it.
- Nodes default to `c7i.xlarge` (4 vCPU, 8 GB): jobs are tiny and the database is the bottleneck. JobRunr's default
  worker count scales with vCPUs, so for a single-node comparison with the Hetzner EX44 use
  `-var node_instance_type=c7i.4xlarge`.
- Nodes run with `-XX:MaxRAMPercentage=75` (local runs use the JVM default of 25%), so the heap depends on the node's
  memory: 6 GB on a c7i.xlarge, 24 GB on a c7i.4xlarge, about 16 GB on the Hetzner EX44.
- Aurora results are a separate series: different storage engine, memory-optimized instance classes only, and possibly
  a different Postgres version (the logbook's DataStore column shows it).
