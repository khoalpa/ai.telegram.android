# Build modernization checklist

The project currently builds on AGP 9.2.1 with Kotlin/KSP 2.2.21, but it relies on compatibility flags that AGP reports as deprecated.

Current-state risk tracking lives in `docs/gradle-agp-legacy-risk.md`.
The migration sequence lives in `docs/agp-toolchain-migration-plan.md`.

Treat those as separate documents:

- Current build contract: why the legacy flags are still present and how the guard rail protects them.
- Migration plan: how to remove the legacy flags before any toolchain version bump.

## Current risk

`gradle.properties` keeps these flags enabled:

```properties
android.builtInKotlin=false
android.newDsl=false
```

They are required for the current KSP/Room configuration, but AGP warns that they will be removed in version 10.0. The build also emits obsolete variant API warnings from the Kotlin/KSP path.

## Migration steps

Follow `docs/agp-toolchain-migration-plan.md`.

The short version is:

1. Create a branch that only changes build tooling.
2. Remove the external Kotlin Android plugin and both legacy flags while keeping the current AGP/Kotlin/KSP/Room versions.
3. Run:

```powershell
.\gradlew.bat :verifyAgpLegacyCompatibility --console=plain
.\gradlew.bat :app:compileOutsidePlayDebugKotlin --console=plain
.\gradlew.bat :app:compileOutsidePlayDebugAndroidTestKotlin --console=plain
.\gradlew.bat :app:testOutsidePlayDebugUnitTest :app:lintOutsidePlayDebug :app:assembleOutsidePlayDebug --console=plain
```

4. Only after that legacy-free baseline passes, upgrade AGP/Kotlin/KSP/Room in a separate change.
5. Run:

```powershell
.\gradlew.bat :app:assembleOutsidePlayRelease --console=plain
```

If KSP or Room still needs a legacy path, keep the current working toolchain and record the blocker in `docs/gradle-agp-legacy-risk.md`.

## Done criteria

- No deprecated `android.builtInKotlin` or `android.newDsl` flags.
- No obsolete variant API warnings during configuration.
- Room schema generation still writes to `app/schemas`.
- Unit tests, lint, debug assemble and release assemble pass.
- ABI split APKs are still produced for `arm64-v8a`, `armeabi-v7a` and `x86_64`.
