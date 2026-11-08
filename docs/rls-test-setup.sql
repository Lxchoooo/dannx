-- Prueba temporal de RLS: crea una colección privada y un ítem.
-- Se borra en el paso siguiente. No forma parte de las migraciones.
do $$
declare
    admin_id uuid := '87645d98-691d-42a7-b1f3-b18f05cabd59';
    collection uuid;
    wallpaper uuid;
begin
    delete from public.collection_items
    where collection_id in (
        select id from public.collections where name = 'Prueba RLS temporal'
    );
    delete from public.collections where name = 'Prueba RLS temporal';

    insert into public.collections (owner_id, owner_name, name, description, is_public)
    values (admin_id, 'rls-test', 'Prueba RLS temporal', 'temporal', false)
    returning id into collection;

    select id into wallpaper
    from public.wallpapers
    where archived_at is null
    order by created_at
    limit 1;

    if wallpaper is not null then
        insert into public.collection_items (collection_id, wallpaper_id, position)
        values (collection, wallpaper, 0)
        on conflict do nothing;
    end if;
end $$;

select
    c.id,
    c.is_public,
    (select count(*) from public.collection_items i where i.collection_id = c.id) as items
from public.collections c
where c.name = 'Prueba RLS temporal';
