Unless a section says otherwise: Scenario01ProcessJobs with 2,000,000 jobs, JobRunr Pro 9.0.2, c7i.xlarge nodes with
64 workers and a Hikari pool of 20 min idle / 40 max, Postgres 18.6 on m6id.4xlarge (16 vCPUs on 8 physical cores),
`pg_aggressive_autovacuum=true`, profiling on.

## Postgres: why is the throughput acting like a rollercoaster?

#### Hypothesis 1
Culprit: autovacuum and dead tuples
Verification: run autovacuum more frequently
Status: confirmed

> Optimization: JobRunr could aim at reducing update operations, which would reduce the amount of dead tuples

## Postgres: `idle in transaction | Client | ClientRead`

#### Hypothesis 1
Culprit: network
Verification: measure the Postgres round trip
Status: confirmed for the `idle in transaction` time, not confirmed as the throughput bottleneck

> By Little's law, about 5,330 transactions/s with 2.30 connections waiting in between is about 0.43 ms of gap per transaction; the median `SELECT 1` was 0.395 ms. So the gap is one network round trip: the COMMIT after each job's success update. - Claude

> This explains what the connections wait on, not what limits throughput: 64 workers in parallel hide a 0.4 ms round trip (at 2-3 round trips per job they would reach ~50k jobs/s). - Claude

> Optimization: network latency may be reduced using `use_placement_group=true`. This has been confirmed (see "Node: what caps one node", Hypothesis 4).

#### Hypothesis 2
Culprit: JobRunr operations (e.g., job serialization, param binding, and others happening after starting the transaction)
Verification: time JobRunr's operations
Status: pending

> It's the write path: JobRunr Pro wraps every job state change in its own explicit JDBC transaction, and the transaction is opened before the client does the expensive part (deserialize/reserialize the whole jobAsJson, run filters, bind 19 columns). Postgres therefore sits in idle in transaction / ClientRead — waiting on the JVM, not doing work. - deepseek

## Node: what caps one node at ~5,240 jobs/s?

Settings: 1 node, `max_wal_size=2GB`, `autovacuum_naptime=10s`, round trip to the database ~0.4 ms.

Summary: a node processes jobs per fetch / fetch cycle. Fetches run one at a time, so the fetch cycle is the per-node
ceiling; the round trip (placement group) and the worker count (jobs per fetch) move it, CPU and connections don't.

`PerformanceTestJob` does nothing (0.43 µs on average), yet every worker takes ~12 ms per job (64 / 5,240), of which only ~0.9 ms is in the database (4.5 busy connections / 5,240).

#### Hypothesis 1
Culprit: workers waiting on Hikari connections (pool max 40 for 64 workers, see `AbstractSqlDataStore`)
Verification: sample Hikari every second; rerun with `maximumPoolSize` >= workers
Status: rejected

> async-profiler (`cpu`, `wall=20ms`, `lock=1ms`, `jfrsync=profile`): 41 lock samples of 1 ms or more in the whole run, none in Hikari. One fetch at a time can't use more than one connection, so a bigger pool would not help. - Claude

> The pool does grow to 40 within the first minute, but Postgres sees ~35 of them as `idle`: a checked-out connection stays `idle` until its first statement (pgjdbc sends `BEGIN` lazily). The connections are held by threads that are not talking to the database yet, e.g. serializing JSON. - Claude

#### Hypothesis 2
Culprit: a serialized fetch loop: ~160 fetches/s × ~32 jobs is a ~6 ms cycle, and 32 jobs / 6 ms ≈ 5.3k jobs/s
Verification: async-profiler + JFR; double the workers
Status: confirmed

> There is no fetch thread: when a worker goes idle, `JobSteward.notifyThreadIdle` → `BasicWorkDistributionStrategy.getJobsToProcess` → `DefaultSqlStorageProvider.getJobsToProcess` runs `JobTable.selectJobsToProcess`, moves the ~32 jobs to `PROCESSING` with `JobTable.updateAllJobs` (a JDBC `executeBatch`) and commits. JFR's `jdk.SocketRead` events show the fetches never overlap. 61,462 fetches in 394 s is a 6.4 ms cycle, and 32.5 jobs / 6.4 ms ≈ 5,070 jobs/s, the measured throughput. - Claude

> Optimization: JobRunr could shorten the cycle (e.g. one `UPDATE ... WHERE id = ANY(?)` instead of a batch of ~32 updates, fewer round trips per fetch) or fetch more jobs per cycle.

