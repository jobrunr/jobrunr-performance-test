SELECT format('- %s (table options: %s): %s live / %s dead rows, %s heap, %s%% of %s updates HOT, %s%% moved to another page, %s autovacuums (last at %s), %s autoanalyzes',
              s.relname, coalesce(array_to_string(c.reloptions, ', '), 'none'), n_live_tup, n_dead_tup, pg_size_pretty(pg_relation_size(s.relid)),
              round(100.0 * n_tup_hot_upd / nullif(n_tup_upd, 0), 1), n_tup_upd,
              round(100.0 * n_tup_newpage_upd / nullif(n_tup_upd, 0), 1),
              autovacuum_count, coalesce(last_autovacuum::text, 'never'), autoanalyze_count)
FROM pg_stat_user_tables s JOIN pg_class c ON c.oid = s.relid
WHERE s.relname = 'jobrunr_jobs';
SELECT format('- Checkpoints: %s timed, %s requested, %s s writing, %s s syncing, %s buffers written',
              num_timed, num_requested, round((write_time / 1000)::numeric, 1), round((sync_time / 1000)::numeric, 1), buffers_written)
FROM pg_stat_checkpointer;
