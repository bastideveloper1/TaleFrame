# TaleFrame

Editor Android privado y offline de historietas interactivas por láminas. Kotlin + Jetpack Compose y SQLite local. Los proyectos de las Iteraciones 1 y 2 se migran sin borrar datos ni cambiar IDs.

## Editor visual

- **Toca** un elemento para seleccionarlo inmediatamente. **Arrastra** para moverlo, sin pulsación larga. **Editar** abre sus propiedades.
- Los elementos pequeños tienen una zona táctil mínima de 48 dp. El lienzo resuelve primero los impactos sobre la composición visible, de delante hacia atrás; usa el área ampliada si no hay un impacto visible.
- La selección no abre formularios ni cambia la altura del lienzo. La geometría inicial del gesto queda fija; los cambios se muestran en memoria y solo se guardan al soltar. Un gesto cancelado descarta su desplazamiento.
- Para cambiar tamaño, arrastra el tirador inferior derecho de un elemento seleccionado. Para elementos pequeños o imágenes rotadas, utiliza Ancho/Alto en **Editar**. El tirador se oculta en esos casos para no interferir con el movimiento. El tirador conserva la esquina superior izquierda mientras haya espacio dentro del lienzo.
- Los textos se ajustan al ancho del cuadro mediante salto de línea; el contenido que excede el alto se limita. La tipografía mantiene su proporción respecto del lienzo.
- **+ Imagen** importa PNG, WebP o JPEG como elemento independiente. PNG/WebP conservan transparencia. Ancho/alto permiten escalar el área de la imagen; la imagen conserva su proporción dentro de esa área. **Editar** ofrece rotación, volteo horizontal y opacidad.
- La fila de selección ofrece **Bloquear/Desbloquear**, **Duplicar**, **Al frente**, **Atrás** y **Eliminar**. Desliza horizontalmente las filas de herramientas para ver todos sus controles.
- Un elemento bloqueado se puede seleccionar, pero no mover ni redimensionar con el dedo. **Elementos** lista las capas de arriba hacia abajo y permite seleccionar elementos cubiertos, bloqueados o invisibles.
- La copia de un elemento conserva sus propiedades y destinos; aparece ligeramente desplazada y delante de los demás. También conserva su bloqueo.

## Fondo

**Fondo** permite elegir color, imagen privada y modo **Encajar / Fit**, **Rellenar / Fill** o **Ajuste manual**. Fit mantiene la imagen completa; Fill recorta al centro. Manual parte de Fit y permite escala y desplazamiento, con controles numéricos visuales y **Mover fondo** para arrastrarlo sobre el lienzo. Bloquear fondo evita modificar su colocación accidentalmente. Los modos y ajustes se guardan con la lámina.

## Álbum y conexiones

- Al abrir un proyecto aparece el **Álbum de láminas**, con miniaturas de la composición completa.
- **Grande**, **Mediana** y **Pequeña** muestran 1, 2 o 3 columnas. La preferencia queda guardada en el dispositivo.
- Cada tarjeta muestra los números visuales de sus destinos y el avance automático; **FIN** indica que no contiene botones ni avance automático. Un botón sin destino se indica mediante `?` o `Sin destino`.
- **Opciones → Mover antes/Mover después** cambia el orden visual. Las conexiones usan IDs estables. El punto de entrada del MVP se conserva: Play comienza en la primera lámina creada que aún exista (menor ID), marcada **Inicio**, aunque se reordene el álbum.
- **Duplicar lámina** inserta una copia a continuación de la original, con toda su composición. Los botones copiados **conservan exactamente sus IDs de destino**, incluso si apuntaban a la propia original. No se redirigen a la copia automáticamente.
- Al editar un botón, el selector de destino muestra tarjetas con miniaturas y nombres.
- **Ver acciones** añade etiquetas temporales de destino sobre los botones del editor. Nunca aparece en Play.

## Calco

**Calco** permite escoger otra lámina mediante miniatura y regular su opacidad de 0 a 90 %. La referencia se dibuja detrás; la composición actual se vuelve parcialmente translúcida para verla incluso bajo un fondo sólido. Solo los elementos de la lámina actual reciben eventos táctiles. **Desactivar calco** vuelve a la vista normal. La referencia y opacidad se conservan localmente por lámina, también tras recreación/reapertura. Si se elimina la referencia, deja de mostrarse. El calco no aparece en miniaturas ni reproducción.

