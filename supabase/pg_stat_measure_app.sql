-- Segunda pasada de la medición: solo las consultas del catálogo (lo que
-- ejecuta la app vía PostgREST y los RPC del Studio), ordenadas por llamadas.
-- La pasada general (ordenada por tiempo total) está en pg_stat_measure.sql y
-- en el log del job 43788970041; ahí dominan la introspección del dashboard,
-- no la app.
--
-- Correrla con el workflow apply-migration (acepta cualquier ruta):
--   gh workflow run apply-migration.yml -f file=supabase/pg_stat_measure_app.sql

select calls,
       round(total_exec_time::numeric, 1) as total_ms,
       round(mean_exec_time::numeric, 1) as mean_ms,
       rows,
       left(query, 100) as query
from pg_stat_statements
where query ilike '%wallpaper%'
   or query ilike '%collection%'
   or query ilike '%tag%'
   or query ilike '%app_settings%'
order by calls desc
limit 20;
