-- Políticas de acceso consistentes y evaluadas una sola vez por consulta.
-- Es idempotente: se puede ejecutar completo en el editor SQL de Supabase.

-- Categorías.
drop policy if exists "categories_public_read" on public.categories;
create policy "categories_public_read"
    on public.categories
    for select
    using (true);

drop policy if exists "categories_admin_insert" on public.categories;
create policy "categories_admin_insert"
    on public.categories
    for insert
    to authenticated
    with check ((select public.is_admin()));

drop policy if exists "categories_admin_update" on public.categories;
create policy "categories_admin_update"
    on public.categories
    for update
    to authenticated
    using ((select public.is_admin()))
    with check ((select public.is_admin()));

drop policy if exists "categories_admin_delete" on public.categories;
create policy "categories_admin_delete"
    on public.categories
    for delete
    to authenticated
    using ((select public.is_admin()));

-- Reglas de etiqueta.
drop policy if exists "tag_categories_public_read" on public.tag_categories;
create policy "tag_categories_public_read"
    on public.tag_categories
    for select
    using (true);

drop policy if exists "tag_categories_admin_insert" on public.tag_categories;
create policy "tag_categories_admin_insert"
    on public.tag_categories
    for insert
    to authenticated
    with check ((select public.is_admin()));

drop policy if exists "tag_categories_admin_update" on public.tag_categories;
create policy "tag_categories_admin_update"
    on public.tag_categories
    for update
    to authenticated
    using ((select public.is_admin()))
    with check ((select public.is_admin()));

drop policy if exists "tag_categories_admin_delete" on public.tag_categories;
create policy "tag_categories_admin_delete"
    on public.tag_categories
    for delete
    to authenticated
    using ((select public.is_admin()));

-- Textos de la portada.
drop policy if exists "app_settings_public_read" on public.app_settings;
create policy "app_settings_public_read"
    on public.app_settings
    for select
    using (true);

drop policy if exists "app_settings_admin_insert" on public.app_settings;
create policy "app_settings_admin_insert"
    on public.app_settings
    for insert
    to authenticated
    with check ((select public.is_admin()));

drop policy if exists "app_settings_admin_update" on public.app_settings;
create policy "app_settings_admin_update"
    on public.app_settings
    for update
    to authenticated
    using ((select public.is_admin()))
    with check ((select public.is_admin()));

-- Cola de limpieza de Storage.
drop policy if exists "storage_cleanup_admin_read" on public.storage_cleanup_queue;
create policy "storage_cleanup_admin_read"
    on public.storage_cleanup_queue
    for select
    to authenticated
    using ((select public.is_admin()));

-- Objetos del bucket del catálogo.
drop policy if exists "wallpapers_storage_read" on storage.objects;
create policy "wallpapers_storage_read"
    on storage.objects
    for select
    using (bucket_id = 'wallpapers');

drop policy if exists "wallpapers_storage_insert" on storage.objects;
create policy "wallpapers_storage_insert"
    on storage.objects
    for insert
    to authenticated
    with check (
        bucket_id = 'wallpapers'
        and (select public.is_admin())
    );

drop policy if exists "wallpapers_storage_update" on storage.objects;
create policy "wallpapers_storage_update"
    on storage.objects
    for update
    to authenticated
    using (
        bucket_id = 'wallpapers'
        and (select public.is_admin())
    )
    with check (
        bucket_id = 'wallpapers'
        and (select public.is_admin())
    );

drop policy if exists "wallpapers_storage_delete" on storage.objects;
create policy "wallpapers_storage_delete"
    on storage.objects
    for delete
    to authenticated
    using (
        bucket_id = 'wallpapers'
        and (select public.is_admin())
    );

-- Los textos de las reglas se guardan sin espacios de más.
alter table public.tag_categories
    drop constraint if exists tag_categories_display_shape;
alter table public.tag_categories
    add constraint tag_categories_display_shape
    check (
        display_tag = btrim(display_tag)
        and char_length(display_tag) between 1 and 40
    );

notify pgrst, 'reload schema';
