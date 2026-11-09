# Política de privacidad — DannX

[English](PRIVACY.md) | Español

Última actualización: 2026-11-09

DannX es una app de fondos de pantalla para Android, de código abierto y
distribuida a través de GitHub Releases. Esta política explica qué guarda la app
en tu dispositivo, qué envía por la red y qué opciones tenés. Se aplica a la app
y no a los sitios o servicios que abras desde ella.

## Resumen

- Podés usar DannX sin crear una cuenta ni dar datos personales.
- No incluye servicios de analítica, publicidad, seguimiento ni reporte de
  fallos.
- Tus favoritos, colecciones, preferencias y la caché offline quedan en el
  almacenamiento privado de la app.
- La red se usa solo para cargar el catálogo público de fondos y las imágenes, y
  para buscar y descargar actualizaciones de la app.
- DannX no pide ubicación, contactos, cámara, micrófono ni datos de salud, y no
  usa identificadores publicitarios.

## Datos que se guardan en tu dispositivo

DannX guarda lo siguiente en el almacenamiento privado de la app:

- Tus favoritos y las colecciones que creás.
- Tus últimas búsquedas (hasta ocho), para que puedas repetirlas.
- Preferencias: tema, idioma, modo lite y si el editor se abre antes de aplicar
  un fondo.
- Una copia offline acotada del catálogo y de sus colecciones, para mostrar
  contenido sin conexión.
- Un registro de los fondos que guardaste, para ofrecerte abrir la copia
  existente en lugar de descargarla otra vez.
- El resultado de la última búsqueda de actualizaciones y, si aceptás una
  actualización, el archivo de instalación descargado en almacenamiento
  temporal.
- Copias temporales de las imágenes que decidís compartir, guardadas en la caché
  de la app.

Las imágenes que guardás se escriben en `Pictures/DannX`, en la galería del
dispositivo, y permanecen ahí hasta que las borres.

Si tenés activado el respaldo de Android, algunos datos de la app —como
favoritos, colecciones y preferencias— pueden incluirse en el respaldo que
administra Google. Las imágenes en caché y los archivos temporales no se
incluyen. Podés controlar esta opción en los ajustes de Android.

Para borrar los datos de la app, andá a Ajustes de Android → Apps → DannX →
Almacenamiento → Borrar datos. La caché del catálogo también se puede limpiar
desde los ajustes de DannX; hacerlo no borra las imágenes que guardaste en la
galería.

## Datos que se envían por la red

DannX usa HTTPS en todas sus solicitudes.

- **Catálogo de fondos.** La app descarga el catálogo público, la información de
  las colecciones y las imágenes desde un servidor alojado en Supabase.
- **Actualizaciones.** Cuando abrís los ajustes o pedís buscar actualizaciones,
  la app consulta la API pública de GitHub para ver si hay una versión más
  nueva, como máximo una vez por día. La descarga solo empieza cuando la pedís,
  y el archivo se verifica con su checksum SHA-256 publicado antes de abrir el
  instalador de Android.
- **Compartir.** Cuando compartís una imagen, se la entrega a la app que elijas
  mediante el menú de compartir de Android. El uso que esa app haga de la imagen
  se rige por su propia política de privacidad.

DannX no envía estadísticas de uso, identificadores del dispositivo ni
identificadores publicitarios. Como en cualquier solicitud de red, los servicios
anteriores reciben tu dirección IP y la información técnica básica necesaria
para responder.

## Permisos

- `INTERNET` y `ACCESS_NETWORK_STATE`: cargar el catálogo y las imágenes, y
  detectar si el dispositivo está sin conexión.
- `SET_WALLPAPER`: aplicar un fondo en la pantalla de inicio, la de bloqueo o
  ambas.
- `REQUEST_INSTALL_PACKAGES`: abrir el instalador de Android cuando decidís
  instalar una actualización. Android siempre te pide confirmación.

Guardar una imagen usa la biblioteca multimedia de Android y no le da a DannX
acceso a las fotos que ya tenés en el dispositivo.

## Terceros

- **Supabase** aloja el catálogo y el almacenamiento de imágenes. Los datos que
  procesa se rigen por la política de privacidad de Supabase.
- **GitHub** aloja el código, las versiones y los datos de actualización. Abrir
  el repositorio, la página de versiones o esta política desde la app abre GitHub
  en tu navegador.
- **Las apps que elegís** al compartir o abrir una imagen guardada reciben solo
  el archivo que seleccionás.

DannX no vende, alquila ni comparte datos personales con anunciantes ni con
corredores de datos.

## Seguridad

Todas las comunicaciones usan HTTPS y las actualizaciones se comparan con un
checksum SHA-256 publicado antes de instalarse. Ningún método de transmisión o
almacenamiento es completamente seguro, pero DannX está diseñada para recopilar
la menor cantidad de datos posible.

## Menores

DannX es una app de fondos de pantalla de uso general y no está dirigida a
menores de 13 años. La app no recopila a sabiendas información personal de
menores.

## Conservación y borrado

Los datos de la app permanecen en tu dispositivo hasta que los borres o
desinstales DannX. Los archivos temporales se limpian periódicamente y cuando
borrás la caché de la app. Desinstalar la app borra sus datos privados; las
imágenes guardadas en la galería no se borran y tenés que eliminarlas desde tu
app de galería.

## Cambios

Cuando esta política cambie, la versión actualizada se va a publicar en este
repositorio con una nueva fecha de "Última actualización". Los cambios
importantes se van a mencionar en las notas de la versión.

## Contacto

Para consultas de privacidad, abrí un issue en
<https://github.com/Lxchoooo/dannx/issues>. Para reportes sensibles, usá el canal
privado descrito en [SECURITY.es.md](SECURITY.es.md).
