# TaleFrame

Editor Android privado y offline de historietas interactivas por láminas. Kotlin + Jetpack Compose y SQLite local. Los proyectos de las Iteraciones 1–5 se migran sin borrar datos ni cambiar IDs.

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
- **Opciones → Mover antes/Mover después** cambia el orden visual. Las conexiones usan IDs estables. El punto de entrada del MVP se conserva: Sin configuración explícita, Play comienza en la primera lámina creada que aún exista (menor ID), marcada **Inicio**, aunque se reordene el álbum. Desde Iteración 5 se puede establecer otra lámina inicial.
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

**Deshacer/rehacer queda pendiente.** El repositorio elimina archivos al desaparecer su última referencia, incluida la biblioteca del proyecto. Un historial correcto necesita comandos con estados anterior/posterior, retención temporal de archivos referenciados por el historial, restauración transaccional de filas/capas/destinos y reglas de invalidación de redo. No se implementó una pila de estados que pueda recuperar filas sin sus imágenes.

El movimiento usa un dedo; las imágenes se escalan/rotan mediante propiedades o tirador, sin gestos multitáctiles. El texto no reduce automáticamente la fuente para caber. En Android 24–27 el decodificador de respaldo no corrige orientación EXIF. Las funcionalidades online siguen fuera del alcance; personajes/presets están disponibles desde Iteración 4 y plantillas editables desde Iteración 5.

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


## Iteración 4: biblioteca, personajes y estilos

**Biblioteca** se abre desde el proyecto o editor. Sus categorías son Personajes, Fondos, Imágenes, GIF, Videos, Audio y Presets. Los recursos muestran nombre, preview, usos y etiqueta **Sin usar**; admiten búsqueda por nombre, importación, renombrado, uso, reemplazo y eliminación segura. Las imágenes tienen miniaturas; GIF/video muestran una imagen estática, sin reproductores ni audio. Los posters se extraen en IO, con resolución reducida y caché limitada a 8 MB. Las cuadrículas son lazy y los conteos se calculan en una pasada por las referencias del proyecto. Entrar en la biblioteca retira el lienzo activo y libera sus reproductores.

**Usar** permite elegir visualmente una lámina y colocar el recurso como elemento, fondo o audio cuando corresponda. Las importaciones directas desde el editor también quedan registradas. Un recurso importado que aún no se utiliza permanece disponible en la biblioteca; borrar una instancia o lámina no elimina su entrada. Una importación parcialmente fallida de secuencia puede dejar recursos válidos **Sin usar**, para recuperarlos o borrarlos explícitamente.

### Archivo, recurso e instancia

El archivo privado sigue usando la deduplicación SHA-256 de Iteración 3. `resources` contiene una entrada por proyecto/ruta/tipo, con ID estable, nombre, categoría y revisión; proyectos distintos pueden compartir los mismos bytes sin compartir sus entradas de catálogo. Insertar o duplicar utiliza las rutas existentes y no vuelve a copiar archivos.

La instancia conserva su ruta y ajustes visuales, además del ID de origen. **Reemplazar** en biblioteca conserva el ID y cambia el archivo para las siguientes inserciones; las instancias ya colocadas mantienen su aspecto y sus archivos. Si el reemplazo coincide con otro recurso ya registrado en el mismo proyecto/tipo, se pide reutilizar ese recurso en lugar de fusionar IDs silenciosamente. Cambiar una expresión desde el editor sí modifica expresamente la instancia seleccionada.

Antes de borrar un recurso se cuentan elementos, fondos, audio, todos los frames de secuencias, expresiones y retratos. Se combinan referencias por ID y por ruta para incluir proyectos antiguos y snapshots anteriores a un reemplazo, sin contar dos veces una misma instancia. La eliminación se rechaza mientras exista cualquier uso en el proyecto. La limpieza física considera además todas las entradas de biblioteca y todas las rutas de todos los proyectos, por lo que no elimina un archivo compartido. No hay purga automática de recursos sin usar.

### Personajes y expresiones

Cada personaje tiene nombre, descripción opcional, retrato opcional y estilo de diálogo asociado. Al crearlo sin seleccionar un estilo, obtiene un preset propio. Sus expresiones tienen nombre, recurso de imagen y orden editable. El retrato puede omitirse: el selector utiliza la primera expresión como preview.

