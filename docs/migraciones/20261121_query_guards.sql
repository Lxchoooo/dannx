-- Guardia de consultas: corta lo que se dispare en vez de dejarlo quemando
-- CPU y egreso del plan gratuito. El anon key viaja en el APK y cualquiera
-- puede paginar el catálogo o repetir la búsqueda en un bucle; estos límites
-- no evitan el uso, pero matan la consulta cara antes de que el proyecto
-- termine pausado o throttled.
--
-- Es idempotente: la extensión se crea con `if not exists` y los límites de
-- rol se reafirman en cada corrida. Los valores salen del diseño de
-- rate-limiting (docs/designs/rate-limiting.md) y se ajustan con lo que mida
-- pg_stat_statements, que también se habilita acá.

create extension if not exists pg_stat_statements;

alter role anon set statement_timeout = '3s';
alter role authenticated set statement_timeout = '10s';
