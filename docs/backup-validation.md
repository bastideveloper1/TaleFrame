# Validación de TaleFrame 5.2

Validado en el emulador Pixel 7a / Android 17, con SQLite v6 y formato `.taleframe` v1.

- **15 pruebas unitarias y 67 pruebas Android distintas aprobadas**. La corrida completa final terminó sin fallos. Una corrida anterior tuvo un timeout de la prueba existente de transiciones; pasó al repetir la suite, sin cambiar editor ni reproducción.
- Tras reforzar los límites del parser se repitieron las **11 pruebas de `ProjectBackupTest` y las 2 de `BackupFlowTest`**, todas aprobadas sobre el código final.
- `test`, `assembleDebug`, `lint` y `git diff --check`: correctos. Lint: **0 errores, 20 advertencias existentes**.
- Manifest/APK: sin `INTERNET`, backup Android deshabilitado y sin nuevas dependencias.

## Integridad

El round-trip compara modelos normalizados por relaciones y SHA-256, sin exigir IDs locales iguales. Incluye portada, cinco láminas con orden personalizado, inicio/borrador, personajes y expresiones ordenadas, diálogo/Narrador/acciones, presets, navegación base, ramas **A → C, A → D, C → E**, autoavance, transiciones, PNG con alfa, GIF, video, audio, slideshow, recursos sin colocar y dos plantillas personalizadas. Una plantilla y un elemento conservan una imagen histórica después de reemplazar su referencia de catálogo.

Se verifica edición posterior, cambio de expresión y aplicación de una plantilla restaurada. Modificar un preset no altera el estilo de una instancia existente. También se preservan personajes antiguos con nombres duplicados y catálogos con dos entradas de bytes idénticos.

Treinta instancias de una imagen producen **un solo blob** en el archivo. La importación en un repositorio con los mismos bytes reutiliza su archivo privado; borrar el proyecto original conserva el archivo utilizado por la copia.

Un trigger de SQLite provoca un fallo a mitad de la escritura: rollback de todas las colecciones, limpieza de archivos nuevos/staging y conservación de los archivos compartidos. Un marcador de importación interrumpida dispara la recuperación al reiniciar, sin borrar multimedia referenciada.

Se rechazan **19 variantes inválidas/maliciosas**: contenido no ZIP, ZIP truncado, manifest ausente, JSON inválido, recurso ausente, bytes cambiados conservando su tamaño, versión futura, traversal, ruta absoluta, backslashes, archivo desconocido, formato incorrecto, contenido posterior al JSON, referencia inexistente con hash válido, nombres ZIP duplicados, compression bomb, exceso de entradas, profundidad excesiva y amplitud excesiva del JSON. No dejan filas, recursos nuevos ni cache de validación.

## Instalación limpia y SAF real

La prueba final utilizó una copia de desarrollo con application ID temporal **`com.r0ybt.taleframe.backuprestore52`**, instalada vacía en un UID separado. No se modificó la configuración del repositorio para crearla: el sufijo se aplicó mediante un init script temporal de Gradle. La revisión automática rechazó borrar los datos de la instalación original; la alternativa aislada permitió completar la prueba sin hacerlo.

1. Exportar el proyecto completo y conservar el `.taleframe` en Downloads, fuera de datos privados.
2. Abrir la instalación vacía y elegir el archivo mediante **Importar → selector Android → Downloads**.
3. Comparar las **ocho colecciones lógicas** y verificar SHA-256 de sus **ocho archivos multimedia**, incluida la imagen histórica.
4. Reproducir el video y la navegación restaurados.
5. Crear y guardar un texto desde el editor de la copia restaurada.
6. Exportar nuevamente mediante **CreateDocument** y abrir el **Android Sharesheet**.
7. Cerrar forzosamente, reabrir y comprobar que sigue la edición guardada.

Todos los pasos pasaron. Se verificó que bases, archivos y preferencias de la instalación original permanecieran idénticos durante esta prueba. La copia aislada se retiró al terminar. La APK final vuelve a usar el application ID normal.

Las pruebas conectadas deben ejecutarse en un dispositivo/emulador de desarrollo: el motor de tests instalado desinstala los APK de prueba y de la aplicación al finalizar. La prueba manual aislada no utiliza esa desinstalación sobre la instalación original.
