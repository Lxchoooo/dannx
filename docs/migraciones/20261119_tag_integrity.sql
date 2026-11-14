-- Coherencia de las reglas de etiqueta, auditoría de fondos y limpieza.
-- Es idempotente: se puede ejecutar completo en el editor SQL de Supabase.

-- 1. Las reglas guardan la clave en minúsculas y el texto con su escritura.
--    Así la regla y la etiqueta canónica no se contradicen.
update public.tag_categories
set tag = lower(btrim(tag))
where tag <> lower(btrim(tag));

alter table public.tag_categories
    drop constraint if exists tag_categories_tag_shape;
alter table public.tag_categories
    add constraint tag_categories_tag_shape
    check (tag = lower(btrim(tag)) and btrim(tag) <> '') not valid;
alter table public.tag_categories
    validate constraint tag_categories_tag_shape;

alter table public.tag_categories
    drop constraint if exists tag_categories_label_match;
alter table public.tag_categories
    add constraint tag_categories_label_match
    check (tag = lower(btrim(display_tag))) not valid;
alter table public.tag_categories
    validate constraint tag_categories_label_match;

-- 2. Una sola implementación para leer las etiquetas de un fondo: la misma
--    que las separa, sin repetidos y hasta 12.
create or replace function public.wallpaper_tag_labels(p_tags text[])
returns text[]
language sql
immutable
set search_path = public, pg_temp
as $$
    select public.normalize_wallpaper_tags(p_tags);
$$;

revoke all on function public.wallpaper_tag_labels(text[]) from public;
grant execute on function public.wallpaper_tag_labels(text[]) to anon, authenticated;

-- 3. Auditoría de los fondos: quién creó la fila y quién la modificó por
--    última vez. Se reemplaza el trigger anterior para no duplicarlo.
alter table public.wallpapers
    add column if not exists created_by uuid references auth.users(id);
alter table public.wallpapers
    add column if not exists updated_by uuid references auth.users(id);

create or replace function public.touch_wallpaper_audit()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    new.updated_at := now();
    if auth.uid() is not null then
        new.updated_by := auth.uid();
        if tg_op = 'INSERT' and new.created_by is null then
            new.created_by := auth.uid();
        end if;
    end if;
    return new;
end;
$$;

revoke all on function public.touch_wallpaper_audit() from public;

drop trigger if exists wallpapers_touch_updated_at on public.wallpapers;
drop trigger if exists wallpapers_touch_audit on public.wallpapers;
create trigger wallpapers_touch_audit
    before insert or update on public.wallpapers
    for each row
    execute function public.touch_wallpaper_audit();

-- 4. Las etiquetas canónicas que ya no usa ningún fondo y que no tienen regla
--    se descartan: si la etiqueta vuelve, se registra con su escritura nueva.
create or replace function public.prune_tag_labels()
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    removed integer;
begin
    delete from public.tag_labels as label
    where not exists (
        select 1
        from public.wallpapers as w
        where w.tags_lower @> array[label.tag]
    )
    and not exists (
        select 1
        from public.tag_categories as rule
        where rule.tag = label.tag
    );

    get diagnostics removed = row_count;
    return removed;
end;
$$;

revoke all on function public.prune_tag_labels() from public;

-- La purga diaria también ordena las etiquetas canónicas.
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
    perform public.prune_tag_labels();
    return purged;
end;
$$;

revoke all on function public.purge_expired_wallpapers(int) from public;

-- 5. La cola de limpieza recuerda los intentos fallidos.
alter table public.storage_cleanup_queue
    add column if not exists last_attempt_at timestamptz;

create or replace function public.fail_storage_cleanup(p_ids bigint[])
returns void
language sql
security definer
set search_path = public, pg_temp
as $$
    update public.storage_cleanup_queue
    set attempts = attempts + 1,
        last_attempt_at = now()
    where id = any(coalesce(p_ids, '{}'::bigint[]));
$$;

revoke all on function public.fail_storage_cleanup(bigint[]) from public;

do $$
begin
    if exists (select 1 from pg_roles where rolname = 'service_role') then
        grant execute on function public.fail_storage_cleanup(bigint[])
            to service_role;
    end if;
end;
$$;

notify pgrst, 'reload schema';