#### Hypothesis 3
Culprit: the 4 vCPU node is CPU bound
Verification: node CPU every second
Status: rejected

> The node is 76% idle (run queue 1.2), the database 83%. - Claude

#### Hypothesis 4
Culprit: network latency on the serialized path
Verification: `use_placement_group=true`
Status: confirmed

> Round trip 0.444 → 0.070 ms, throughput 5,059 → 7,097 jobs/s (+40%), still ~32.7 jobs per fetch, fetch cycle 6.4 → 4.6 ms. A fetch has 3 round trips (select, batch update, commit) and the job that triggers the next fetch 2 more (update, commit). - Claude

> `max_wal_size` 2 GB → 16 GB makes no difference (5,059 → 4,990 jobs/s without placement group, 7,097 → 6,924 with it). Checkpoints become timed and the "checkpoints are occurring too frequently" warnings go away, but `WALWrite` waits stay: they come from flushing commits. - Claude

#### Hypothesis 5
Culprit: virtual-thread carriers (one per vCPU) pinned during JDBC calls, capping database concurrency at ~4
Verification: async-profiler wall-clock samples of the carriers, JFR `jdk.VirtualThreadPinned`
Status: rejected

> The carriers spend 87% of their wall-clock time idle in `ForkJoinPool.awaitWork`, none parked while pinned, and JFR recorded no `jdk.VirtualThreadPinned` events. - Claude

> Note: `jfr summary` mislabels async-profiler's events (its CPU samples show up as `jdk.DataLoss`); use `jfr-converter.jar --cpu / --wall / --lock` to read them.

## Node: does throughput grow with the worker count?

Settings: 1 node, placement group, `worker_count=128`.

Why a fetch returns ~32 jobs with 64 workers (JobRunr OSS code, measured timings):
- Only a worker that just finished a job can start a fetch (`JobSteward.notifyThreadIdle` → `OnboardNewWorkTask`), and only one fetch runs at a time (`tryLock`).
- A fetch asks for all free workers: `workerCount - occupiedWorkerCount` (`BasicWorkDistributionStrategy`).
- A job is shorter than a fetch, so each fetch takes the workers the previous one left free: 61 → 3 → 61 → 3… ~32 per fetch on average (measured: 32.5).

#### Hypothesis 1
Culprit: jobs per fetch is workers / 2, so more workers means more jobs per fetch
Verification: 128 workers; jobs per fetch from the fetch calls in `pg_stat_statements`
Status: confirmed

> 64 → 128 workers: 32.7 → 67.2 jobs per fetch, 7,097 → 8,905 jobs/s (+25%), fetch cycle 4.6 → 7.5 ms. A fetch costs roughly 1.8 ms + 0.085 ms per job, so more workers give less and less: ~10k jobs/s with 256 workers, never more than ~11.8k per node. - Claude

## Node: Hikari pool not saturated with 64 workers

#### Hypothesis 1
Culprit: at most 32 jobs are competing for a connection
Verification: increase the workers
Status: confirmed (64 workers: 0.01 ms average wait for a connection; 128 workers: 0.40 ms)

> With 128 workers a fetch hands out ~67 jobs at once, more than the 40 connections. Only ~4.3 connections are in use on average (9,040 acquired/s × 0.48 ms held), so the waits come in bursts right after each hand-out. - Claude

## Node: would running `getJobsToProcess` again immediately help?

Verification: re-run `getJobsToProcess` if work is still pending
Status: pending

> Measured gap: a worker that finishes during a fetch can't start the next one (`tryLock`), so the next fetch waits for the first job of the current batch to finish. With `log_data_store_timings=true` (placement group, `max_wal_size=16GB`) that gap is 20% of the cycle at every cluster size: 0.98 of 4.85 ms on 1 node, 1.44 of 7.15 ms on 3, about one success `saveJob` (0.92 / 1.85 ms). So the possible gain is ~25%, not 40%. - Claude

## Cluster: why does throughput stop scaling from 3 nodes?

Settings: `aws/bin/sweep.sh` with 1-4 nodes, 64 workers per node, placement group. `fetch cycle` = (fetches / node) /
processing duration, `jobs/fetch` = 2,000,000 / fetches, statement times are means from `pg_stat_statements`, the claim
update per row.

