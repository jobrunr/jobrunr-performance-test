CREATE TEMP VIEW statements AS SELECT * FROM pg_stat_statements WHERE toplevel AND calls > 0;
SELECT format('JobStats queries: %s calls, %s%% of the execution time',
              coalesce(sum(calls) FILTER (WHERE query LIKE '%jobrunr_jobs_stats%'), 0),
              round((100 * coalesce(sum(total_exec_time) FILTER (WHERE query LIKE '%jobrunr_jobs_stats%'), 0) / nullif(sum(total_exec_time), 0))::numeric, 2))
FROM statements;
SELECT '';
SELECT '| time (%) | calls | total (ms) | mean (ms) | rows | blocks hit / read / dirtied / written per call | query id | statement |';
SELECT '|---:|---:|---:|---:|---:|---:|---|---|';
SELECT format('| %s | %s | %s | %s | %s | %s / %s / %s / %s | %s | `%s` |',
              round((100 * total_exec_time / sum(total_exec_time) OVER ())::numeric, 2), calls,
              round(total_exec_time::numeric, 1), round(mean_exec_time::numeric, 3), rows,
              round(shared_blks_hit::numeric / calls, 1), round(shared_blks_read::numeric / calls, 1),
              round(shared_blks_dirtied::numeric / calls, 1), round(shared_blks_written::numeric / calls, 1), queryid,
              replace(left(regexp_replace(query, '\s+', ' ', 'g'), 160), '|', '\|'))
FROM statements
ORDER BY total_exec_time DESC
LIMIT 20;
