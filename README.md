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

Push tag `v*` → workflow builds jar, publishes to GitHub Packages, attaches asset to GitHub Release.

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
