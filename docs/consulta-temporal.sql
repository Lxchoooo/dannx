-- Consulta temporal de inspección. No modifica nada.
with cols as (
    select
        'columna' as tipo,
        table_name || '.' || column_name as objeto,
        data_type || ' | null=' || is_nullable || ' | default=' || coalesce(column_default, '(sin default)') as detalle
    from information_schema.columns
    where table_schema = 'public'
      and table_name in ('collections', 'collection_items')
),
cols_hex as (
    select
        'columna_hex' as tipo,
        c.relname || '.' || a.attname as objeto,
        encode(convert_to(a.attname, 'UTF8'), 'hex') as detalle
    from pg_attribute a
    join pg_class c on c.oid = a.attrelid
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public'
      and c.relname in ('collections', 'collection_items')
      and a.attnum > 0
      and not a.attisdropped
),
rls as (
    select
        'rls' as tipo,
        c.relname as objeto,
        'enabled=' || c.relrowsecurity::text || ' | forced=' || c.relforcerowsecurity::text as detalle
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public'
      and c.relname in ('collections', 'collection_items')
),
policies as (
    select
        'politica' as tipo,
        tablename || '.' || policyname as objeto,
        'cmd=' || cmd || ' | roles=' || roles::text
            || ' | using=' || coalesce(qual, '(ninguno)')
            || ' | check=' || coalesce(with_check, '(ninguno)') as detalle
    from pg_policies
    where schemaname = 'public'
      and tablename in ('collections', 'collection_items')
)
select tipo, objeto, detalle
from (
    select * from cols
    union all
    select * from cols_hex
    union all
    select * from rls
    union all
    select * from policies
) as inspeccion
order by tipo, objeto;