**+ Personaje** abre personaje → expresión y crea una imagen habitual del lienzo, con metadatos de personaje/expresión. **Cambiar expresión** conserva posición, tamaño, rotación, volteo, opacidad, bloqueo y capa; también funciona sobre una instancia bloqueada. Duplicación de elemento/lámina conserva estos metadatos. Opcionalmente se pueden seleccionar varias expresiones como una secuencia de una sola capa, usando duración y repetición de Iteración 3. Los frames heredan siempre la transformación del elemento.

Editar la definición o imagen de una expresión afecta las siguientes inserciones; no cambia automáticamente las escenas existentes. Borrar personaje/expresión conserva las imágenes colocadas y sus snapshots, retirando únicamente las asociaciones mediante `SET NULL`.

### Diálogos, Narrador y presets

**+ Diálogo** permite personaje, **Narrador**, diálogo genérico o preset independiente. La inserción aplica inmediatamente el estilo y deja el texto listo para editar. Cada proyecto tiene un Narrador propio, editable, inicialmente rectangular y semitransparente. La opción **Mostrar nombre** guarda y muestra el nombre del hablante con el diálogo.

Los estilos ofrecen rectángulo, rectángulo redondeado, óvalo y círculo, familias de fuente locales, tamaño relativo, alineación, color de texto/fondo, opacidad de fondo y borde. `DialogueVisual` es el renderer común para editor, Play, miniaturas y previews de estilos. Las formas oval/círculo reservan espacio interior para el texto; el círculo se inscribe en el cuadro del elemento. El contenido que excede el área no reduce automáticamente la fuente.

Los presets de diálogo y apariencia de botón guardan estilos. Los presets de acción guardan transición y duración, **sin destino**. En propiedades del botón, los selectores visuales de apariencia/acción mantienen el destino actual; los ajustes avanzados se despliegan con **Estilo y presets**. **Guardar como preset** y **Guardar acción como preset** crean una copia nombrada desde la instancia. El temporizador también permite copiar una transición desde un preset de acción.

Cada inserción copia valores completos: editar la instancia no modifica el preset, y editar el preset solo afecta las próximas inserciones. El ID de preset sirve como origen, sin herencia visual dinámica. No se aplica ningún cambio masivo a escenas existentes. Un preset asociado a un personaje no puede eliminarse hasta reasignarlo; el Narrador se puede editar, pero no eliminar.

### SQLite 3 → 4 y archivos principales

La arquitectura sigue siendo `Compose UI → StoryViewModel → StoryRepository → SQLiteOpenHelper`; `LibraryStore` es la fachada de operaciones de catálogo del repositorio y usa su mismo escritor IO y base privada. No hay una segunda base ni caché persistente paralela.

La migración aditiva crea `resources`, `characters`, `expressions` y `presets`, con índices por proyecto/personaje y un único Narrador por proyecto. Añade FKs anulables a elementos (`resource_id`, `character_id`, `expression_id`, `preset_id`) y láminas (`background_resource_id`, `audio_resource_id`). Estilos, nombre del hablante/origen y frames de expresiones se guardan en los ajustes JSON existentes. Todas las asociaciones se validan dentro del mismo proyecto.

El backfill registra las rutas previas de fondos, elementos, audio y frames y crea Narradores, sin copiar ni renombrar archivos, ni reescribir ajustes antiguos. Los elementos previos usan el estilo `legacy`, manteniendo su apariencia y tamaño automático. Se conservan IDs, secuencias AUTOINCREMENT, conexiones, capas, geometría y multimedia; desde esquemas 1/2 se ejecutan primero las migraciones anteriores dentro de la misma transacción del helper.

Archivos nuevos principales: `data/LibraryModels.kt`, `data/LibraryStore.kt`, `ui/LibraryScreen.kt`, `ui/LibraryDialogs.kt`, `ui/DialogueStyle.kt` y `ui/LocalPosters.kt`. Se extendieron `data/Models.kt`, `data/MediaOptions.kt`, `data/StoryRepository.kt`, `ui/TaleFrameApp.kt`, `ui/EditorDialogs.kt`, `ui/MediaDialogs.kt`, `ui/SlideCanvas.kt` y `ui/LocalMedia.kt`. El controlador de drag conserva selección inmediata, borrador en memoria y guardado al soltar. No se agregaron dependencias ni permisos; continúa sin INTERNET y con backup/transferencia deshabilitados.

### Validación y límites de Iteración 4

