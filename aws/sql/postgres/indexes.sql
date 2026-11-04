-- the counters are reset before processing (db_before_processing_script)
SELECT '| index | size | scans | entries read | rows fetched | entries read per scan |';
SELECT '|---|---:|---:|---:|---:|---:|';
SELECT format('| %s | %s | %s | %s | %s | %s |', indexrelname, pg_size_pretty(pg_relation_size(indexrelid)), idx_scan,
              idx_tup_read, idx_tup_fetch, coalesce(round(idx_tup_read::numeric / nullif(idx_scan, 0), 1)::text, ''))
FROM pg_stat_user_indexes
WHERE relname = 'jobrunr_jobs'
ORDER BY idx_scan DESC, indexrelname;