## Persistencia y arquitectura

Se conserva `Compose UI → StoryViewModel → StoryRepository → SQLiteOpenHelper`.

- `data/Models.kt`: proyecto, lámina y elementos de texto/botón/imagen.
- `data/StoryRepository.kt`: migración y operaciones transaccionales sobre SQLite privado.
- `state/StoryViewModel.kt`: un escritor serializado en `Dispatchers.IO`. Al destruir el ViewModel, el escritor termina las acciones ya confirmadas antes de cerrar la base.
- `ui/SlideCanvas.kt`: lienzo común para editor, miniaturas y Play, controlador de gestos y geometría temporal.
- `ui/Geometry.kt`: geometría y hitboxes con rotación.
- `ui/LocalImages.kt`: decodificación fuera del hilo principal y caché de bitmaps limitada a 24 MB. Miniaturas usan resolución reducida. Android 28+ aplica orientación EXIF mediante ImageDecoder.
- `ui/Album.kt`: álbum y selector visual reutilizable.
- `ui/EditorDialogs.kt`: propiedades, fondo y calco.
- `ui/TaleFrameApp.kt`: navegación y controles.

### Esquema 1 → 2

La migración se ejecuta dentro de la transacción de SQLiteOpenHelper. Añade orden visual y modo/escala/posición/bloqueo del fondo a las láminas. Reconstruye únicamente la tabla de elementos para admitir `image` junto a `text` y `button`, conservando todos sus IDs, datos, referencias y la secuencia AUTOINCREMENT, incluidos IDs eliminados.

Los elementos añaden referencia a imagen, ancho/alto normalizados, rotación, volteo, opacidad, bloqueo y orden de capa. Los elementos antiguos mantienen tamaño automático (`0`) y el sistema anterior de coordenadas sobre el espacio disponible. El orden inicial de láminas/capas conserva el orden por ID del MVP. El fondo antiguo conserva Fill, escala 1 y desplazamiento 0.

Reordenar y duplicar láminas, y cambiar capas, son operaciones transaccionales. Borrar láminas conserva la política anterior: sus elementos se eliminan y las conexiones entrantes quedan sin destino. El repositorio rechaza destinos de otro proyecto. Los movimientos y redimensionamientos usan actualizaciones de geometría específicas, sin sobrescribir otras propiedades con una copia antigua. Los diálogos aplican solo las diferencias sobre la fila actual; bloquear actualiza únicamente el bloqueo. Así, una edición confirmada inmediatamente después de un drag no restaura coordenadas o tamaños antiguos.

Las imágenes se copian al almacenamiento privado antes de guardar su referencia. No se depende de permisos URI permanentes ni de la disponibilidad posterior del proveedor. Límite de importación: 40 MB. Las copias comparten recursos y la limpieza consulta referencias tanto de fondos como de elementos antes de eliminar archivos. Desinstalar la aplicación elimina las historias.

### Causa del retraso del MVP

`detectDragGesturesAfterLongPress` imponía una espera y podía cancelar el reconocimiento si el dedo empezaba a moverse antes de completarla. Compartía cada elemento con `clickable`, que abría un diálogo al tocar. Los elementos pequeños dependían además del área del texto. No había escrituras SQLite por cada movimiento: el MVP ya guardaba al terminar.

La nueva interacción tiene un único controlador en el lienzo: selecciona en DOWN, reconoce movimiento con el umbral táctil normal de Android, mueve en memoria y confirma al soltar. Los cambios de selección/estado no reinician el controlador. Los ajustes conservan un borrador visual hasta que SQLite confirma la geometría, evitando un salto a la posición anterior durante el guardado. No se añaden esperas artificiales.

## Privacidad

Sin INTERNET, telemetría, backend, autenticación ni dependencias de servicios remotos. Backup y transferencia del sistema continúan deshabilitados/excluidos. Imágenes, SQLite y preferencias permanecen en almacenamiento privado local. No se agregaron dependencias de ejecución.

## Validación

```bash
./gradlew test
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
./gradlew lint
git diff --check
```