**10 pruebas unitarias y 30 pruebas Android aprobadas** en Pixel 7a / Android 17. `./gradlew test connectedDebugAndroidTest assembleDebug lint` terminó correctamente. Lint: **0 errores y 18 avisos**, de plantilla/versiones y sugerencias KTX. Se conserva la suite anterior completa.

`LibraryLogicTest` cubre conteos y snapshots. `LibraryPersistenceTest` prueba migración real de esquema 3, conservación exacta de settings/IDs/rutas, referencias y borrado seguro, deduplicación, reemplazo, personajes/expresiones, cambio con geometría bloqueada, secuencias, presets, reapertura y aislamiento entre proyectos. `LibraryFlowTest` recorre la UI de creación de personaje/expresiones, inserción, drag, bloqueo, cambio de expresión, diálogo/Narrador, recreación y Play. `PresetEditorTest` verifica copia independiente de apariencia/transición y conservación del destino.

Se instaló el APK final y se comprobó `am force-stop` + reapertura con una historia temporal que incluía catálogo, personaje, expresiones, estilo de diálogo, Narrador, presets de botón/acción, geometría bloqueada y multimedia anterior. Las filas completas de las siete tablas y los bytes privados permanecieron idénticos; la UI reconstruyó la escena y mostró el personaje en Biblioteca. Se restauró la base original del emulador y se retiraron todos los fixtures temporales. `git diff --check` también pasó.

Límites: las expresiones usan imágenes estáticas; GIF/video se reutilizan como recursos independientes. No hay globos de habla/pensamiento, fuentes descargadas, herencia dinámica de presets, plantillas completas ni deshacer/rehacer. El tamaño de bibliotecas muy grandes y los codecs simultáneos dependen de memoria y hardware; se mantienen los límites de importación de Iteración 3.

En teléfono físico conviene importar desde proveedores reales, reutilizar archivos y comprobar **Sin usar**/borrado protegido; crear varios personajes y expresiones; cambiar expresión tras mover, rotar, voltear y bloquear; comprobar nombre/Narrador y formas con textos largos; comparar edición de instancia con edición de preset; verificar que aplicar un preset no cambie el destino; abrir biblioteca con GIF/video y volver al editor; probar calco/capas, audio y cierre forzado. Instalar sobre Iteración 3 sin borrar datos y revisar historias anteriores. El emulador se ejecutó sin salida de audio: calidad de sonido y codecs reales requieren verificación física.


## Iteración 5: composición y presentación

### Identidad y entrada del proyecto

La interfaz utiliza superficies blanco rosado/gris cálido y un acento rosa empolvado de contraste alto, con tarjetas y diálogos de esquinas coherentes. El tema oscuro usa carbón, superficies gris cálido oscuro y rosa desaturado. **Tema** en la lista de proyectos permite **Seguir sistema / Rosa claro / Oscuro**, con preferencia local y barras del sistema adaptadas. Los colores de las composiciones y presets existentes no se sustituyen al cambiar el tema de la aplicación.

Las tarjetas de proyecto tienen portada, nombre y cantidad de láminas. Sin portada muestran un placeholder TaleFrame. **Cambiar portada** en la entrada del proyecto selecciona una imagen de su Biblioteca, permite reemplazarla o quitarla. La portada es una asociación al recurso: sigue su archivo actual si ese recurso se reemplaza en Biblioteca. Su referencia impide eliminar el recurso mientras esté asociada.

La entrada se integra con el Álbum, sin una pantalla adicional: muestra portada, cantidad de láminas/borradores e inicio, y ofrece **Continuar edición**, **Reproducir desde inicio**, Personajes, Presets y Plantillas; Biblioteca permanece accesible en la cabecera. Continuar recupera la última lámina abierta desde el Álbum, con preferencia local por proyecto. **Reproducir desde esta lámina** está en la toolbar del editor; el Play de su cabecera también inicia en la lámina actual. Play desde el Álbum/Biblioteca inicia en el inicio del proyecto. **Salir de reproducción** y Back regresan al editor/Álbum.

### Inicio, borradores y conexiones

**Opciones → Establecer como lámina inicial** guarda un ID estable; el Álbum marca **Inicio**. Si no se define, o se elimina la inicial, se conserva el criterio histórico del menor ID. Cambiar orden visual no modifica la entrada.