Summary: a node's throughput is jobs per fetch / fetch cycle, and jobs per fetch doesn't change with the cluster size,
so all of the loss is the fetch cycle growing (4.4 → 8.8 ms from 1 to 4 nodes). The database has spare capacity: its
CPU, its buffer pool, the WAL volume and durability, the stats query and the stray row locks are all ruled out. What's
left is that every node claims from the same few pages, and the cost of working there grows with the whole cluster's
throughput (Hypothesis 6).

`max_wal_size=2GB`, `shared_buffers` 128 MB:

| nodes | jobs/s | per node | jobs/fetch | fetch cycle | fetch select | claim update | success update | DB busy | busy conns | `Lock transactionid` |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 6,999 | 6,999 | 32.6 | 4.4 ms | 0.36 ms | 0.032 ms | 0.23 ms | 20% | 4.3 | 0 |
| 1, 128 workers | 8,905 | 8,905 | 67.2 | 7.5 ms | 0.63 ms | 0.030 ms | 0.20 ms | 22% | 4.6 | 0 |
| 2 | 11,009 | 5,504 | 32.6 | 5.9 ms | 0.71 ms | 0.041 ms | 0.55 ms | 37% | 11.1 | 1.2 |
| 3 | 12,901 | 4,300 | 32.5 | 7.6 ms | 1.21 ms | 0.053 ms | 1.04 ms | 54% | 26.7 | 8.1 |
| 4 | 14,825 | 3,706 | 32.4 | 8.8 ms | 1.57 ms | 0.063 ms | 1.43 ms | 63% | 35.8 | 12.5 |
| 1, smart queue | 10,692 | 10,692 | 319 | 30 ms | 1.84 ms | 0.032 ms | 0.11 ms | 20% | 7.4 | 0 |
| 3, smart queue | 21,021 | 7,007 | 317 | 45 ms | 5.69 ms | 0.054 ms | 0.80 ms | 50% | 43.4 | 2.0 |
| 3, smart queue | 20,196 | 6,732 | 318 | 47 ms | 7.91 ms | 0.058 ms | 0.95 ms | 62% | 45.0 | 4.5 |

> Throughput per node follows jobs per fetch / cycle to the percent (20,196 jobs/s on 3 nodes = 3 × 318 / 47 ms), and the loop is busy the whole run (fetches per node × cycle = 100% of the processing time). The nodes are not the limit: 0.7-1.3 of 4 vCPUs busy, a Hikari connection waited for 0.07 ms, and the claim loop is 11.6% of a node's CPU samples (53% is the job-save path, 12% Jackson). - Claude

Where the cycle goes, from the client (`log_data_store_timings=true`, `max_wal_size=16GB`, means per fetch on node 0):

| nodes | jobs/s | fetch cycle | claim batch update | fetch select | rest of the fetch (commit, JVM) | gap until the next fetch |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 6,755 | 4.85 ms | 2.28 ms | 1.14 ms | 0.45 ms | 0.98 ms |
| 2 | 11,050 | 5.91 ms | 2.66 ms | 1.63 ms | 0.47 ms | 1.18 ms |
| 3 | 13,684 | 7.15 ms | 3.21 ms | 2.01 ms | 0.53 ms | 1.44 ms |

> The 2.3 ms of growth from 1 to 3 nodes is spread: claim +0.93 ms, select +0.87 ms, gap +0.46 ms. No single statement is the problem; every statement on `jobrunr_jobs` gets slower. The gap is about one success `saveJob`, so the success update is partly on the serialized path (~20% of the cycle). - Claude

#### Hypothesis 1
Culprit: stray row locks. The fetch is `SELECT ... FOR UPDATE SKIP LOCKED`. If node A claims job X while node B's select is running, B can still see X as `ENQUEUED`, locks it, then drops it because it is no longer `ENQUEUED`; Postgres keeps that lock until B's fetch transaction ends, and A's success update of X waits for it.
Verification: sample `pg_blocking_pids()` for the sessions waiting on `transactionid`; commit the claim on its own
Status: confirmed as a mechanism, rejected as the cause of the scaling loss

> `pg_blocking_pids()` (3 nodes): 73-87% of the `transactionid` waiting is blocked by a connection of another node, almost always the claim or its `COMMIT`. - Claude

