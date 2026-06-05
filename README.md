# Field Guide Export

Forge mod: Patchouli `guide-export/` + scoped EMI via [minecraft-web-export](https://github.com/jmecn/minecraft-web-export) (`mwe_version` in `gradle.properties`).

## Layout

```text
<exportRoot>/
  guide-export/   manifest.json, meta.json, assets/, data/, lang/, assets/icons/ (planned)
  emi/            scoped EMI bundle schema 2 (minecraft-web-export)
```

## Build

```bash
./gradlew jar
```

Requires minecraft-web-export **0.3.3+** on GitHub Packages (`gpr.user` / `GITHUB_TOKEN`).

## Runs

| Gradle run | Purpose |
|------------|---------|
| `runClient` | Dev |
| `runExportClient` | Guide only → `build/guide-export` |
| `runExportCiClient` | mwe CI driver → `build/export/` (guide + EMI) |

In-game: `/fieldguideexport run`. Guide-only: `-Dfieldguide.exportEmi=false`.

## CI JVM (modpack)

Install **field-guide-export** + **minecraft-web-export** jars. Headless export:

```text
-DminecraftWebExport.runExportAndExit=true
-DminecraftWebExport.export.outputDir=<exportRoot>
-DminecraftWebExport.exportMode=scoped
```