**Marcar borrador / Marcar completa** se ofrece en Opciones y en el editor. Los borradores siguen siendo totalmente editables. **Omitir láminas en borrador** es una preferencia del proyecto. Si la inicial está omitida, se empieza por la primera lámina no borrador por ID; la entrada del proyecto muestra explícitamente ese inicio efectivo. Si todas están omitidas, se muestra un aviso y no se abre Play. La prueba desde una escena actual omitida también avisa; desactivar la opción permite reproducirla.

Un botón o temporizador dirigido a un borrador omitido muestra **Destino en borrador** y mantiene la escena actual. No redirige hacia otra lámina ni sigue conexiones por su cuenta. Se puede cerrar el aviso, elegir otra acción o salir. La acción bloqueada no consume la puerta de salida de la visita; el temporizador no reintenta continuamente. Los destinos guardados no se alteran al omitir borradores. **FIN** sigue identificando escenas sin botones ni autoavance; un botón sin destino se marca como tal.

### Plantillas editables

**Nueva lámina** ofrece **Vacía** y previews de plantillas incluidas/propias. Hay seis bases: Lámina completa, 2 verticales, 2 horizontales, 3 paneles, 4 paneles y Conversación. Las incluidas tienen IDs negativos reservados y se generan localmente; no tienen archivos externos ni filas copiadas a cada proyecto. Se distinguen de las plantillas del usuario y no se pueden renombrar/eliminar; **Duplicar plantilla** crea una copia propia editable en su administración.

**Guardar como plantilla** en el editor toma un snapshot de fondo, audio, geometría, capas, textos, estilos, imágenes, personajes, expresiones, paneles y multimedia. La plantilla pertenece al proyecto y no cambia cuando se edita/elimina la lámina original. Su preview usa el lienzo común en modo estático. Biblioteca → Plantillas permite crear una composición, guardar desde su editor, renombrar, duplicar, eliminar y aplicar en una **nueva** lámina; no sobrescribe composiciones existentes.

La aplicación crea lámina y elementos con IDs nuevos dentro de una transacción. Reutiliza rutas privadas; no copia archivos. Mantiene metadatos de personaje/expresión/preset si sus definiciones todavía existen. Si se eliminaron, retira la asociación conservando los nombres, imágenes y estilos del snapshot.

Las plantillas guardan composición/apariencia: no almacenan destinos de botones ni destino de autoavance. Al aplicar, los botones quedan **Sin destino** y el temporizador desactivado, con duración/transición disponibles para configurarlos. La nueva escena no hereda el estado Borrador ni se vuelve inicial automáticamente. **Duplicar lámina**, como antes, sí conserva destinos y estado de la lámina: se distingue de aplicar una plantilla.

Todas las rutas de plantillas, incluidos fondo, audio y frames, participan en la limpieza física. Sus IDs/rutas participan en los conteos de Biblioteca. Se protege el archivo anterior si un recurso se reemplaza después de guardar una plantilla. Eliminar una plantilla no elimina sus láminas derivadas.

### Paneles y encuadre

Un panel es un elemento `image` con opciones de encuadre en JSON; no necesita un nuevo tipo SQL ni reconstruir la tabla estable de elementos. Puede existir vacío. **+ Panel** crea uno independiente, y las plantillas incluidas usan estos mismos elementos. Selección inmediata, drag, tamaño, rotación, volteo, opacidad, bloqueo, capas, duplicación y eliminación utilizan el controlador existente.

**Editar** permite asignar imagen por importación o desde Biblioteca, vaciar el panel y elegir **Fit / Fill / Manual**. Manual parte de Fill y ofrece escala y posición interior horizontal/vertical. Los cambios de encuadre no mueven el panel: el contenido se recorta a su área. Las transformaciones externas mueven/rotan el panel completo. Los paneles son independientes; no imponen una cuadrícula rígida después de aplicar la plantilla. La asignación desde Biblioteca usa imágenes estáticas; la importación/reemplazo multimedia sigue el soporte anterior del elemento.

### Texto, selección y botones

Se añaden **Diálogo** (bocadillo con cola sencilla fija) y **Pensamiento** (óvalo con burbujas), redimensionables y compatibles con colores, Narrador/personajes y presets. Los controles comunes incorporan negrita/cursiva, manteniendo las fuentes locales, tamaño relativo y alineación existentes. El contenido se recorta/abrevia al exceder el cuadro: **Texto no cabe** aparece solo en editor, sin modificar automáticamente el tamaño elegido. No aparece en Play ni miniaturas.

