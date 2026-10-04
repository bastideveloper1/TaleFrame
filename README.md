# TaleFrame

Editor Android privado y offline de historietas interactivas por láminas. Kotlin + Jetpack Compose y SQLite local. Los proyectos del MVP se migran sin borrar datos ni cambiar IDs.

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
- Cada tarjeta muestra los números visuales de sus destinos; **FIN** indica que no contiene botones. Un botón sin destino se indica mediante `?` o `Sin destino`.
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

Resultados finales: **4 pruebas unitarias y 13 pruebas Android aprobadas** en Pixel 7a / Android 17. `test`, `connectedDebugAndroidTest`, `assembleDebug`, lint y `git diff --check` completados correctamente. Lint: 0 errores y 14 avisos de versiones/recursos de la plantilla.

Se comprobó también un cierre real con `am force-stop` y reapertura del APK final: las filas completas y el archivo privado permanecieron idénticos, incluidos fondo manual, dimensiones, imágenes, rotación, volteo, opacidad, bloqueo, capas y destinos. La UI reconstruyó la composición. Los datos temporales de la comprobación se retiraron y se restauró la base original del emulador. La respuesta táctil de esta iteración aún debe comprobarse en hardware físico.

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Pendiente y comprobación en teléfono

**Deshacer/rehacer queda pendiente.** El repositorio elimina archivos al desaparecer su última referencia. Un historial correcto necesita comandos con estados anterior/posterior, retención temporal de archivos referenciados por el historial, restauración transaccional de filas/capas/destinos y reglas de invalidación de redo. No se implementó una pila de estados que pueda recuperar filas sin sus imágenes.

El movimiento usa un dedo; las imágenes se escalan/rotan mediante propiedades o tirador, sin gestos multitáctiles. El texto no reduce automáticamente la fuente para caber. En Android 24–27 el decodificador de respaldo no corrige orientación EXIF. No se añadieron GIF, video, audio, personajes, presets, plantillas ni funcionalidades online.

En el teléfono conviene revisar toques y arrastres rápidos/repetidos sobre elementos pequeños y solapados, bloqueo/desbloqueo desde **Elementos**, importación de PNG/WebP transparentes, tiradores y controles en pantallas pequeñas, fondos verticales/horizontales en los tres modos, calco para alineación y reapertura de un proyecto antiguo tras instalar el APK sobre el MVP. La fluidez física y la respuesta del proveedor de archivos deben confirmarse en el dispositivo real.
