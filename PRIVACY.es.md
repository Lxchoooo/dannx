# Política de privacidad — DannX

Última actualización: 2026-11-08

DannX es una app de fondos de pantalla para Android y de código abierto. Esta
política explica qué guarda la app, qué envía por la red y qué no recopila.

## Resumen

- No hace falta una cuenta para explorar fondos, descargarlos ni usar el feed de
  ejemplo.
- No incluye SDKs de analítica, publicidad, seguimiento ni reporte de fallos.
- Los favoritos, las colecciones, los ajustes y la caché offline quedan en tu
  dispositivo.
- El único servicio de red es el proyecto de Supabase que aloja el catálogo de
  fondos. El ingreso de administrador a la pantalla privada Estudio usa Supabase
  Auth.
- La app no recopila ubicación, contactos, cámara, micrófono ni datos de salud.

## Datos que se guardan en tu dispositivo

DannX guarda estos datos en el almacenamiento privado de la app:

- Favoritos.
- Colecciones que creás mientras usás el feed de ejemplo.
- Ajustes: tema, modo lite y si el editor se abre antes de aplicar un fondo.
- El último feed y las últimas colecciones, en caché para usarlos sin conexión.
- La lista de fondos que descargaste, para ofrecerte la copia existente.
- Si ingresás al Estudio privado: tu correo de administrador, tu id de usuario y
  los tokens de sesión de Supabase. Viven en un archivo de preferencias separado
  que queda excluido del backup en la nube de Android y de la transferencia entre
  dispositivos.

Podés borrar estos datos en cualquier momento desde Ajustes de Android → Apps →
DannX → Almacenamiento → Borrar datos. La caché también se puede limpiar desde
los ajustes de DannX.

## Datos que se envían por la red

- La app descarga el catálogo público de fondos, las imágenes y los datos de
  colecciones desde Supabase por HTTPS.
- Si ingresás al Estudio privado, tu correo y contraseña se envían a Supabase
  Auth. La app no guarda la contraseña; solo conserva los tokens de sesión que
  devuelve el servidor.
- Los cambios que hacés en el Estudio privado (fondos, categorías, tags y
  colecciones) se envían a Supabase para actualizar el catálogo.
- Las imágenes descargadas van directo desde Supabase Storage a tu dispositivo.
  DannX no las hace pasar por ningún otro servidor.
- No se envían estadísticas de uso, identificadores del dispositivo ni
  identificadores publicitarios.

## Permisos

- `INTERNET` y `ACCESS_NETWORK_STATE`: cargar fondos y detectar si el
  dispositivo está sin conexión.
- `SET_WALLPAPER`: aplicar un fondo en la pantalla de inicio, la de bloqueo o
  ambas.
- El acceso a MediaStore se usa solo para guardar una imagen en
  `Pictures/DannX` y para abrir un archivo que ya descargaste.

## Terceros

- **Supabase** aloja el catálogo de fondos, la autenticación y el almacenamiento
  de imágenes. Su política de privacidad aplica a los datos que procesa ese
  servicio.
- **GitHub** aloja el código y los APK de release. Abrir el repositorio, la
  página de releases o esta política desde la app abre GitHub en tu navegador.
- Las imágenes se cargan con Coil/OkHttp directamente desde la URL de
  almacenamiento de Supabase.

DannX no vende, alquila ni comparte datos personales con anunciantes ni con
corredores de datos.

## Menores

DannX es una app de fondos de pantalla de uso general y no está dirigida a
menores de 13 años. La app no recopila a sabiendas información personal de
menores.

## Cambios

Cuando esta política cambie, la versión actualizada se va a publicar en este
repositorio con una nueva fecha de "Última actualización". Los cambios
importantes se van a mencionar en las notas del release.

## Contacto

Para consultas de privacidad, abrí un issue en
<https://github.com/Lxchoooo/dannx/issues>. Para reportes sensibles, usá el canal
privado descrito en [SECURITY.es.md](SECURITY.es.md).