Los botones muestran presión mediante una reducción moderada de escala/opacidad. El preset/instancia permite **Sin efecto / Brillo suave / Pulso suave**, por defecto desactivado. El pulso solo se anima en reproducción activa; no corre en álbum, biblioteca, calco o transiciones. Las previews muestran forma, colores, fuente y brillo del preset, usando su nombre como texto de botón.

La selección usa el acento del tema y distingue bloqueo con un indicador discreto. Las guías de centro/bordes aparecen temporalmente durante el borrador de drag; no hacen snap ni escriben SQLite. La toolbar conserva dos filas de altura fija: **Herramientas** reduce la fila a controles de capas/reproducción y permite recuperar las acciones; sus descripciones accesibles indican reducir/mostrar. Cambiar selección no altera la altura ni cancela gestos. Las paletas de color tienen controles de 48 dp; las filas y diálogos ofrecen scroll para pantallas pequeñas.

### SQLite 4 → 5 y arquitectura

Se conserva `Compose UI → StoryViewModel → StoryRepository → SQLiteOpenHelper`. `TemplateStore` comparte repositorio y cola IO con `LibraryStore`. `TemplateCodec` guarda un cuerpo JSON autocontenido con propiedades visuales, rutas y IDs de origen; no serializa objetos Android ni almacena bitmaps.

La migración aditiva añade a `projects` `cover_resource_id` y `initial_slide_id` (FK con `ON DELETE SET NULL`) y `skip_drafts` (0 por defecto); añade `draft` a `slides` (0) y crea `templates(id,project_id,name,body)` con índice por proyecto y eliminación en cascada. Paneles, negrita/cursiva y efecto de botón amplían los JSON existentes, con defaults que conservan estilos previos. No se reconstruyen tablas ni se reescriben settings de historias anteriores. Desde v1/v2/v3 se ejecutan primero las migraciones existentes, incluyendo Biblioteca, y después v5. Se validan portadas, entrada y aplicación dentro del mismo proyecto.

Archivos nuevos: `data/TemplateStore.kt`, `ui/TemplateDialogs.kt`, `ui/PanelVisual.kt` y `ui/ProjectEntry.kt`. Integraciones principales: `Models`, `MediaOptions`, `LibraryModels`, `StoryRepository`, `TaleFrameApp`, `Album`, `LibraryScreen`, `LibraryDialogs`, `EditorDialogs`, `DialogueStyle`, `SlideCanvas`, `Playback` y `theme/Theme.kt`.

### Validación y comprobación física

**13 pruebas unitarias y 40 pruebas Android aprobadas** en Pixel 7a / Android 17. `./gradlew test connectedDebugAndroidTest assembleDebug lint` completó correctamente; lint: **0 errores y 19 avisos** (plantilla/versiones y sugerencias KTX). `git diff --check` pasó. Se mantuvieron todos los casos anteriores; el selector de volver en `LibraryFlowTest` se actualizó a la descripción accesible del control compacto, sin retirar sus comprobaciones de datos/gestos.

Se instaló el APK definitivo y se comprobó cierre real con `am force-stop` + reapertura: las ocho tablas completas y los bytes de los recursos privados permanecieron idénticos, incluyendo portada, inicio explícito, borradores, plantillas, paneles, formas/negrita/cursiva y multimedia previa. Se revisaron los temas claro/oscuro a 360 dp de ancho (720×1280, 320 dpi). Se restauraron la base y preferencias originales, retirando los fixtures temporales; el emulador no tuvo salida de audio.

Las pruebas nuevas cubren migración real v4 conservando cada columna previa e IDs/secuencias; portada/entrada/borradores; snapshot/aplicación de plantillas; archivos retenidos después de borrar escenas/reemplazar recursos; metadatos eliminados; layouts y duplicaciones; destinos bloqueados por botón/temporizador; overflow sin reducción de fuente; creación visual, portada, panel desde Biblioteca, guardado/reutilización de plantilla y toolbar tras recreación.

Límites: no hay cola de bocadillo libre, texto enriquecido por fragmentos, autoajuste de fuente, paneles vinculados entre sí ni reflujo automático del layout. Las guías son aproximadas y no hacen snap. Las plantillas se aplican en nuevas láminas del mismo proyecto; no hay importación/exportación ni actualización dinámica de sus instancias. Siguen pendientes deshacer/rehacer y verificación física de Android 24–27/codecs, grandes bibliotecas y mezcla real de sonido.

