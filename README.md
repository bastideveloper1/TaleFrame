# TaleFrame

MVP Android local de historietas interactivas por láminas, en Kotlin y Jetpack Compose.

## Uso

1. Crea un proyecto y abre su tarjeta.
2. Crea láminas. La primera existente (orden de creación) inicia la historia.
3. Abre una lámina. **Fondo** permite color de la paleta, color HEX o una imagen local.
4. Añade **Texto** o **Botón**. Toca un elemento para modificarlo o eliminarlo.
5. Mantén pulsado y arrastra un elemento para moverlo. La posición se guarda al soltar.
6. Selecciona el destino de cada botón, incluyendo láminas anteriores o la misma lámina.
7. **Play** inicia desde la primera lámina, sin controles del editor. El botón Atrás de Android sale de la reproducción.

Los cambios se guardan al confirmar los diálogos o finalizar un arrastre. Los diálogos tienen **Cancelar** para descartar su borrador. Los proyectos y láminas tienen confirmación antes de eliminarse.

## Arquitectura y datos

- `data/Models.kt`: proyecto, lámina y elementos de texto/botón.
- `data/StoryRepository.kt`: SQLite privado mediante `SQLiteOpenHelper`, sin dependencias adicionales. Se eligió SQLite directo en vez de Room para mantener pequeño el MVP y evitar procesadores de código; las operaciones quedan encapsuladas en el repositorio.
- `state/StoryViewModel.kt`: estado observable, acciones serializadas y operaciones de disco en `Dispatchers.IO`.
- `ui/TaleFrameApp.kt`: navegación local, listas y diálogos.
- `ui/SlideCanvas.kt`: composición común para editor/reproductor, arrastre y transición fade de 300 ms.
- `MainActivity.kt`: montaje del ViewModel y Compose.

Las tablas tienen claves foráneas. Borrar un proyecto elimina sus láminas y elementos. Borrar una lámina elimina sus elementos y deja en NULL los destinos que apuntaban a ella. El repositorio rechaza destinos de otro proyecto. Los elementos tienen un tipo explícito `text` o `button` y conservan texto, colores, posición y destino.

El lienzo usa relación 3:4 y coordenadas normalizadas sobre el espacio disponible para cada elemento. Tamaños de texto y padding escalan con el lienzo; el editor y el reproductor usan la misma composición. Los elementos se mantienen dentro del lienzo. Se priorizó movimiento estable: no hay redimensionamiento libre.

Las imágenes se seleccionan mediante Storage Access Framework y se **copian a archivos privados** antes de guardar la referencia. No se depende de permisos URI persistentes ni de la disponibilidad posterior del archivo original. Se limita la importación a 40 MB y se decodifica con muestreo (máximo aproximado de 2048 píxeles por lado) fuera del hilo principal. Las imágenes sin referencias se eliminan al reemplazar fondos o borrar datos. Fondo sólido e imagen tienen propiedades separadas; una futura incorporación multimedia debe añadir un tipo de medio y la correspondiente migración, sin alterar elementos o conexiones.

No hay permiso INTERNET, cuentas, servicios remotos, telemetría ni bibliotecas de red añadidas. El respaldo en nube y la transferencia del sistema están deshabilitados/excluidos. Desinstalar la aplicación elimina sus historias.

## Validación

```bash
./gradlew test
./gradlew assembleDebug
./gradlew lint
./gradlew connectedDebugAndroidTest # requiere dispositivo/emulador Android
git diff --check
```

- Unitarias: límites de coordenadas y valores no finitos, además de la prueba existente.
- Persistencia Android: recreación del repositorio/base, igualdad de todos los datos, imagen privada después de borrar el original, posiciones, conexiones de retorno, borrados en cascada, destinos NULL, renombrado y rechazo de conexiones entre proyectos.
- Flujo Compose: creación de proyecto y tres láminas, fondo, texto, arrastre, botones con ramas y retorno, recreación de actividad, Play y ausencia de controles del editor.
- Privacidad Android: ausencia de INTERNET en los permisos del paquete instalado.

Resultados de la validación del MVP: `test` (2 pruebas unitarias), `assembleDebug`, `lint` (0 errores) y `connectedDebugAndroidTest` (5 pruebas Android) completados correctamente en Pixel 7a / Android 17. `git diff --check` sin problemas. Lint conserva avisos de versiones y recursos de la plantilla.

Además se comprobó en el emulador un cierre real mediante `am force-stop` y reapertura: las filas de proyectos, láminas y elementos permanecieron idénticas (incluidos colores, coordenadas y destinos), el archivo de imagen privado conservó sus bytes y la UI reconstruyó el texto y los botones. Los datos temporales de esta comprobación se retiraron y se restauró la base original del emulador. No se ha validado en hardware físico.

APK de desarrollo: `app/build/outputs/apk/debug/app-debug.apk`.

## Alcance

Solo fondos estáticos. Sin GIF/video, redimensionamiento libre, editor de grafos ni scripting. Las imágenes usan recorte centrado para llenar el lienzo. Los botones sin destino aparecen pero no navegan en reproducción. Los textos demasiado extensos se limitan al tamaño del lienzo.
