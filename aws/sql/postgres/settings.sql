-- compared with boot_val: initdb writes some defaults to postgresql.conf
SELECT '| setting | value | source |';
SELECT '|---|---|---|';
SELECT format('| %s | %s | %s |', name, replace(CASE unit
         WHEN '8kB' THEN pg_size_pretty(setting::bigint * 8192)
         WHEN 'kB' THEN pg_size_pretty(setting::bigint * 1024)
         WHEN 'MB' THEN pg_size_pretty(setting::bigint * 1024 * 1024)
         ELSE setting || coalesce(unit, '')
       END, '|', '\|'), source)
FROM pg_settings
WHERE source NOT IN ('default', 'override', 'client', 'session') AND setting IS DISTINCT FROM boot_val
  AND category NOT LIKE '%Locale and Formatting'
ORDER BY name;