En teléfono físico revisar tema claro/oscuro y cambio del sistema, contraste y tamaño de fuente del dispositivo; scroll de toolbar y diálogos en pantallas pequeñas; encuadre de imágenes verticales/horizontales/transparencia en paneles; drag rápido, bloqueo y guías; overflow en bocadillos; presión/pulso de botones; portada y recursos retenidos por plantillas; reproducción desde inicio/actual con conexiones a borradores; cierre forzado y actualización sobre Iteración 4 sin borrar datos. No se añadieron permisos ni dependencias: continúa sin INTERNET, con recursos locales y backup/transferencia deshabilitados.

## TaleFrame 5.1 — Dogfooding UX y navegación

### Reproducción y transiciones

El flash tenía varias ventanas concretas: `localImage`/`localPoster` comenzaban con `null` incluso ante un hit de caché; la composición dibujaba entonces `slide.color` (blanco por defecto). Además, el progreso compartido empezaba en 1 antes de que `LaunchedEffect` lo llevara a 0, y el cambio de preview estática a reproducción recreaba contenido sin una imagen disponible mientras se preparaba GIF/TextureView.

La lectura inicial usa exclusivamente caché en memoria. Al salir se conserva la escena activa hasta preparar thumbnails/posters de origen y destino en IO; no se preparan MediaPlayers del destino. Cada salida posee un `Animatable(0f)` propio. La animación espera a que el destino llegue a composición. Su área y desplazamiento coinciden con el lienzo real; en horizontal las bandas exteriores permanecen negras y no cruzan la historia durante los slides. Los posters/thumbs se retienen durante la transición y la entrada, con un límite adicional de 12 MB por escena (24 MB durante el cruce), además de las cachés existentes. Las imágenes completas se siguen decodificando fuera de Main. El GIF muestra su imagen estática hasta tener drawable; TextureView permanece transparente hasta su primer buffer, sobre su poster. Los recursos dañados conservan el error existente y el color configurado. Un fondo blanco elegido intencionalmente sigue siendo blanco.

**Ninguna, Fade, Deslizar izquierda y Deslizar derecha** tienen tarjetas visuales, una demostración A/B y **Probar transición**. Duración: 200–1000 ms; Ninguna es inmediata después de preparar el destino. **Transición** en el menú rápido ofrece **Aplicar**; en Propiedades/Audio-Tiempo se confirma con Guardar. Cada acción conserva su propio efecto. No se añadieron los tres efectos opcionales.

La cabecera del editor ofrece **▶ Probar desde aquí**; la toolbar también ofrece **▶ Reproducir desde inicio**. La primera usa un punto de entrada temporal y conserva el inicio oficial y la política de borradores. **Mostrar nombre de lámina** es un control durante Play: añade/quita inmediatamente nombre y posición como overlay, sin guardar contenido narrativo.

### Editor y herramientas

Un DOWN selecciona; el movimiento usa el slop normal de Android, sin esperar tap, doble toque ni pulsación larga. Al finalizar un toque breve se conserva únicamente ID, tiempo y posición; un segundo toque breve sobre ese mismo elemento, dentro de los límites de Android y del área cercana, abre las acciones. Arrastrar, cancelar, tocar otra zona o mantener pulsado invalida el candidato. El reconocimiento no introduce un temporizador que retrase el drag.

**Doble toque** abre un menú breve para texto, botón, imagen, personaje, panel y multimedia. Texto/botón ofrecen **Editar texto** separado de **Propiedades**; el botón añade **Destino** y **Transición**; imágenes ofrecen cambiar recurso/expresión. Duplicar y bloquear están disponibles, y **Eliminar** queda como acción visible con confirmación posterior. El diálogo rápido enfoca el texto y aplica una diferencia sobre la fila más reciente, conservando geometría, capas y apariencia.

**Destino** y **Propiedades → Elegir destino** abren un selector a pantalla completa: miniatura, nombre, posición, Borrador y borde/etiqueta **ACTUAL**. Los loops a la lámina actual se permiten. La elección utiliza el ID, nunca el número visual. Los selectores de calco y uso de recursos conservan su función propia.

