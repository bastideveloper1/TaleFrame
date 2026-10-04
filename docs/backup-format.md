# TaleFrame project format v1

A `.taleframe` file is a ZIP containing exactly `manifest.json`, `project.json` and zero or more `resources/<sha256>` entries. It is a logical, single-project export; it contains no SQLite database, device identifiers or preferences from other projects. Version 1 is independent of SQLite v6. Future storage migrations must adapt this explicit record contract rather than silently change it.

## Manifest

- `format`: `TaleFrame.project`.
- `formatVersion`: integer `1`. Future versions are rejected with a specific Spanish message.
- `appVersion`: application version name.
- `exportedAt`: UTC timestamp.
- `projectName`: same name as the project record.
- `projectSha256`: lowercase SHA-256 of the exact UTF-8 bytes in `project.json`.
- `resources`: array of `{entry, sha256, size}`. Size is the uncompressed byte count; the entry suffix and SHA-256 must match.

## Logical records

`project.json` is an object with eight arrays: `projects` (exactly one), `slides`, `elements`, `resources`, `characters`, `expressions`, `presets`, `templates`. `ProjectBackup.columns` defines the version-1 field contract. Nullable SQL values become JSON null; integer flags remain 0/1. `settings` and template `body` are JSON objects, including embedded template settings; they are not SQL statements or escaped SQL dumps.

Record IDs are opaque positive identifiers scoped to each collection in this package. Every reference is validated against its collection before any durable writes. Import creates new local IDs and remaps ownership, initial slide, cover, narrative destinations, base navigation, autoadvance, character/expression/preset associations, expression sequences and template metadata. Source IDs are never installed as local primary keys. Relative creation order is preserved, including the existing implicit first-slide rule when no initial slide is configured.

Media paths (`path`, `image`, `audio`, `media.frames`) become `resources/<sha256>` tokens. Text and names are not interpreted as paths. All project catalog resources travel, including unused entries. Historical media retained by template snapshots or independent elements also travels. Each distinct byte sequence is stored once. Deleted origin definitions in historical snapshots are cleared, matching the existing template application behavior; their image/style snapshots remain intact.

Global theme, grid, toolbar order, album size, last visited slide and calco preferences are device/editor settings and are not exported as project preferences. Factory templates are recreated by TaleFrame, while every custom template is exported.

## Validation and import

The input archive is copied into a private, uniquely named cache directory. A bounded ZIP directory whitelist rejects directories, unknown entries, absolute paths, traversal, backslashes, duplicate names, missing declarations and extra files. Extraction checks canonical paths. The importer validates hashes, sizes, record types/IDs, relationships, media headers/metadata, cover/portrait/expression types, character dialogue presets and the single-Narrator constraint before writing project rows or durable media.

Limits: 10,000 ZIP entries; 2 GiB total uncompressed data; 500 MiB per member; 16 MiB for each JSON document; 100,000 logical records; 10,000 elements per template; JSON nesting depth 32, at most 100,000 containers and 500,000 separators per document (checked before parsing to bound allocations). A member of at least 16 MiB with compression ratio above 1000 is rejected. Existing media limits also apply: images/GIF 40 MiB, audio 100 MiB, video 500 MiB, GIF at most 8 megapixels and 4096 pixels per side. The ZIP writer uses deflate level zero, since media is already compressed.

Validated bytes are copied to private storage, reusing existing hash files only after checking their bytes. Damaged shared files are never overwritten. Legacy catalog entries with different names but identical bytes retain separate IDs; private hard-link aliases satisfy SQLite's path/type uniqueness, with a copy fallback if hard links are unavailable.

All eight collections are inserted and their references remapped in one SQLite transaction. No normal creation API seeds another Narrator, resynchronizes base buttons, renumbers layers or rejects legacy duplicate character names. On a handled failure, SQLite rolls back and only newly created files are deleted; reused files stay intact. Cache is removed on success, error or cancellation of the copy-name dialog. A durable private marker precedes new media copies. On restart after an interrupted import, the existing global reference-based cleaner removes uncommitted orphan media and markers while preserving committed/restored/shared/catalog/template files. Stale validation cache is also removed when the ViewModel next starts.

## Android flow and limitations

`Exportar proyecto` opens `CreateDocument` with a sanitized `<name>.taleframe` suggestion. `Importar` opens `OpenDocument` from the main screen. No broad storage permission or Internet permission is required. Export success offers the standard Android Sharesheet with a temporary read grant for the selected document URI. This does not connect TaleFrame to an external service.

An import always creates a new project. On a name collision, `Importar como copia` / `Cancelar` proposes the next free `(2)`, `(3)`, etc. IO runs in the existing serialized background writer; status shows preparation/validation/copy/import phases and resource counts. Accepted operations finish atomically; long-running operations do not expose a cancel button. Cancelling a system picker does nothing; cancelling the duplicate-name dialog discards all staging data.

Version 1 has no merge, multi-project archive or global file association. Importing through the main screen avoids relying on inconsistent MIME types reported for `.taleframe` by document providers. A future `Exportar todos los proyectos` can reuse the per-project codec, but needs its own container/index and an explicit multi-project collision/rollback policy.

SQLite and filesystem copies are not a single OS transaction. Handled errors and ordinary cancelled dialogs perform immediate cleanup; a killed process rolls back unfinished SQLite writes, and the durable marker triggers reference-based media recovery on next startup. Completed commits remain intact even if the process dies before deleting the marker. Cache/disk space must accommodate both the input archive and validated staged resources while importing. A provider that cannot delete a failed export may leave a partial external document; TaleFrame attempts its deletion and reports failure. Hashes detect corruption, not authorship; the format is neither signed nor encrypted.
