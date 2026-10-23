# Running the JobRunr Pro performance tests on AWS

Runs a JobRunr Pro scenario on N application nodes sharing one Postgres database, so you can compare instance types and
measure how throughput scales in cluster mode.

> This setup is new: report what breaks and see [Troubleshooting](#troubleshooting).

## How a run works

- **Database**, chosen with `db_type`:
  - `ec2` (default): `postgres:18.6` in Docker on the DB machine, tuned for this workload (see [Options](#options)).
    The data directory is on local NVMe instance storage, so `db_instance_type` must have it (`m6id`, `i4i`, `c6id`, ...).
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

The machines run in the default VPC; they only accept traffic from each other, which is why the database uses fixed
credentials. See [Options](#options) for all cluster variables.

## Options

### `terraform -chdir=aws/terraform/cluster apply`

Pass with `-var <name>=<value>` or `TF_VAR_<name>`; see `aws/terraform/cluster/main.tf` for the descriptions.

| variable | default | |
|---|---|---|
| `node_count` | `1` | `1` = single-node baseline |
| `node_instance_type` | `c7i.xlarge` | JobRunr's worker count scales with vCPUs |
| `db_type` | `ec2` | `ec2` (Postgres on local NVMe) or `aurora` |
| `db_instance_type` | `m6id.4xlarge` | `db_type=ec2` only; must have local NVMe (`m6id`, `c6id`, `i4i`, ...) |
| `aurora_instance_class` | `db.r7g.4xlarge` | `db_type=aurora` only |
| `aurora_postgres_version` | `18` | `db_type=aurora` only; the latest minor is used |
| `availability_zone` | auto | defaults to the first AZ that offers both instance types |
| `use_placement_group` | `false` | lower network latency; launches can fail for lack of capacity |
| `ttl_hours` | `6` | machines terminate themselves after this |
| `postgres_max_connections` | `500` | must fit `node_count * 40 + 10` |
| `postgres_extra_args` | `[]` | `db_type=ec2` only: extra Postgres settings, appended after the built-in ones (e.g. `["shared_buffers=8GB"]`); changing them recreates the DB machine |

The `db_type=ec2` Postgres tuning (sized for the write churn of these scenarios) lives in `local.postgres_settings` in
`aws/terraform/cluster/main.tf`, together with the reasoning per value. `postgres_extra_args` is appended last, so the
last value wins.

### `aws/bin/build-and-push.sh`

| env | default | |
|---|---|---|
| `TOOL_VERSION` | `1.0.0-SNAPSHOT` | JobRunr Pro version to build against |
| `IMAGE_TAG` | `<git sha>[-dirty]-pro-<TOOL_VERSION>` | written to `aws/.image-tag`, picked up by `run-cluster.sh` |

### `aws/bin/run-cluster.sh [scenario] [amount] [repetitions] [key=value]...`

- Positional: scenario (`Scenario01ProcessJobs`), amount (`2000000`), repetitions (`1`).
- Environment: `IMAGE_TAG` (from `aws/.image-tag`), `JAVA_OPTS` (`-XX:MaxRAMPercentage=75`), `START_DELAY_SECONDS`
  (`120`), `EXECUTION_TIMEOUT_SECONDS` (`7200`; must exceed `max_duration` + `START_DELAY_SECONDS`).
- The `key=value` extras are appended to the test's `exec.args` and take the same parameters as the local runner, e.g.
  `max_duration=PT2H` or `log_data_store_timings=true` — see [Extra parameters](../README.md#extra-parameters).

### `aws/bin/sweep.sh`

Applies the cluster for every node count, runs the scenario, and destroys everything at the end.

- Environment: `NODE_COUNTS` (`1 2 4`), `NODE_INSTANCE_TYPE` (`c7i.xlarge`), `DB_INSTANCE_TYPE` (`m6id.4xlarge`).
- Any other cluster variable via `TF_VAR_*`, e.g. `TF_VAR_db_type=aurora`.

## Recommended machines

All of these are offered in `eu-central-1`. Prices are on-demand Linux list prices for Frankfurt (October 2026), per
machine and hour, without the root volume (about $0.01/h) or data transfer; check the
[pricing page](https://aws.amazon.com/ec2/pricing/on-demand/) before a big sweep. Intel/AMD and Graviton (`*g`)
machines both work: the image is multi-arch (amd64 and arm64) and Terraform picks the matching AMI per machine. Keep
amd64 as the reference series, since the local/Hetzner runs are amd64 too.

The observations are what we expect, not what we measured. Replace them with results once a configuration has run.

### Worker nodes (`node_instance_type`)

| instance | vCPU / GB | $/h | why try it | expected |
|---|---|---|---|---|
| `c7i.xlarge` | 4 / 8 | 0.20 | default, Intel Sapphire Rapids | Baseline. Node CPU stays low because the database is the limit. |
| `c8g.xlarge` | 4 / 8 | 0.18 | Graviton4, cheapest of the current generation | About the same throughput as `c7i.xlarge` for 10% less; best cost per job if the DB is the limit. |
| `c7g.xlarge` | 4 / 8 | 0.16 | Graviton3, cheapest option | Close to `c8g.xlarge` while nodes are mostly waiting on the DB; falls behind when the node CPU matters. |
| `c7a.xlarge` | 4 / 8 | 0.23 | AMD, every vCPU is a full core (no SMT) | Highest throughput per 4-vCPU node when the node CPU matters (e.g. 1–2 nodes); no difference when DB-bound. |
| `c8i.xlarge` | 4 / 8 | 0.21 | newest Intel | A few % over `c7i.xlarge` for 5% more; mainly to check the newest generation. |
| `m7i.xlarge` | 4 / 16 | 0.24 | same CPU as `c7i`, twice the memory (12 GB heap); db-scheduler's 4-core nodes | Same throughput as `c7i.xlarge`; if not, GC or heap size matters for the scenario. |
| `m7i.large` | 2 / 8 | 0.12 | db-scheduler's 2-core nodes | Like `c7i.large`, with enough heap that memory is not a factor. |
| `c7i.large` / `c8g.large` | 2 / 4 | 0.10 / 0.09 | many small nodes | More servers means more JobRunr coordination (heartbeats, master election, polling): expect lower throughput than half as many `xlarge` nodes. |
| `c7i.4xlarge` / `c8g.4xlarge` | 16 / 32 | 0.81 / 0.73 | single-node comparison with the Hetzner EX44, or few big nodes | 4× the workers per node but still 40 Hikari connections: more throughput per node, but the DB limit is reached with fewer nodes. |

With more than 12 nodes, raise `postgres_max_connections` (`node_count * 40 + 10`).

### Database (`db_instance_type`, `db_type=ec2`)

Needs local NVMe (a `d` in the family name, or `i*`). Postgres runs with `shared_buffers` at its default (128 MB), so
extra memory only helps as OS page cache; set `postgres_extra_args='["shared_buffers=..."]'` when comparing memory
sizes. Only the first NVMe disk is used, so sizes with several disks (`m6id.12xlarge` and up, `i4i.8xlarge` and up) get
more CPU and memory but not more disk throughput.

| instance | vCPU / GB | NVMe | $/h | why try it | expected |
|---|---|---|---|---|---|
| `c6id.xlarge` / `c8gd.xlarge` | 4 / 8 | 237 GB | 0.23 / 0.23 | smallest sensible DB (Intel / Graviton4) | Like `m6id.xlarge` while the job table fits in 8 GB; with millions of jobs, reads start hitting the disk. |
| `m6id.xlarge` | 4 / 16 | 237 GB | 0.29 | small DB | DB CPU saturates with 1–2 nodes; shows how quickly the DB becomes the limit. |
| `r6id.xlarge` | 4 / 32 | 237 GB | 0.36 | db-scheduler's 4-core Postgres | Same plateau as `m6id.xlarge`: the extra memory isn't used (see `shared_buffers` above). |
| `m6id.2xlarge` | 8 / 32 | 474 GB | 0.57 | medium DB | Roughly half the plateau of `m6id.4xlarge`. |
| `r6id.2xlarge` | 8 / 64 | 474 GB | 0.73 | db-scheduler's 8-core Postgres | Same plateau as `m6id.2xlarge`. |
| `m6id.4xlarge` | 16 / 64 | 950 GB | 1.14 | default | Baseline. |
| `m8gd.4xlarge` | 16 / 64 | 950 GB | 1.11 | Graviton4, same size, slightly cheaper | Higher plateau than `m6id.4xlarge` (newer CPU, more memory bandwidth) for the same price. |
| `c6id.4xlarge` | 16 / 32 | 950 GB | 0.93 | same CPU, half the memory | Same throughput as `m6id.4xlarge` as long as the working set fits in memory; cheapest 16-vCPU DB. |
| `r6id.4xlarge` | 16 / 128 | 950 GB | 1.45 | same CPU, twice the memory | No gain over `m6id.4xlarge` unless `shared_buffers` is raised and the job table outgrows memory. |
| `i4i.4xlarge` | 16 / 128 | 3750 GB | 1.64 | storage-optimized NVMe (more IOPS, lower write latency) | If this beats `r6id.4xlarge`, WAL/fsync latency is the limit, not CPU. |
| `m8gd.8xlarge` / `m6id.8xlarge` | 32 / 128 | 1900 GB | 2.22 / 2.28 | big DB | Higher plateau if the DB CPU is the limit; if not, lock contention on the jobs table is. |

### Database (`aurora_instance_class`, `db_type=aurora`)

I/O-Optimized, single writer. Aurora only offers memory-optimized classes (the smallest non-burstable one is 2 / 16,
so there is no 4 / 8); compare with `r6id` / `i4i` rather than `m6id`. Both Graviton (`db.r7g`, `db.r8g`) and Intel
(`db.r7i`, `db.r6i`) classes are offered.

| instance | vCPU / GB | $/h | expected |
|---|---|---|---|
| `db.r7g.large` | 2 / 16 | 0.43 | Smallest useful size; saturates with a single node. |
| `db.r7g.xlarge` | 4 / 32 | 0.87 | db-scheduler's 4-core Postgres. |
| `db.r7g.2xlarge` | 8 / 64 | 1.73 | Medium; db-scheduler's 8-core Postgres. |
| `db.r7g.4xlarge` / `db.r8g.4xlarge` | 16 / 128 | 3.46 / 3.46 | Default / Graviton4 at the same price. Expect a lower plateau than `ec2` on local NVMe with the same vCPUs: commits wait for network storage. |
| `db.r7i.4xlarge` | 16 / 128 | 3.64 | Intel, to compare with the amd64 `ec2` series; about the same as `db.r7g.4xlarge`. |
| `db.r7g.8xlarge` | 32 / 256 | 6.92 | Big. |
| `db.r7g.16xlarge` | 64 / 512 | 13.84 | Very big; only if the 8xlarge still scales. |

Example: 4 × `c8g.xlarge` with an `m6id.4xlarge` DB costs about $1.87/h; the same nodes with `db.r7g.4xlarge` about
$4.19/h.

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
- With `db_type=ec2` the Hikari pool (40 per node) matches the local runs, but the Postgres settings are tuned for the
  write churn of this scenario (the defaults are in `local.postgres_settings` in `aws/terraform/cluster/main.tf`) and
  therefore differ from the local/Hetzner runs, which use the Testcontainers defaults. Change
  `postgres_extra_args` or `JAVA_OPTS` deliberately and note it.
- Nodes default to `c7i.xlarge` (4 vCPU, 8 GB): jobs are tiny and the database is the bottleneck. JobRunr's default
  worker count scales with vCPUs, so for a single-node comparison with the Hetzner EX44 use
  `-var node_instance_type=c7i.4xlarge`, or pin it with `worker_count=<n>` (passed to `run-cluster.sh` as an extra).
- Nodes run with `-XX:MaxRAMPercentage=75` (local runs use the JVM default of 25%), so the heap depends on the node's
  memory: 6 GB on a c7i.xlarge, 24 GB on a c7i.4xlarge, about 16 GB on the Hetzner EX44.
- Aurora results are a separate series: different storage engine, memory-optimized instance classes only, and possibly
  a different Postgres version (the logbook's DataStore column shows it).
