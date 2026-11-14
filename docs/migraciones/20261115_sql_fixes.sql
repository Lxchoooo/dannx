-- Correcciones y ajustes del lado del servidor.
-- Es idempotente: se puede ejecutar completo en el editor SQL de Supabase.

-- 1. La regla de una etiqueta debe encontrar los fondos sin importar cómo
--    esté escrita la etiqueta ("Cleopatra" o "cleopatra").
create or replace function public.set_tag_category(
    p_tags text[],
    p_category_id uuid
)
returns integer
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
    v_name text;
    v_count integer;
begin
    if not public.is_admin() then
        raise exception 'No autorizado' using errcode = '42501';
    end if;

    select c.name into v_name
    from public.categories c
    where c.id = p_category_id;

    if v_name is null then
        raise exception 'La categoría no existe' using errcode = '22023';
    end if;

    insert into public.tag_categories (tag, display_tag, category_id)
    select lower(btrim(wanted.tag)), btrim(wanted.tag), p_category_id
    from unnest(coalesce(p_tags, '{}'::text[])) as wanted(tag)
    where btrim(wanted.tag) <> ''
    on conflict (tag) do update
        set display_tag = excluded.display_tag,
            category_id = excluded.category_id,
            updated_at = now();

    update public.wallpapers as w
    set category_id = p_category_id,
        category = v_name
    where exists (
        select 1
        from unnest(coalesce(p_tags, '{}'::text[])) as wanted(tag)
        where exists (
            select 1
            from unnest(public.wallpaper_tag_labels(w.tags)) as label(value)
            where lower(btrim(label.value)) = lower(btrim(wanted.tag))
        )
    );

    get diagnostics v_count = row_count;
    return v_count;
end;
$$;

revoke all on function public.set_tag_category(text[], uuid) from public;
grant execute on function public.set_tag_category(text[], uuid) to authenticated;

-- 2. La purga usaba una columna que la tabla no tiene. La ruta de Storage
--    vive en storage_path.
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
    where expired.path is not null;

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

-- 3. Reordenar una colección depende del dueño, no del administrador. Las
--    políticas de acceso ya limitan la escritura a quien corresponde.
create or replace function public.reorder_collection(
    p_collection_id uuid,
    p_wallpaper_ids uuid[]
)
returns void
language sql
security invoker
set search_path = public, pg_temp
as $$
    with ordered as (
        select
            candidate.wallpaper_id,
            (min(candidate.position) - 1)::int as position
        from unnest(p_wallpaper_ids)
            with ordinality as candidate(wallpaper_id, position)
        group by candidate.wallpaper_id
    )
    update public.collection_items as item
    set position = ordered.position
    from ordered
    where item.collection_id = p_collection_id
      and item.wallpaper_id = ordered.wallpaper_id;
$$;

revoke all on function public.reorder_collection(uuid, uuid[]) from public;
grant execute on function public.reorder_collection(uuid, uuid[]) to authenticated;

-- 4. Políticas del feed con la comprobación de administrador una sola vez.
drop policy if exists "wallpapers_public_read" on public.wallpapers;
create policy "wallpapers_public_read"
    on public.wallpapers
    for select
    using (
        (deleted_at is null and archived_at is null)
        or (select public.is_admin())
    );

drop policy if exists "wallpapers_admin_insert" on public.wallpapers;
create policy "wallpapers_admin_insert"
    on public.wallpapers
    for insert
    to authenticated
    with check ((select public.is_admin()));

drop policy if exists "wallpapers_admin_update" on public.wallpapers;
create policy "wallpapers_admin_update"
    on public.wallpapers
    for update
    to authenticated
    using ((select public.is_admin()))
    with check ((select public.is_admin()));

drop policy if exists "wallpapers_admin_delete" on public.wallpapers;
create policy "wallpapers_admin_delete"
    on public.wallpapers
    for delete
    to authenticated
    using ((select public.is_admin()));

-- 5. Las colecciones se pueden reconstruir desde el repositorio: se reemplazan
--    sus políticas por un juego fijo y con la identidad evaluada una vez.
do $$
declare
    policy record;
begin
    for policy in
        select tablename, policyname
        from pg_policies
        where schemaname = 'public'
          and tablename in ('collections', 'collection_items')
    loop
        execute format(
            'drop policy %I on public.%I',
            policy.policyname,
            policy.tablename
        );
    end loop;
end $$;

create policy "collections_read_visible"
    on public.collections
    for select
    using (is_public or owner_id = (select auth.uid()));

create policy "collections_owner_insert"
    on public.collections
    for insert
    to authenticated
    with check (owner_id = (select auth.uid()));

create policy "collections_owner_update"
    on public.collections
    for update
    to authenticated
    using (owner_id = (select auth.uid()))
    with check (owner_id = (select auth.uid()));

create policy "collections_owner_delete"
    on public.collections
    for delete
    to authenticated
    using (owner_id = (select auth.uid()));

create policy "collection_items_read_visible"
    on public.collection_items
    for select
    using (
        exists (
            select 1
            from public.collections c
            where c.id = collection_id
              and (c.is_public or c.owner_id = (select auth.uid()))
        )
    );

create policy "collection_items_owner_insert"
    on public.collection_items
    for insert
    to authenticated
    with check (
        exists (
            select 1
            from public.collections c
            where c.id = collection_id
              and c.owner_id = (select auth.uid())
        )
    );

create policy "collection_items_owner_update"
    on public.collection_items
    for update
    to authenticated
    using (
        exists (
            select 1
            from public.collections c
            where c.id = collection_id
              and c.owner_id = (select auth.uid())
        )
    )
    with check (
        exists (
            select 1
            from public.collections c
            where c.id = collection_id
              and c.owner_id = (select auth.uid())
        )
    );

create policy "collection_items_owner_delete"
    on public.collection_items
    for delete
    to authenticated
    using (
        exists (
            select 1
            from public.collections c
            where c.id = collection_id
              and c.owner_id = (select auth.uid())
        )
    );

grant select on public.collections to anon, authenticated;
grant insert, update, delete on public.collections to authenticated;
grant select on public.collection_items to anon, authenticated;
grant insert, update, delete on public.collection_items to authenticated;

-- 6. Las posiciones de las categorías tampoco pueden ser negativas.
alter table public.categories
    drop constraint if exists categories_position_nonneg;
alter table public.categories
    add constraint categories_position_nonneg
    check (position >= 0);

notify pgrst, 'reload schema';
