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
  SELECT sampled_at, a.state, coalesce(a.wait_event_type, 'CPU'), coalesce(a.wait_event, 'CPU'), count(*), '', '', '', ''
  FROM unnest(activity) a
  WHERE a.backend_type = 'client backend' AND a.usename = current_user
  GROUP BY a.state, a.wait_event_type, a.wait_event
  UNION ALL
  SELECT sampled_at, 'autovacuum', replace(a.query, 'autovacuum: ', ''), '', 1::bigint, '', '', '', ''
  FROM unnest(activity) a
  WHERE a.backend_type = 'autovacuum worker'
  UNION ALL
  SELECT sampled_at, 'checkpoint', '', '', 1::bigint, '', '', '', ''
  FROM unnest(activity) a
  WHERE a.backend_type = 'checkpointer' AND a.wait_event IS DISTINCT FROM 'CheckpointerMain';

  -- lock: blocker state, blocker wait, count, waiting statement, blocking statement, blocker, lock type; statements as
  -- 'query id: start' without characters that need CSV or Markdown quoting
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
         CASE WHEN spans_snapshot THEN format('%s: %s', w.query_id, left(regexp_replace(w.query, '[\s,"|`]+', ' ', 'g'), 70)) ELSE 'unknown' END,
         CASE WHEN spans_snapshot THEN format('%s: %s', b.query_id, left(regexp_replace(b.query, '[\s,"|`]+', ' ', 'g'), 70)) ELSE 'unknown' END,
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
