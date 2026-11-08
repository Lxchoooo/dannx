-- Repara la columna de privacidad de collections.
--
-- La base viva tenía la columna con un nombre corrupto (is_ + tres U+2022)
-- mientras la app escribe is_public. El renombre conserva los valores y las
-- políticas se actualizan solas porque Postgres guarda la referencia por
-- atributo. Es idempotente.

do $$
declare
    candidates int;
    old_name text;
begin
    if to_regclass('public.collections') is null then
        raise notice 'No existe public.collections; nada que reparar.';
        return;
    end if;

    if exists (
        select 1
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'collections'
          and column_name = 'is_public'
    ) then
        raise notice 'collections.is_public ya existe.';
        return;
    end if;

    select count(*), min(column_name)
    into candidates, old_name
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'collections'
      and data_type = 'boolean'
      and column_name like 'is\_%';

    if candidates = 0 then
        raise notice 'No hay columna is_*; se agrega is_public.';
        alter table public.collections
            add column is_public boolean not null default true;
    elsif candidates = 1 then
        raise notice 'Renombrando collections.% a is_public.', old_name;
        execute format(
            'alter table public.collections rename column %I to is_public',
            old_name
        );
    else
        raise exception
            'Hay % columnas booleanas is_* en collections; revisar a mano.',
            candidates;
    end if;
end $$;

alter table public.collections alter column is_public set default true;
update public.collections set is_public = true where is_public is null;

alter table public.collections enable row level security;
alter table public.collection_items enable row level security;

grant select on public.collections to anon, authenticated;
grant select on public.collection_items to anon, authenticated;
grant insert, update, delete on public.collections to authenticated;
grant insert, update, delete on public.collection_items to authenticated;

notify pgrst, 'reload schema';

-- Verificación: el workflow imprime este resultado.
select
    (select count(*)
     from information_schema.columns
     where table_schema = 'public'
       and table_name = 'collections'
       and column_name = 'is_public') as is_public_present,
    (select count(*)
     from information_schema.columns
     where table_schema = 'public'
       and table_name = 'collections'
       and data_type = 'boolean'
       and column_name like 'is\_%') as is_columns,
    (select count(*)
     from pg_policies
     where schemaname = 'public'
       and tablename in ('collections', 'collection_items')
       and (coalesce(qual, '') like '%is_public%'
            or coalesce(with_check, '') like '%is_public%')) as policies_with_is_public,
    (select count(*)
     from pg_policies
     where schemaname = 'public'
       and tablename in ('collections', 'collection_items')
       and coalesce(qual, '') like '%is\_%'
       and coalesce(qual, '') not like '%is_public%') as stale_policies;
