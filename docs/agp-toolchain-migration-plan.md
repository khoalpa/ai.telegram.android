# AGP toolchain migration plan

Status date: 2026-06-07

This plan is separate from `docs/gradle-agp-legacy-risk.md` on purpose. The
legacy-risk document describes the currently supported build contract. This file
describes the one-time migration path to remove legacy flags before upgrading
AGP/Kotlin/KSP/Room.

## Rule of engagement

Do not upgrade AGP, Kotlin, KSP or Room until the build works without these
legacy flags:

```properties
android.builtInKotlin=false
android.newDsl=false
```

Do not remove the flags in the same commit as a toolchain version bump. The first
migration change should keep the current tested tuple:

- Gradle wrapper: `9.5.1`
- Android Gradle Plugin: `9.2.1`
- Kotlin Android plugin: `2.2.21`
- Compose compiler plugin: `2.2.21`
- KSP: `2.2.21-2.0.5`
- Room: `2.8.4`

## Phase 0: Prepare

1. Create a dedicated build-tooling branch.
2. Do not mix product/UI/data changes into the migration branch.
3. Capture the current baseline:

```powershell
.\gradlew.bat :app:compileOutsidePlayDebugKotlin --console=plain
.\gradlew.bat :app:testOutsidePlayDebugUnitTest :app:lintOutsidePlayDebug :app:assembleOutsidePlayDebug --console=plain
```

4. Keep `docs/gradle-agp-legacy-risk.md` unchanged until the legacy-free build
   passes.

## Phase 1: Remove legacy flags without upgrading versions

1. Remove `id("org.jetbrains.kotlin.android")` from `app/build.gradle.kts`.
2. Keep `id("org.jetbrains.kotlin.plugin.compose")`.
3. Remove `android.builtInKotlin=false` from `gradle.properties`.
4. Remove `android.newDsl=false` from `gradle.properties`.
5. Run:

```powershell
.\gradlew.bat :verifyAgpLegacyCompatibility --console=plain
.\gradlew.bat :app:compileOutsidePlayDebugKotlin --console=plain
.\gradlew.bat :app:compileOutsidePlayDebugAndroidTestKotlin --console=plain
.\gradlew.bat :app:testOutsidePlayDebugUnitTest :app:lintOutsidePlayDebug :app:assembleOutsidePlayDebug --console=plain
```

6. Verify Room/KSP output still works:

```powershell
.\gradlew.bat :app:kspOutsidePlayDebugKotlin --console=plain
```

7. Verify ABI split APKs are still produced for `arm64-v8a`, `armeabi-v7a` and
   `x86_64`.

If this phase fails, revert the branch and update `docs/gradle-agp-legacy-risk.md`
with the exact failing command and failure reason. Do not proceed to Phase 2.

## Phase 2: Mark the build legacy-free

Only after Phase 1 passes:

1. Update `docs/gradle-agp-legacy-risk.md` to say the legacy flags have been
   removed and archive the old risk as historical context.
2. Rename or replace `verifyAgpLegacyCompatibility` with a legacy-free guard
   that fails if either deprecated flag reappears.
3. Run the Phase 1 verification commands again after the guard update.

## Phase 3: Upgrade toolchain versions

After a committed legacy-free baseline exists, update toolchain versions in a
separate change:

1. Choose a single tested tuple for AGP, Kotlin, KSP and Room.
2. Keep KSP aligned with the Kotlin version prefix.
3. Run:

```powershell
.\gradlew.bat :verifyAgpLegacyCompatibility --console=plain
.\gradlew.bat :app:compileOutsidePlayDebugKotlin --console=plain
.\gradlew.bat :app:compileOutsidePlayDebugAndroidTestKotlin --console=plain
.\gradlew.bat :app:testOutsidePlayDebugUnitTest :app:lintOutsidePlayDebug :app:assembleOutsidePlayDebug --console=plain
.\gradlew.bat :app:assembleOutsidePlayRelease --console=plain
```

4. Record the tested tuple and build output sizes in the release sign-off or the
   migration PR.

Use `-PallowUntestedAgpLegacy=true` only for local probes while finding a working
tuple. Do not rely on that property in a committed migration.

## Done criteria

- `gradle.properties` has no `android.builtInKotlin=false`.
- `gradle.properties` has no `android.newDsl=false`.
- `app/build.gradle.kts` no longer applies `org.jetbrains.kotlin.android`.
- No obsolete variant API warnings during configuration.
- Room schema generation still succeeds.
- Unit tests, lint, debug assemble, androidTest compile and release assemble pass.
- ABI split APKs are still produced for `arm64-v8a`, `armeabi-v7a` and `x86_64`.