**Cuadrícula: Off / Fina / Media** persiste en preferencias locales. Es dibujo sobre el área útil, solo en editor, con divisiones de 5 % o 10 %. Convive con las guías; no hace snap ni escribe posiciones. La toolbar mide 60 dp, usa controles pastel más amplios y conserva reducción/expansión. Mantener pulsada una herramienta abre **Mover antes / Mover después** (una posición); su orden persiste por dispositivo. Las herramientas nuevas se añaden al orden guardado y las claves desconocidas se ignoran.

Un **swipe horizontal que comienza en espacio vacío** cambia de lámina según el Álbum: izquierda siguiente, derecha anterior. Requiere al menos 72 dp o 20 % del ancho, y predominio horizontal de 1,5×. Empezar sobre un elemento, su zona táctil ampliada o tirador conserva el gesto de ese elemento; mover un fondo manual también conserva prioridad. Un indicador superpuesto `12 / 34` dura 1,1 s y no cambia el tamaño del lienzo. Los extremos no navegan fuera del proyecto. Durante movimiento se mantiene el borrador en memoria y solo se confirma al soltar.

### Álbum, biblioteca y cuadernos

El estado del grid vive en la navegación del proyecto, fuera de la rama que compone el Álbum. Volver del editor conserva índice y offset. Mantener una tarjeta pulsada y arrastrar muestra **Insertar aquí** con borde; acercarse a los extremos desplaza el Álbum. Al soltar se guarda una sola operación de orden. Cancelar no persiste. Los IDs, imágenes, transiciones y conexiones narrativas no se recrean. Se mantienen Mover antes/Mover después como alternativa accesible.

Las tarjetas de proyecto usan un cuaderno cerrado estilizado: lomo, portada, título y borde de páginas, manteniendo las acciones de administración. La portada continúa siendo una referencia independiente de las láminas.

Biblioteca rechaza creaciones/renombrados con nombres de personaje equivalentes al normalizar mayúsculas, extremos y espacios repetidos, dentro del mismo proyecto: **Ya existe un personaje con este nombre.** Los duplicados anteriores no se eliminan ni renombran; pueden editar sus otros datos sin cambiar el nombre.

### Navegación base y SQLite 5 → 6

La migración es aditiva: `projects.automatic_base_navigation INTEGER NOT NULL DEFAULT 0` y `elements.base_navigation TEXT NULL`, con valores `previous` / `next` solo en botones. No reconstruye tablas ni reescribe los JSON, destinos, IDs, secuencias, orden ni archivos existentes. Las migraciones anteriores continúan encadenadas.

**Navegación base automática** está **OFF tanto en proyectos existentes como nuevos**. Activar ofrece **Generar y activar**: sincroniza Anterior/Siguiente por orden actual sin tocar botones narrativos. OFF conserva los botones base existentes como snapshots manuales; no sincroniza ni genera otros. Duplicar con OFF conserva la composición, incluidos los botones ya presentes, con los destinos originales.

Con ON, duplicar inserta inmediatamente después del origen y sincroniza: original Siguiente → copia; copia Anterior → original. También sincroniza al crear, reordenar o eliminar láminas. Se conserva un único botón por rol; primera solo Siguiente, última solo Anterior, única ninguno. Los botones existentes solo reciben el nuevo destino, conservando ID, posición, tamaño, bloqueo, estilo y transición; los que dejan de corresponder se eliminan. Los nuevos usan márgenes inferiores seguros. Duplicar un elemento base crea un botón narrativo ordinario para que una sincronización no elimine esa copia personal.

Solo filas con `base_navigation` participan en la sincronización. Los botones narrativos conservan sus destinos estables aunque las escenas cambien de posición. Borrar el destino mantiene `ON DELETE SET NULL`. En Play, los botones base son botones normales sin etiquetas técnicas. Las plantillas conservan su contrato visual: no transportan destino ni rol de navegación base; cualquier botón visual reutilizado queda como narrativo sin destino.

### Verificación y límites

Se mantienen las pruebas anteriores y se añaden casos de migración v5 comparando las ocho tablas/columnas anteriores y secuencias; navegación base ON/OFF, duplicación, extremos, orden, eliminación y geometría; nombres de personaje y deltas; doble toque, texto, eliminación y Play actual; scroll/drag del Álbum; selector ACTUAL/loops; cuadrícula/orden de herramientas; cache visible en la primera composición; muestreo de frames para las cuatro transiciones con colores, imagen, GIF y video; y drag con 45 capas, GIF/video, cuadrícula y sin recrear el MediaPlayer.

