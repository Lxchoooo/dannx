-- Bucket del catálogo: sin esto, una instalación nueva aplica las políticas de
-- Storage de las migraciones anteriores pero no tiene dónde escribir, y subir
-- una imagen falla con "Bucket not found".
--
-- Es idempotente: si el bucket ya existe, solo reafirma los límites. Los
-- valores son los mismos que documenta supabase/setup.sql.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'wallpapers',
    'wallpapers',
    true,
    26214400,
    array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update set
    public = true,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

notify pgrst, 'reload schema';
