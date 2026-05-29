# Field Guide Export

Forge mod that exports the TFG Patchouli field guide to `guide-export/`. Uses [minecraft-web-export](https://github.com/jmecn/minecraft-web-export) for EMI later (`mwe_version` in `gradle.properties`).

## Build

```bash
./gradlew jar
```

Gradle may need `gpr.user` / `gpr.key` (or `GITHUB_TOKEN`) to reach GitHub Packages for minecraft-web-export.

## Runs

| Task | Notes |
|------|-------|
| `runClient` | Dev client |
| `runExportClient` | Sets `fieldguide.exportFolder=build/guide-export` |

Implementation is being moved over from `Field-Guide-Modern/forge`.
