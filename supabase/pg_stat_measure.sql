-- Medición de consultas para ajustar los límites del rate limiting y las
-- migraciones futuras. NO es una migración: no crea ni altera nada.
--
-- Correrla con el workflow apply-migration (acepta cualquier ruta):
--   gh workflow run apply-migration.yml -f file=supabase/pg_stat_measure.sql
-- Los números quedan en el log del job. Requiere la extensión
-- pg_stat_statements, creada por docs/migraciones/20261121_query_guards.sql.
--
-- La Management API devuelve solo el resultado de la última sentencia: el
-- archivo lleva una sola consulta. Para ver la configuración de roles usar
-- `select rolname, rolconfig from pg_roles where rolname in
-- ('anon','authenticated')` como segunda pasada.

create extension if not exists pg_stat_statements;

select calls,
       round(total_exec_time::numeric, 1) as total_ms,
       round(mean_exec_time::numeric, 1) as mean_ms,
       rows,
       left(query, 80) as query
from pg_stat_statements
order by total_exec_time desc
limit 20;
