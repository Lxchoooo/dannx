-- Limpieza de la prueba temporal de RLS.
delete from public.collection_items
where collection_id in (
    select id from public.collections where name = 'Prueba RLS temporal'
);
delete from public.collections where name = 'Prueba RLS temporal';

select count(*) as restantes
from public.collections
where name = 'Prueba RLS temporal';