Las pruebas cubren geometría, selección inmediata, gesto corto, ausencia de guardado durante drag, cancelación, tamaño, bloqueo, calco/acciones excluidos de Play, migración real de esquema 1, IDs y secuencias, conexiones, transparencia PNG/WebP, propiedades visuales, recursos compartidos, orden/capas, duplicación, recuperación tras reabrir SQLite, preferencias y recreación de Activity. El flujo anterior de creación y reproducción continúa probado.

La validación de la Iteración 3 se describe más abajo. Se mantienen todas las pruebas anteriores.

Se comprobó también un cierre real con `am force-stop` y reapertura del APK final: las filas completas y el archivo privado permanecieron idénticos, incluidos fondo manual, dimensiones, imágenes, rotación, volteo, opacidad, bloqueo, capas y destinos. La UI reconstruyó la composición. Los datos temporales de la comprobación se retiraron y se restauró la base original del emulador. La respuesta táctil de esta iteración aún debe comprobarse en hardware físico.

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Pendiente y comprobación en teléfono

**Deshacer/rehacer queda pendiente.** El repositorio elimina archivos al desaparecer su última referencia. Un historial correcto necesita comandos con estados anterior/posterior, retención temporal de archivos referenciados por el historial, restauración transaccional de filas/capas/destinos y reglas de invalidación de redo. No se implementó una pila de estados que pueda recuperar filas sin sus imágenes.

El movimiento usa un dedo; las imágenes se escalan/rotan mediante propiedades o tirador, sin gestos multitáctiles. El texto no reduce automáticamente la fuente para caber. En Android 24–27 el decodificador de respaldo no corrige orientación EXIF. Personajes, presets, plantillas y funcionalidades online siguen fuera del alcance.

En el teléfono conviene revisar toques y arrastres rápidos/repetidos sobre elementos pequeños y solapados, bloqueo/desbloqueo desde **Elementos**, importación de PNG/WebP transparentes, tiradores y controles en pantallas pequeñas, fondos verticales/horizontales en los tres modos, calco para alineación y reapertura de un proyecto antiguo tras instalar el APK sobre el MVP. La fluidez física y la respuesta del proveedor de archivos deben confirmarse en el dispositivo real.


## Iteración 3: multimedia local

Las filas de herramientas se deslizan horizontalmente. **+ GIF**, **+ Video** y **+ Secuencia** crean elementos independientes con los mismos gestos, tamaño, rotación, opacidad, bloqueo y capas del editor anterior. **Fondo** importa imagen, GIF o video y conserva Fit/Fill/Manual. **Editar → Reemplazar recurso** permite recuperar un recurso dañado. **Fondo → Eliminar recurso de fondo** conserva el color sólido.

GIF y videos automáticos se reproducen en el editor y en Play. Las miniaturas, el selector y el calco son estáticos: no mantienen reproductores ni audio. El audio del video respeta su configuración; inicialmente está silenciado. El audio asociado a la lámina se reproduce solo en Play, para que trabajar en el editor no inicie música involuntariamente.

**Audio / Tiempo** permite importar/reemplazar/eliminar audio de la lámina, regular su volumen y escoger repetición o una vez. En el mismo diálogo se activa **Después de X segundos**, con destino visual y transición propia. El editor y álbum muestran `⏱ segundos → destino`. El temporizador solo funciona en Play.

**+ Secuencia** selecciona varias imágenes; el orden inicial corresponde al devuelto por el proveedor de archivos. En **Editar** se cambia la duración, repetición y orden (`↑`), se quitan imágenes y se añaden más. La secuencia es una sola capa. Todas sus imágenes heredan siempre la posición, tamaño, rotación, volteo y opacidad del elemento; la herencia está activada por construcción, sin propiedades independientes por frame. Una secuencia sin repetición conserva la última imagen. Reemplazar su recurso convierte el elemento en una imagen estática; se pueden volver a añadir imágenes.

### Multimedia y lifecycle

