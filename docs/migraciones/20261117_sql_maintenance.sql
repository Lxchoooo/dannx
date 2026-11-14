-- Mantenimiento de la base: cola de limpieza, auditoría y textos.
-- Es idempotente: se puede ejecutar completo en el editor SQL de Supabase.

-- 1. La cola de limpieza no necesita la misma ruta dos veces.
delete from public.storage_cleanup_queue older
using public.storage_cleanup_queue newer
where older.path = newer.path
  and older.id < newer.id;

create unique index if not exists storage_cleanup_queue_path_idx
    on public.storage_cleanup_queue (path);

alter table public.storage_cleanup_queue
    drop constraint if exists storage_cleanup_queue_path_shape;
alter table public.storage_cleanup_queue
    add constraint storage_cleanup_queue_path_shape
    check (btrim(path) <> '');

alter table public.storage_cleanup_queue
    drop constraint if exists storage_cleanup_queue_attempts_nonneg;
alter table public.storage_cleanup_queue
    add constraint storage_cleanup_queue_attempts_nonneg
    check (attempts >= 0);

-- 2. La purga guarda cada ruta una sola vez, aunque se repita la ejecución.
create or replace function public.purge_expired_wallpapers(p_days int default 30)
returns int
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    purged int;
begin
    insert into public.storage_cleanup_queue (path)
    select expired.path
    from (
        select nullif(btrim(w.storage_path), '') as path
        from public.wallpapers as w
        where w.deleted_at is not null
          and w.deleted_at < now() - make_interval(
              days => greatest(coalesce(p_days, 30), 1)
          )
    ) as expired
    where expired.path is not null
    on conflict (path) do nothing;

    delete from public.wallpapers
    where deleted_at is not null
      and deleted_at < now() - make_interval(
          days => greatest(coalesce(p_days, 30), 1)
      );

    get diagnostics purged = row_count;
    return purged;
end;
$$;

revoke all on function public.purge_expired_wallpapers(int) from public;

-- 3. La limpieza diaria se vuelve a programar por si pg_cron se habilitó
--    después de la migración original.
do $$
begin
    create extension if not exists pg_cron;
exception
    when others then
        raise notice 'pg_cron no disponible: %', sqlerrm;
end;
$$;

do $$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        if exists (
            select 1 from cron.job where jobname = 'purge-expired-wallpapers'
        ) then
            perform cron.unschedule('purge-expired-wallpapers');
        end if;
        perform cron.schedule(
            'purge-expired-wallpapers',
            '15 4 * * *',
            'select public.purge_expired_wallpapers();'
        );
    end if;
end;
$$;

-- 4. Los textos de los ajustes y de las reglas guardan cuándo y quién cambió.
create or replace function public.touch_app_setting()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    new.updated_at := now();
    if auth.uid() is not null then
        new.updated_by := auth.uid();
    end if;
    return new;
end;
$$;

drop trigger if exists app_settings_touch_updated on public.app_settings;
create trigger app_settings_touch_updated
    before insert or update on public.app_settings
    for each row
    execute function public.touch_app_setting();

drop trigger if exists tag_categories_touch_updated_at on public.tag_categories;
create trigger tag_categories_touch_updated_at
    before update on public.tag_categories
    for each row
    execute function public.touch_category();

revoke all on function public.touch_app_setting() from public;

-- 5. Los textos no se guardan con espacios de más.
alter table public.wallpapers
    drop constraint if exists wallpapers_title_trimmed;
alter table public.wallpapers
    add constraint wallpapers_title_trimmed
    check (title = btrim(title)) not valid;
alter table public.wallpapers
    validate constraint wallpapers_title_trimmed;

alter table public.wallpapers
    drop constraint if exists wallpapers_author_trimmed;
alter table public.wallpapers
    add constraint wallpapers_author_trimmed
    check (author = btrim(author)) not valid;
alter table public.wallpapers
    validate constraint wallpapers_author_trimmed;

alter table public.wallpapers
    drop constraint if exists wallpapers_category_trimmed;
alter table public.wallpapers
    add constraint wallpapers_category_trimmed
    check (category = btrim(category)) not valid;
alter table public.wallpapers
    validate constraint wallpapers_category_trimmed;

alter table public.wallpapers
    drop constraint if exists wallpapers_storage_path_shape;
alter table public.wallpapers
    add constraint wallpapers_storage_path_shape
    check (btrim(storage_path) <> '') not valid;
alter table public.wallpapers
    validate constraint wallpapers_storage_path_shape;

alter table public.categories
    drop constraint if exists categories_name_trimmed;
alter table public.categories
    add constraint categories_name_trimmed
    check (name = btrim(name));

alter table public.collections
    drop constraint if exists collections_name_trimmed;
alter table public.collections
    add constraint collections_name_trimmed
    check (name = btrim(name));

notify pgrst, 'reload schema';