> Claim committed on its own (3 nodes, JobRunr 1.0.0-SNAPSHOT): `transactionid` waits drop from 4.6 to 0.04 connections and the success update from ~1.0 to 0.49 ms, but throughput stays at 11,604 jobs/s (9.0.2: 12,901-13,135). The fetch cycle got longer instead (8.4 vs 7.4-7.6 ms): the separate claim adds ~0.8 ms of database work and a second commit per fetch. A claim in the same transaction as the batch update (`WITH claimed AS (UPDATE ...)`) holds its locks just as long and adds a third update per job: 11,064 jobs/s. - Claude

#### Hypothesis 2
Culprit: the scenario monitor runs `select * from jobrunr_jobs_stats` on every node about once a second: a parallel scan of the whole table (leader + 2 workers), 200-750 ms per call
Verification: compare throughput in the seconds with 0, 1, 2 or 3+ stats queries running (`DIAGNOSTICS=true`)
Status: rejected within a run; the aggregate load matters at 6-8 nodes (see below)

> Within a 3-node run (`max_wal_size=16GB`) throughput is the same with and without it: ~26k `jobrunr_jobs` updates/s before the monitors start (0 running), ~26.5k with 3 running, ~26k when only 1 is left near the end. That holds although at 3 nodes it uses ~40% of the database's CPU (3.0 parallel workers on CPU, 1.9 vCPUs of exited children) and holds the oldest snapshot 61% of the time. - Claude

> Earlier, two runs where the monitor happened to fire 15-18 times instead of 500+ were the best of their series (3 nodes plain: 14,225 vs 13,135 jobs/s; 3 nodes smart queue: 21,021 vs 20,196). Still worth removing from the cluster setup: it's load that grows with the node count without doing work, and it blurs the database measurements. - Claude

> At 8 nodes the aggregate load is large enough to matter across runs: the unthrottled 128 MB run (83 calls per node, 1.2 s each) did 17,759 jobs/s; rate-limiting the monitor to 1/30 s on the same 128 MB setup did 23,244 (+31%). Within a run the monitor is always on, which is why the within-run comparison did not show it. - Claude

#### Hypothesis 3
Culprit: relation extension: lock holders queue on `Lock extend` to add pages to `jobrunr_jobs`, so they hold their row locks longer
Verification: compare `Lock extend` and `transactionid` waits
Status: rejected: `Lock extend` is 0.1-0.6 of 19-45 busy connections in the 3- and 4-node runs, against 2.0-12.5 on `transactionid`

#### Hypothesis 4
Culprit: the shared write path: every job writes two full row versions of a ~2 kB `jobAsJson` (0% HOT updates) and commits twice, a cost per job, not per node
Verification: vary the write path at 3 nodes
Status: rejected

| 3 nodes, `PG_SETTINGS` / extra arg | jobs/s | fetch select | claim update | success update | DB busy | `WALWrite` | `transactionid` |
|---|---:|---:|---:|---:|---:|---:|---:|
| `max_wal_size=2GB` (baseline) | 14,225 | 0.955 ms | 0.050 ms | 0.895 ms | 43% | 2.33 | 4.90 |
| `max_wal_size=16GB` | 14,287 | 0.954 ms | 0.049 ms | 0.830 ms | 50% | 1.60 | 5.19 |
| `max_wal_size=16GB` | 13,632 | n/a | n/a | n/a | 50% | n/a | n/a |
| `max_wal_size=16GB` + `pg_fillfactor=50` | 13,689 | n/a | n/a | n/a | 52% | 2.75 | 5.24 |
| `synchronous_commit=off` | 13,957 | 1.194 ms | 0.051 ms | 0.943 ms | 54% | 0 | 4.25 |
| `max_wal_size=16GB`, 1.0.0-SNAPSHOT (dbb2aef97) | 13,559 | 0.979 ms | 0.049 ms | 0.893 ms | 51% | 2.06 | 5.48 |

> `synchronous_commit=off` takes the `WALWrite` waits away and changes nothing; `max_wal_size=16GB` removes the requested checkpoints and changes nothing. WAL is ~2.7 kB per `jobrunr_jobs` update at 1, 2 and 3 nodes, i.e. the same work per job. - Claude

#### Hypothesis 5
Culprit: the `LWLock BufferContent` waits are on heap pages: with `fillfactor` 100 every update writes its new row version to another page, and the concurrent updates all land on the few pages that still have free space
Verification: `pg_fillfactor=50` at 3 nodes, back-to-back with a run without it
Status: rejected