- `ui/LocalMedia.kt` centraliza la presentación y propiedad de reproductores. Android 28+ usa `ImageDecoder` / `AnimatedImageDrawable` para GIF, decodificados en IO y reducidos a un máximo de 1536 px por lado. Android 24–27 usa `Movie` como respaldo local; está obsoleto pero limitado a estos sistemas y sin dependencia adicional. Ambos permiten repetir o reproducir una vez.
- Video y audio usan `MediaPlayer`, con preparación asíncrona, volumen, repetición y captura de errores. El video se presenta en `TextureView`, para que Compose pueda componer sus capas, rotación y opacidad. Una matriz mantiene Fit/Fill; Manual añade la escala y desplazamiento existentes.
- Cada instancia tiene un propietario con `release()` idempotente, callbacks invalidados antes de liberar y `Surface` liberada al destruir la textura. Al cambiar de lámina, eliminar/reemplazar media, abandonar editor/Play, destruir/recrear la pantalla o pasar a STOP se liberan los reproductores. GIF detiene el drawable y retira su callback. Al volver de segundo plano se reinicia la reproducción de la lámina; no se conserva el segundo exacto ni el tiempo pendiente del avance.
- La identidad del reproductor depende del recurso y su configuración, nunca de x/y/ancho/alto. Un drag modifica solo la geometría, sin reconstruir decodificadores ni escribir por cada movimiento. Una revisión persistida fuerza la recarga cuando se reemplaza el mismo recurso tras un fallo.
- Video automático comienza al prepararse. Video manual muestra el primer frame en el editor y **▶ / Pausa** en Play; ese control también reinicia un video terminado. Una reproducción sin loop conserva el último frame en la textura cuando el codec lo permite. El final del video solo marca su finalización: no ejecuta navegación.
- `ui/Playback.kt` dibuja las transiciones usando composiciones estáticas y comienza la multimedia del destino al terminarlas. Para GIF/video, el contenido estático es el primer frame/poster; no se captura exactamente el último frame del reproductor saliente. No quedan reproductores anteriores funcionando detrás de la transición.

