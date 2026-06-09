import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.5" apply false
}

abstract class VerifyAgpLegacyCompatibilityTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rootBuildFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appBuildFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val gradlePropertiesFile: RegularFileProperty

    @get:Input
    abstract val testedAgpVersion: Property<String>

    @get:Input
    abstract val testedKotlinVersion: Property<String>

    @get:Input
    abstract val testedKspVersion: Property<String>

    @get:Input
    abstract val builtInKotlin: Property<String>

    @get:Input
    abstract val newDsl: Property<String>

    @get:Input
    abstract val allowUntestedAgpLegacy: Property<Boolean>

    @TaskAction
    fun verify() {
        val rootBuildText = rootBuildFile.get().asFile.readText()
        val appBuildText = appBuildFile.get().asFile.readText()

        fun pluginVersion(id: String): String {
            val pattern = Regex("""id\("${Regex.escape(id)}"\)\s+version\s+"([^"]+)"""")
            return pattern.find(rootBuildText)?.groupValues?.get(1)
                ?: throw GradleException("Could not find plugin version for '$id' in root build.gradle.kts.")
        }

        val agpVersion = pluginVersion("com.android.application")
        val kotlinVersion = pluginVersion("org.jetbrains.kotlin.android")
        val kspVersion = pluginVersion("com.google.devtools.ksp")
        val usesExternalKotlinAndroidPlugin = appBuildText.contains("""id("org.jetbrains.kotlin.android")""")
        val agpMajor = agpVersion.substringBefore('.').toIntOrNull()
            ?: throw GradleException("Could not parse AGP version '$agpVersion'.")

        if (usesExternalKotlinAndroidPlugin && builtInKotlin.get() != "false") {
            throw GradleException(
                "AGP built-in Kotlin is enabled while app/build.gradle.kts still applies " +
                    "org.jetbrains.kotlin.android. Either keep android.builtInKotlin=false " +
                    "or complete the built-in Kotlin migration and remove the external Kotlin Android plugin."
            )
        }
        if (usesExternalKotlinAndroidPlugin && newDsl.get() != "false") {
            throw GradleException(
                "android.newDsl must remain false while org.jetbrains.kotlin.android is applied on AGP 9.x. " +
                    "The external Kotlin Android plugin still calls the legacy variant API."
            )
        }
        if (agpMajor >= 10 && (builtInKotlin.get() == "false" || newDsl.get() == "false")) {
            throw GradleException(
                "AGP $agpVersion no longer supports android.builtInKotlin=false/android.newDsl=false. " +
                    "Complete the built-in Kotlin + AndroidComponents migration before moving to AGP 10+."
            )
        }
        if (!kspVersion.startsWith("${kotlinVersion}-")) {
            throw GradleException(
                "KSP $kspVersion is not aligned with Kotlin $kotlinVersion. " +
                    "Use a KSP artifact whose prefix matches the Kotlin plugin version."
            )
        }
        if (
            !allowUntestedAgpLegacy.get() &&
            (
                agpVersion != testedAgpVersion.get() ||
                    kotlinVersion != testedKotlinVersion.get() ||
                    kspVersion != testedKspVersion.get()
                )
        ) {
            throw GradleException(
                "Untested AGP/Kotlin/KSP combination: AGP $agpVersion, Kotlin $kotlinVersion, KSP $kspVersion. " +
                    "The tested legacy combination is AGP ${testedAgpVersion.get()}, " +
                    "Kotlin ${testedKotlinVersion.get()}, KSP ${testedKspVersion.get()}. " +
                    "Follow docs/agp-toolchain-migration-plan.md, then pass -PallowUntestedAgpLegacy=true only for an explicit local probe."
            )
        }
    }
}

val verifyAgpLegacyCompatibility by tasks.registering(VerifyAgpLegacyCompatibilityTask::class) {
    group = "verification"
    description = "Fails fast when AGP/Kotlin legacy compatibility flags drift from the tested configuration."

    rootBuildFile.set(layout.projectDirectory.file("build.gradle.kts"))
    appBuildFile.set(layout.projectDirectory.file("app/build.gradle.kts"))
    gradlePropertiesFile.set(layout.projectDirectory.file("gradle.properties"))
    testedAgpVersion.set("9.2.1")
    testedKotlinVersion.set("2.2.21")
    testedKspVersion.set("2.2.21-2.0.5")
    builtInKotlin.set(providers.gradleProperty("android.builtInKotlin").orElse("true"))
    newDsl.set(providers.gradleProperty("android.newDsl").orElse("true"))
    allowUntestedAgpLegacy.set(
        providers.gradleProperty("allowUntestedAgpLegacy")
            .map { it == "true" }
            .orElse(false)
    )
}

subprojects {
    plugins.withId("com.android.application") {
        tasks.matching { it.name == "preBuild" }.configureEach {
            dependsOn(verifyAgpLegacyCompatibility)
        }
    }
}
