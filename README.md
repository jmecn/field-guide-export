# Field Guide Export

Forge mod: Patchouli `guide-export/` + scoped EMI via [minecraft-web-export](https://github.com/jmecn/minecraft-web-export) (`mwe_version` in `gradle.properties`).

## Layout

```text
<exportRoot>/
  guide-export/   manifest.json, meta.json, assets/, data/
  emi/          scoped EMI bundle (when exportEmi is on)
```

## Build

```bash
./gradlew jar
```

GitHub Packages: `gpr.user` / `gpr.key` or `GITHUB_TOKEN` for minecraft-web-export.

## Release

`mod_version` in `gradle.properties` should match the tag (e.g. `0.1.0` ↔ `v0.1.0`). CI passes `-Pmod_version` from the tag so the jar is always `field-guide-export-<version>.jar`.

Push tag `v*` → workflow builds jar, publishes to GitHub Packages, attaches asset to GitHub Release. After shipping, bump `mod_version` on `main` for the next cycle (e.g. `0.2.0-SNAPSHOT`).

## Runs

| Gradle run | Purpose |
|------------|---------|
| `runClient` | Dev |
| `runExportClient` | Guide only → `build/guide-export` |
| `runExportCiClient` | CI: `fieldguide.runExportAndExit` → `build/export/` |

In-game: `/fieldguideexport run` (guide + EMI when `fieldguide.exportEmi` is not `false`).

## CI JVM flags (modpack)

```text
-Dfieldguide.runExportAndExit=true
-Dfieldguide.exportRoot=<dir>
-DminecraftWebExport.exportWorldName=guide-export
-DminecraftWebExport.exportMode=scoped
```

Do **not** set `-DminecraftWebExport.runExportAndExit=true` (mwe driver would conflict).

Install **both** `field-guide-export` and `minecraft-web-export` jars from releases.