Referencias de las APIs utilizadas: [MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer), [AnimatedImageDrawable](https://developer.android.com/reference/android/graphics/drawable/AnimatedImageDrawable).

### Acciones temporales y transiciones

El botón y el temporizador usan la misma puerta de salida `ExitGate`. Cada visita recibe un token; solo la primera salida lo consume. Un temporizador con un token antiguo no puede salir de una visita posterior. El efecto de temporización se cancela al cambiar de visita, salir de Play o pasar a segundo plano. Los botones permanecen activos durante la espera; durante la transición no hay nuevas salidas. Un enlace a la propia lámina inicia una visita nueva, incluida su multimedia y su temporizador.

Cada botón guarda **Ninguna**, **Fade**, **Slide izquierda** o **Slide derecha**, más duración de 200 a 1000 ms. El avance automático guarda su propia configuración, independiente de los botones. Los diálogos muestran una prueba A → B con **Probar transición**, recortada a su área de preview. Ninguna realiza el cambio inmediatamente. El tiempo de avance se cuenta desde la entrada de la lámina, después de la transición; no espera indefinidamente a un recurso incompatible.

### SQLite 2 → 3 y archivos

Se añaden `settings TEXT NOT NULL DEFAULT '{}'` a láminas y elementos, y `auto_target_id` a láminas, con FK `ON DELETE SET NULL`. Los ajustes JSON incluyen media, frames, duración, audio, volúmenes, loop/autoplay, revisiones y transiciones. El destino temporal sigue siendo una columna con integridad referencial, validada para el mismo proyecto. La migración 2 → 3 es aditiva y no reconstruye tablas. Desde versión 1 se ejecuta primero la migración 1 → 2 existente y después la nueva. Se conservan IDs, conexiones, secuencias, geometría y rutas de archivos. Los valores por defecto mantienen imágenes estáticas y fade de 300 ms; las láminas antiguas no adquieren audio ni avance automático.

Las importaciones SAF copian los bytes al directorio privado existente `files/backgrounds`, sin depender de permisos URI posteriores. Imágenes/GIF: **40 MB**; video: **500 MB**; audio: **100 MB**. Los GIF se limitan además a 4096 px por lado y 8 megapíxeles. Imágenes se validan mediante bounds; audio/video mediante metadatos y nuevamente por el reproductor al abrirse. Toda copia, validación y hash se ejecuta en IO. El ViewModel conserva su cola serial de escrituras y publica el estado de UI en Main.

Los nuevos archivos usan SHA-256 del contenido para reutilizar importaciones idénticas. Las duplicaciones de láminas/elementos comparten las rutas existentes, incluidos archivos de Iteración 2. Los archivos antiguos no se renombran. Si una copia compartida está dañada, una nueva importación válida usa una ruta distinta. La limpieza considera fondos, elementos, audio y todos los frames; solo elimina un archivo cuando desaparece su última referencia. Importar una secuencia adjunta todas sus rutas antes de la limpieza; un fallo limpia las copias sin referencia. Quitar el primer frame actualiza la referencia principal a la nueva primera imagen.

### Formatos, límites y comprobación física

No se añadieron dependencias de ejecución ni permisos. Continúan **sin INTERNET**, `allowBackup=false`, `fullBackupContent=false` y exclusiones de backup/transferencia. No hay servicios externos.

Los formatos/codecs dependen del Android y del hardware: se probaron GIF, MP4 con H.264/AAC y WAV PCM mediante fixtures locales generados con FFmpeg. Como elección práctica para probar en un teléfono, usar MP4 H.264/AAC y audio AAC/MP3/WAV compatible con el dispositivo. No se promete soporte universal de contenedores, codecs, video HDR, DRM ni resolución arbitraria. La preparación fallida muestra un error para reemplazar o eliminar el recurso. Android 24–27 y sus codecs/GIF de respaldo necesitan verificación física adicional.

Limitaciones deliberadas: la secuencia tiene una duración común para todos los frames y herencia de transformación siempre activa; no ofrece transformaciones individuales por imagen. No hay pausa/reanudación exacta después de recreación/segundo plano. Las transiciones usan posters estáticos de multimedia. No hay precarga de la siguiente lámina, límite global de duración del proyecto ni mezclador/pistas globales. La cantidad simultánea de videos depende de los decodificadores disponibles; errores de codec se muestran en cada recurso.

Probar especialmente en teléfono: GIF grandes/repetición única, videos verticales y horizontales en Fit/Fill/Manual, transparencia/capas/opacidad/rotación sobre video, drag rápido con multimedia activa, video manual y conservación del último frame, volumen y mezcla de audio/video, navegación rápida frente al temporizador, enlace a la misma lámina, salir a Home y volver, rotar/recrear/cerrar y reabrir, proveedor de archivos y reemplazo de archivos dañados. Instalar sobre Iteración 2 sin borrar datos y revisar los proyectos anteriores.

### Validación final de Iteración 3

**7 pruebas unitarias y 22 pruebas Android aprobadas**, incluidos todos los casos anteriores, en Pixel 7a / Android 17. `./gradlew test connectedDebugAndroidTest assembleDebug lint` y `git diff --check` completados correctamente. Lint: **0 errores y 16 avisos** (14 de plantilla/versiones y 2 sugerencias KTX).

`PlaybackLogicTest` cubre tokens, competencia entre salidas, frames y límites de transiciones. `MediaPersistenceTest` prueba migración real 2 → 3, propiedades multimedia, conexiones/FK, reapertura, duplicación, deduplicación, archivos corruptos y limpieza compartida. `PlaybackTest` verifica cancelación de temporizador por botón, autoavance/enlace propio, slideshow de una sola capa y geometría estable, GIF realmente animado y detenido al salir, liberación de video/audio, ausencia de recreación de players durante drag, STOP/START y recreación de Activity. Los fixtures locales de prueba están en `app/src/androidTest/assets`; se generaron con las fuentes sintéticas `testsrc` y `sine` de FFmpeg.

También se instaló el APK final y se realizó `am force-stop` + reapertura con una historia temporal que incluía video de fondo, GIF, audio, slideshow, transformaciones, destinos y transiciones. Se compararon todas las filas y los bytes privados de recursos antes/después; la UI reconstruyó la composición y mostró el indicador temporal. Se restauró la base original y se retiraron los fixtures de esa comprobación. El emulador se usó sin salida de audio: los tests verifican ownership/preparación/configuración, pero la calidad del sonido y los codecs de hardware requieren teléfono físico.

Archivos principales de esta iteración: `data/Models.kt`, `data/MediaOptions.kt`, `data/StoryRepository.kt`, `state/StoryViewModel.kt`, `ui/LocalMedia.kt`, `ui/Playback.kt`, `ui/MediaDialogs.kt`, `ui/EditorDialogs.kt`, `ui/SlideCanvas.kt`, `ui/TaleFrameApp.kt` y `ui/Album.kt`.
