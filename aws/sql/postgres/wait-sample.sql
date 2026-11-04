-- 'query id: start' without characters that need CSV or Markdown quoting
CREATE FUNCTION pg_temp.statement_label(query_id bigint, query text) RETURNS text LANGUAGE sql AS $$
  SELECT concat_ws(': ', query_id, left(regexp_replace(query, '[\s,"|`]+', ' ', 'g'), 70))
$$;

-- columns: sampled_at_ms, kind, then per kind; one 'sample' row per second, also without activity
CREATE FUNCTION pg_temp.wait_sample()
  RETURNS TABLE (sampled_at_ms bigint, kind text, c3 text, c4 text, n bigint, c6 text, c7 text, c8 text, c9 text)
  LANGUAGE plpgsql AS $$
DECLARE
  sampled_at bigint := (extract(epoch FROM clock_timestamp()) * 1000)::bigint;
  waits_before text[];
  activity pg_stat_activity[];
BEGIN
  -- lock waits are read live before and after the pg_stat_activity snapshot; only a wait present in both reads
  -- spans the snapshot, so its state and statements are from that wait
  waits_before := ARRAY(SELECT concat_ws('/', l.pid, blocker, l.locktype, l.database, l.relation, l.page, l.tuple,
                                         l.virtualxid, l.transactionid, l.classid, l.objid, l.objsubid, l.waitstart)
                        FROM pg_locks l, unnest(pg_blocking_pids(l.pid)) AS blocker WHERE NOT l.granted);
  activity := ARRAY(SELECT a FROM pg_stat_activity a WHERE pid <> pg_backend_pid());

  RETURN QUERY
  SELECT sampled_at, 'sample', '', '', 0::bigint, '', '', '', ''
  UNION ALL
  -- client: wait event type, wait event, count, the statement (the last one if idle in transaction), state
  SELECT sampled_at, 'client', coalesce(a.wait_event_type, 'CPU'), coalesce(a.wait_event, 'CPU'), count(*),
         pg_temp.statement_label(a.query_id, a.query), a.state, '', ''
  FROM unnest(activity) a
  WHERE a.backend_type = 'client backend' AND a.usename = current_user AND a.state <> 'idle'
  GROUP BY a.state, a.wait_event_type, a.wait_event, 6
  UNION ALL
  -- backend: the other processes that are not idle in their main loop, by type and CPU or wait event type
  SELECT sampled_at, 'backend', a.backend_type, coalesce(a.wait_event_type, 'CPU'), count(*), '', '', '', ''
  FROM unnest(activity) a
  WHERE a.backend_type <> 'client backend' AND a.wait_event_type IS DISTINCT FROM 'Activity'
  GROUP BY a.backend_type, a.wait_event_type
  UNION ALL
  -- load: stats queries running, jobrunr_jobs updates so far, parallel workers, WAL bytes so far
  SELECT sampled_at, 'load',
         (count(*) FILTER (WHERE a.backend_type = 'client backend' AND a.state = 'active' AND a.query LIKE '%jobrunr_jobs_stats%'))::text,
         (SELECT n_tup_upd FROM pg_stat_user_tables WHERE relname = 'jobrunr_jobs')::text,
         count(*) FILTER (WHERE a.backend_type = 'parallel worker'),
         (pg_current_wal_lsn() - '0/0'::pg_lsn)::text, '', '', ''
  FROM unnest(activity) a
  UNION ALL
  -- horizon: the backend with the oldest xmin; statement, ms since its statement started, xid age, backend type, state.
  -- A lazy VACUUM's xmin doesn't hold back the horizon (PROC_IN_VACUUM), the ANALYZE of a VACUUM ANALYZE does
  (SELECT sampled_at, 'horizon',
          CASE WHEN a.query = '' THEN a.backend_type
               WHEN a.backend_type = 'autovacuum worker' THEN left(a.query, 64) || ' (analyze)'
               ELSE pg_temp.statement_label(a.query_id, a.query) END,
          coalesce(round(extract(epoch FROM clock_timestamp() - a.query_start) * 1000)::text, ''),
          age(a.backend_xmin)::bigint, a.backend_type, coalesce(a.state, ''), '', ''
   FROM unnest(activity) a
   WHERE a.backend_xmin IS NOT NULL AND a.pid NOT IN (SELECT pid FROM pg_stat_progress_vacuum)
   ORDER BY age(a.backend_xmin) DESC
   LIMIT 1)
  UNION ALL
  SELECT sampled_at, 'autovacuum', replace(a.query, 'autovacuum: ', ''), '', 1::bigint, '', '', '', ''
  FROM unnest(activity) a
  WHERE a.backend_type = 'autovacuum worker'
  UNION ALL
  SELECT sampled_at, 'checkpoint', '', '', 1::bigint, '', '', '', ''
  FROM unnest(activity) a
  WHERE a.backend_type = 'checkpointer' AND a.wait_event IS DISTINCT FROM 'CheckpointerMain';

  -- lock: blocker state, blocker wait, count, waiting statement, blocking statement, blocker, lock type
  RETURN QUERY
  WITH waits AS (
    SELECT l.pid AS waiter, blocker, l.locktype,
           concat_ws('/', l.pid, blocker, l.locktype, l.database, l.relation, l.page, l.tuple, l.virtualxid,
                     l.transactionid, l.classid, l.objid, l.objsubid, l.waitstart) = ANY (waits_before) AS spans_snapshot
    FROM pg_locks l, unnest(pg_blocking_pids(l.pid)) AS blocker
    WHERE NOT l.granted
  )
  SELECT sampled_at, 'lock',
         CASE WHEN spans_snapshot THEN coalesce(b.state, '') ELSE 'unknown' END,
         CASE WHEN spans_snapshot THEN coalesce(b.wait_event_type || ' ' || b.wait_event, 'CPU') ELSE 'unknown' END,
         count(*),
         CASE WHEN spans_snapshot THEN pg_temp.statement_label(w.query_id, w.query) ELSE 'unknown' END,
         CASE WHEN spans_snapshot THEN pg_temp.statement_label(b.query_id, b.query) ELSE 'unknown' END,
         CASE WHEN b.backend_type <> 'client backend' THEN b.backend_type
              WHEN b.client_addr IS NOT DISTINCT FROM w.client_addr THEN 'same node' ELSE 'other node' END,
         waits.locktype
  FROM waits JOIN unnest(activity) w ON w.pid = waits.waiter JOIN unnest(activity) b ON b.pid = waits.blocker
  WHERE w.backend_type = 'client backend'
  GROUP BY 3, 4, 6, 7, 8, 9;
END
$$;

SELECT * FROM pg_temp.wait_sample()
\watch 1
