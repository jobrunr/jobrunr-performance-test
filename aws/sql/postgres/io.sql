-- reset before processing (db_before_processing_script)
SELECT '| backend type | object | context | hits | reads | writes | extends | evictions | reuses |';
SELECT '|---|---|---|---:|---:|---:|---:|---:|---:|';
SELECT format('| %s | %s | %s | %s | %s | %s | %s | %s | %s |', backend_type, object, context, coalesce(hits, 0),
              coalesce(reads, 0), coalesce(writes, 0), coalesce(extends, 0), coalesce(evictions, 0), coalesce(reuses, 0))
FROM pg_stat_io
WHERE coalesce(reads, 0) + coalesce(writes, 0) + coalesce(extends, 0) + coalesce(evictions, 0) > 0
ORDER BY coalesce(evictions, 0) + coalesce(writes, 0) DESC, coalesce(reads, 0) DESC;