> 13,689 jobs/s against 14,287 and 13,632 without it. The fillfactor did its job (8,101 MB heap, 60% of the updates kept their row version on the same page) and `BufferContent` did not drop (2.41 → 3.53 connections); the fetch select got 27% more expensive on the doubled heap. - Claude

> The `BufferContent` waits are on the success update (3.4 connections at 3 nodes), next to its `transactionid` waits (5.6); the claim and the fetch select hardly wait (0.08). - Claude

#### Hypothesis 6
Culprit: the fetch's *latency*, not the database's capacity. Each node runs one serialized fetch, and every node's fetch works at the same head of `jobrunr_job_to_process_idx` and the same last pages of the indexes, while the whole cluster rewrites them. So its cost grows with the cluster's throughput, not the node's.
Verification: index entries read per fetch and the statements' blocks per call (`DIAGNOSTICS=true`); then change only the claim path (workers per node, per-node claim ranges)
Status: supported by the data, not tested by changing the claim path yet

`max_wal_size=16GB`, `DIAGNOSTICS=true`, `log_data_store_timings=true`:

| nodes | jobs/s | index entries read per fetch | live rows fetched per fetch | fetch select | blocks hit per select | claim update per row | blocks per claimed row | success update |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 6,050 | 584 | 32.6 | 0.50 ms | 270 | 0.033 ms | 23.7 | 0.30 ms |
| 2 | 11,047 | 1,165 | 55 | 0.62 ms | 510 | 0.039 ms | 23.8 | 0.46 ms |
| 3 | 12,976 | 2,035 | 81 | 1.16 ms | 726 | 0.052 ms | 23.8 | 1.00 ms |

> The fetch select does 3.5× more work for the same ~32.6 jobs. `jobrunr_job_to_process_idx` is ordered by `dynamicqueue, priority, updatedat, servertag`, without `state`, so every scan starts at the oldest jobs and steps over the entries of jobs the whole cluster claimed recently that aren't marked dead yet, and over rows the other nodes have locked (live rows fetched 32.6 → 81, the extra ones are skipped). The entries read grow with the cluster's throughput. An entry can only be marked dead once no snapshot can see its row version any more; why that takes long enough to leave ~2,000 entries is not proven, since removing the stats query's snapshots (Hypothesis 2) changed nothing. - Claude

> The claim update does the same work per row (23.8 blocks) 57% slower, almost all of it on CPU (0.99 connections on CPU, 0.08 waiting at 3 nodes). That fits contention on the same hot index and heap pages (shared cache lines, not long enough to show up as waits), or hyperthreads sharing a core (Hypothesis 7); this data can't separate them. - Claude

> `jobrunr_jobs` has 16 indexes, but 9 stay empty (8 kB, most likely partial indexes these jobs never match), so with 0% HOT updates each update writes the other ~7: Btree records are 22% of the WAL bytes, ~5 per update. Three of those 7 are written but never read while processing: `jobrunr_job_state_updated_at_idx` (88 MB), `jobrunr_job_delete_at_idx` (75 MB) and `jobrunr_job_signature_idx` (25 MB). - Claude

#### Hypothesis 7
Culprit: the database runs out of CPU: 51% of 16 vCPUs is about all 8 physical cores, with a run queue of 13 at 3 nodes
Verification: CPU per vCPU and physical core from `/proc/stat`, CPU per Postgres process type (`DIAGNOSTICS=true`)
Status: rejected for capacity; hyperthread sharing open

> 3 nodes: 8.5 vCPUs busy, cores in use 4.4 of 8 (a core counted as busy as its busiest vCPU), no second with every core ≥ 90% busy, and taking ~3 vCPUs of stats-query work away (Hypothesis 2) changes nothing. Cores with both hyperthreads busy do grow: 1.4 (1 node) → 2.5 → 4.1 (3 nodes), in step with the claim's slowdown per row. A 3-node run on m6id.8xlarge would tell: if the claim drops back to ~0.033 ms per row, it's the hyperthreads. - Claude

#### Hypothesis 8
Culprit: `shared_buffers` at its 128 MB default: the stats query streams 70-100k buffers per call through it and pushes out the pages the fetch uses
Verification: `PG_SETTINGS="max_wal_size=16GB shared_buffers=16GB"` at 3 nodes
Status: rejected at 3 nodes; ~5-10% gain at 6-8 nodes (see below)

