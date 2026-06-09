# Gradle/AGP legacy compatibility

Status date: 2026-06-07

## Current pinned toolchain

- Gradle wrapper: `9.5.1`
- Android Gradle Plugin: `9.2.1`
- Kotlin Android plugin: `2.2.21`
- Compose compiler plugin: `2.2.21`
- KSP: `2.2.21-2.0.5`

The project still requires these `gradle.properties` flags while it applies the external Kotlin Android plugin:

```properties
android.builtInKotlin=false
android.newDsl=false
```

`./gradlew :app:compileOutsidePlayDebugKotlin -Pandroid.debug.obsoleteApi=true` reports the obsolete `applicationVariants`, `testVariants`, and `unitTestVariants` callers as the `kotlin-android` plugin. That means `android.newDsl=false` is tied to the external Kotlin plugin and should not be removed independently.

## Guard rail

Root `build.gradle.kts` registers `verifyAgpLegacyCompatibility` and wires it into Android `preBuild`.

The guard fails fast when:

- `org.jetbrains.kotlin.android` is still applied but `android.builtInKotlin` is not `false`.
- `org.jetbrains.kotlin.android` is still applied but `android.newDsl` is not `false`.
- AGP is moved to `10+` while either legacy flag remains.
- KSP no longer matches the Kotlin plugin version prefix.
- AGP/Kotlin/KSP versions drift from the tested legacy tuple without an explicit probe.

For an intentional compatibility probe only, run with:

```powershell
.\gradlew.bat :app:compileOutsidePlayDebugKotlin --console=plain "-PallowUntestedAgpLegacy=true"
```

Do not commit a version drift while this file still describes the active build state.
The migration plan is intentionally tracked separately in
`docs/agp-toolchain-migration-plan.md` so the current compatibility contract does
not drift during toolchain upgrade work.

## Probe results

These probes were run against the current codebase:

- `-Pandroid.newDsl=true`: fails while applying `org.jetbrains.kotlin.android` because the external Kotlin plugin expects the legacy `BaseExtension`.
- `-Pandroid.builtInKotlin=true`: fails because AGP built-in Kotlin support makes `org.jetbrains.kotlin.android` redundant/incompatible.

## Upgrade boundary

This file is the source of truth for the current pinned legacy tuple only. Before
any AGP/Kotlin/KSP/Room version upgrade lands, complete the legacy-flag removal
phase in `docs/agp-toolchain-migration-plan.md`.

The key rule is: do not combine a toolchain version bump with removal of
`android.builtInKotlin=false` or `android.newDsl=false` in the same change.