La preparación usa el primer frame/poster estático, no captura el último buffer del video/GIF saliente. Las primeras visitas con recursos grandes pueden mantener brevemente la escena anterior mientras se prepara el destino; no se alarga el fade. El scroll se conserva durante navegación/recreación, sin prometer recuperar el punto exacto después de matar el proceso. No se añadió snap, reorder multitáctil, deshacer ni exportación. La fluidez y los codecs se deben contrastar en hardware físico, especialmente Android 24–27, muchas imágenes distintas, videos grandes, sonido, gestos rápidos, teclado y pantallas pequeñas. Todo sigue local, sin INTERNET, cuentas, backend ni telemetría, y con backup deshabilitado.

Validación final de 5.1: **15 pruebas unitarias y 54 Android aprobadas** en la corrida completa. Después del ajuste del área de reproducción se revalidaron **17 casos de UI/reproducción**: siete por Gradle y diez directamente con AndroidJUnitRunner, porque el filtro de clases del ejecutor Gradle seleccionó solo la primera clase de la lista. Los diecisiete pasaron, incluyendo entrada/salida de colores, imágenes, GIF y video con las cuatro transiciones y un viewport horizontal.

`test`, `connectedDebugAndroidTest`, `assembleDebug`, `lint` y `git diff --check` completados; lint: **0 errores y 20 avisos**. El APK definitivo se instaló y se comprobó `am force-stop` + reapertura a 360 dp: ocho tablas completas y bytes de recursos conservados, además de navegación base ON/tipos, cuadrícula y orden de herramientas. Se revisaron editor claro/oscuro, biblioteca y cuadernos; los fixtures de esa comprobación se retiraron y se restauraron su base/preferencias originales. Continúa sin INTERNET y con backup/transferencia deshabilitados. No se hizo commit ni push.

## TaleFrame 5.2 — Respaldo portable

En la pantalla principal, **Exportar proyecto** en cada tarjeta guarda un único `Nombre.taleframe` mediante el selector de archivos de Android. **Importar** restaura ese archivo como un proyecto nuevo y abre su álbum. Si el nombre ya existe, ofrece **Importar como copia** con el siguiente número disponible o **Cancelar**; nunca sobrescribe ni fusiona proyectos. Después de exportar se puede **Compartir** usando el sistema Android.

El ZIP de formato **v1**, independiente de SQLite **v6**, contiene `manifest.json`, `project.json` y multimedia identificada por SHA-256. Incluye portada, configuración del proyecto, todas las láminas y capas, destinos narrativos/base/autoavance, personajes/expresiones, presets, plantillas personalizadas y **toda la Biblioteca, incluso recursos sin colocar**. Conserva los archivos antiguos de las plantillas. No incluye las preferencias globales del editor ni plantillas de fábrica.

La importación valida estructura, límites, referencias, tipos multimedia y hashes en cache antes de escribir permanentemente. Genera IDs nuevos dentro de una transacción y conserva las relaciones y las instancias de estilos. Los errores hacen rollback y limpian solamente las copias nuevas. IO se ejecuta fuera del hilo principal con progreso por fases/recursos. No se permite cancelar una operación ya aceptada; sí cancelar el selector o el diálogo de copia. Un marcador privado permite limpiar staging y multimedia huérfana al reiniciar tras una interrupción del proceso, conservando recursos ya confirmados o compartidos.

Se mantiene el almacenamiento privado, `allowBackup=false`, sin `INTERNET`, sin cuentas, nube, telemetría ni nuevas dependencias. No se modifica el editor ni la reproducción. El acceso principal de importación es el selector interno; no se registra una asociación global para archivos. El respaldo de todos los proyectos queda para una extensión posterior.

Contrato, límites y consideraciones de recuperación: [docs/backup-format.md](docs/backup-format.md). Pruebas de persistencia/seguridad: `ProjectBackupTest`; pruebas de pantalla/recreación/copia/reproducción/edición: `BackupFlowTest`.

Validación de 5.2: **15 pruebas unitarias + 67 Android**, con las **13 de respaldo** repetidas sobre el código final. Lint: **0 errores / 20 advertencias existentes**. La restauración mediante SAF en una instalación aislada vacía conservó las ocho colecciones, verificó ocho archivos multimedia y permitió reproducir, editar, exportar, compartir y reabrir tras cierre forzado. Detalles: [docs/backup-validation.md](docs/backup-validation.md).