> 13,295 jobs/s against 13,599-13,684 at 128 MB; fetch select 0.958 vs 1.019 ms, claim 0.049 vs 0.051 ms per row, success update 0.939 vs 0.899 ms. Backend evictions (`pg_stat_io`) don't grow with the node count either (1.80M → 1.67M from 1 to 3 nodes). - Claude

## Cluster: the database instance, not the node, sets the plateau

Settings unless stated otherwise: `Scenario02ProcessJobsInDynamicQueues`, 2,000,000 jobs, 64 workers per node,
`max_wal_size=16GB`, `shared_buffers=16GB`, and the scenario monitor rate-limited to 1/30 s. Runs from 2026-11-02 and
2026-11-03; values are medians with the number of runs in parentheses. The 128 MB and unthrottled runs from 10-28/10-30
are a separate phase (see shared_buffers below).

| database | 1 node | 2 nodes | 4 nodes | 6 nodes | 8 nodes |
|---|---:|---:|---:|---:|---:|
| `m6id.4xlarge` | 7,190 (2) | 12,691 (4) | 20,887 (3) | 24,276 (3) | 25,051 (2) |
| `m6id.8xlarge` | 7,299 (1) | 13,925 (1) | 24,520 (1) | 31,840 (1) | 36,818 (1) |
| per node, 4xlarge | 7,190 | 6,346 | 5,222 | 4,046 | 3,131 |
| per node, 8xlarge | 7,299 | 6,962 | 6,130 | 5,307 | 4,602 |

> The 4xlarge plateaus at ~24-25k from 6 nodes (6 → 8 nodes is +3%); the 8xlarge is still scaling at 8 nodes
> (6 → 8 is +16%). A 5 x `m6id.12xlarge` run reached 29,749. The 6 x 12xlarge run (20261102T141005Z) is not a 6-node
> point: only 3 worker profiles exist, start spread is n/a, and 2M jobs finished in 105.6 s, so exclude it. The 6-node
> 4xlarge run 20261030T164904Z is also excluded (1,388,346 of 2,000,000 succeeded). - Claude

> At 8 nodes the 4xlarge is at the physical core wall: 64% user + 21% system, 7.0 of 8 cores in use, both
> hyperthreads busy on 6.9 cores, run queue 41.5. The 8xlarge is not: 40% + 15%, 9.6 of 16 cores in use, 8.6 with both
> hyperthreads busy, run queue 22.4. Doubling the database removes the CPU wall, but per-statement times still grow
> with cluster throughput (success update 0.316 → 0.667 ms from 4 to 8 nodes on 8xlarge), so the remaining limit is
> contention on the hot index/heap pages (Hypothesis 6), not CPU. - Claude

> At one node both databases give ~7.2-7.3k jobs/s, so that ceiling is the node's serialized fetch loop, not the
> database. The database only becomes the limit from about 4 nodes; at 8 nodes a node runs at 3.1k (4xlarge) or 4.6k
> (8xlarge), well below its single-node ~7.3k. - Claude

> Scenario shape at equal settings (10-28/10-30, 128 MB, unthrottled monitor): at 3 nodes dynamic queues reached
> 15,261, plain `Scenario01` 12,976-14,263 and node queues 12,889. Node queues also have an end spread of 8.6-9.4 s at
> 2-3 nodes: the static per-node queue leaves nodes idle at the tail, so it is not a good plateau comparison. - Claude

### `idle in transaction` was never gone

Settings: the 16 GB series above.

| database / nodes | busy conns | idle in tx / ClientRead | share of busy | idle in tx per job | active `BufferContent` | jobs/s |
|---|---:|---:|---:|---:|---:|---:|
| 4xlarge, 4 | 20.5 | 3.10 | 15.2% | 0.155 ms | 3.57 | 19,995 |
| 4xlarge, 8 | 62.8 | 5.58 | 8.9% | 0.226 ms | 25.32 | 24,638 |
| 8xlarge, 4 | 19.0 | 3.70 | 19.4% | 0.151 ms | 2.55 | 24,520 |
| 8xlarge, 8 | 40.9 | 8.93 | 21.8% | 0.243 ms | 7.69 | 36,818 |

> The absolute gap per job stays at ~0.15-0.24 ms. Its share rises when the database stops being the limit: from 4 to 8
> nodes on 4xlarge, `BufferContent` grows from 3.6 to 25.3 connections and WALWrite from 0.6 to 5.8, so the idle in
> transaction share falls to 8.9%. On 8xlarge at 8 nodes those waits are 7.7 and 2.1, so the same client gap is 21.8%.
> The gap is still the success `saveJob` (7.94 of 8.93 connections at 8xlarge/8; 4.89 of 5.58 at 4xlarge/8): the
> transaction is open before JobRunr does the client-side work, and Postgres waits in ClientRead. - Claude

