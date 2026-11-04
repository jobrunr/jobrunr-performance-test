-- psql variable mid: WAL position in bytes halfway through processing, or empty
CREATE EXTENSION IF NOT EXISTS pg_walinspect;
SELECT format('- WAL: %s in %s records (%s full-page images), %s per jobrunr_jobs update, WAL buffers full %s times',
              pg_size_pretty(w.wal_bytes), w.wal_records, w.wal_fpi,
              coalesce(pg_size_pretty(round(w.wal_bytes / nullif(u.rows, 0))), 'n/a'), w.wal_buffers_full)
FROM pg_stat_wal w,
     (SELECT sum(rows) AS rows FROM pg_stat_statements WHERE toplevel AND query ILIKE 'update jobrunr_jobs %') u;
-- WAL before the last checkpoint's redo point may be recycled already
SELECT greatest('0/0'::pg_lsn + nullif(:'mid', '')::numeric, (pg_control_checkpoint()).redo_lsn) AS from_lsn \gset
SELECT least(:'from_lsn'::pg_lsn + 1073741824::numeric, pg_current_wal_lsn()) AS to_lsn \gset
SELECT format('- By record type, %s of WAL from %s (%s after the middle of processing):',
              pg_size_pretty(:'to_lsn'::pg_lsn - :'from_lsn'::pg_lsn), :'from_lsn',
              coalesce(pg_size_pretty(:'from_lsn'::pg_lsn - ('0/0'::pg_lsn + nullif(:'mid', '')::numeric)), 'unknown'));
SELECT '';
SELECT '| resource manager | records | record bytes | full-page image bytes | share of bytes (%) |';
SELECT '|---|---:|---:|---:|---:|';
SELECT format('| %s | %s | %s | %s | %s |', s."resource_manager/record_type", s.count, pg_size_pretty(s.record_size),
              pg_size_pretty(s.fpi_size), round(s.combined_size_percentage::numeric, 1))
FROM pg_get_wal_stats(:'from_lsn', :'to_lsn') s
WHERE s.count > 0
ORDER BY s.combined_size DESC;