> Most of that client work is the detailed query logging, not job work: `TimedPreparedStatement.close()` calls
> `getSqlWithValues()` after the UPDATE has returned and before the transaction commits. It rebuilds the whole UPDATE
> with all 19 bound values, including the ~2 KB `jobAsJson`, using regex, `SimpleDateFormat` and `StringBuilder`. Of the
> CPU samples inside `saveJob` on 8xlarge/8, ~25% are `getSqlWithValues` (with Pattern/SimpleDateFormat/StringBuilder),
> ~23% Jackson serialization plus `Sql.setParams`, ~21% the database execute/socket read and ~8% the commit round trip.
> This is instrumentation from `log_data_store_timings=true`, which every run has; with it off, the client-side gap and
> the idle in transaction share should both drop. - Claude

> The 0.07 ms `SELECT 1` round trip is not part of the idle gap: the gap ends when the client sends COMMIT, and the
> commit round trip happens after. JFR's wall samples inside `saveJob` put `Unsafe.unpark`/carrier wake at ~5% and
> `getSqlWithValues` at ~36%, so two virtual-thread hops do not explain the ~0.15 ms. - Claude

> This is a composition effect, not a new bottleneck: dividing the idle connections by throughput gives the same
> ~0.2 ms per job at both database sizes. It becomes the limiting path only if the database gets faster still or the
> workers are raised much further. - Claude

### `shared_buffers`: 128 MB vs 16 GB

Clean comparisons, i.e. both sides with the same monitor rate. `saveJob` is the mean client-side method duration from
the per-node report.

| nodes | 128 MB | 16 GB | delta | `saveJob` 128 MB → 16 GB | client-backend relation evictions |
|---:|---:|---:|---:|---|---|
| 3 (Scenario01) | 13,684 | 13,295-13,599 | no gain | 1.85 → 1.95-2.04 ms | not recorded |
| 4 | 19,739 | 19,995-20,891 | up to +6% | 1.44 → 1.37 ms | 1.91M → 0 |
| 8 | 23,244 | 24,638-25,465 | +6 to +10% | 3.27 → 2.82 ms | 1.98M → 0 |

> At 8 nodes the 128 MB run also did 1.52M relation reads and 1.52M writes by client backends; 16 GB did 93 reads and 0
> writes, and the stats query dropped from 1.20 s to 0.50 s. At 3 nodes 128 MB is enough (the 128 MB runs range from
> 12,976 to 14,263), so 16 GB gives no gain there. - Claude

> The 6-node comparison (17,962 at 128 MB against 24,078-24,692 at 16 GB) is not a shared_buffers result: the 128 MB run
> is the last unthrottled one (90 `getJobStats` calls per node, 1.07 s each, 4.90M evictions) while the 16 GB runs have
> 4 calls per node. At 8 nodes, rate-limiting the monitor alone takes a 128 MB run from 17,759 to 23,244 (+31%);
> `shared_buffers=16GB` adds ~7% on top. So 16 GB removes buffer churn and buys ~5-10% at 6-8 nodes, but it does not
> move the 4xlarge plateau. The 8xlarge gain is cores and memory bandwidth, not shared_buffers. - Claude

Next, cheapest first:
- A/B the monitor rate limiter at 6-8 nodes on the same DB instance: the 8-node 128 MB comparison above suggests it is
  worth more than `shared_buffers`, but it has not been isolated in one series.
- A/B `shared_buffers` 4/8/16 GB at 8 nodes to find the knee; the job table is ~5 GB with ~1 GB of indexes.
- 10-12 nodes on `m6id.8xlarge`: it is not CPU-saturated at 8 nodes, so the next plateau is either the hot pages/index
  or the per-node fetch loop.
- 8 nodes with `worker_count=128`: more jobs per fetch, which shortens the fetch loop and was +25% on 1 node.
- JobRunr side: the claim rewrites `jobAsJson` and ~7 indexes, and it is on the critical path, so making it cheap
  (state-only update, no JSON rewrite, fewer indexes) or claiming more jobs per fetch is worth more than any Postgres
  setting tested so far.
