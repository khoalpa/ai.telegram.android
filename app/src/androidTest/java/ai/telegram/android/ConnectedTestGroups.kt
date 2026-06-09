package ai.telegram.android

/**
 * Instrumented tests that are safe to run on a logged-in real-device install.
 *
 * These tests avoid Compose input injection and Telegram session state. The
 * connected-safe script uses an explicit class allowlist for Redmi/HyperOS, and
 * this annotation documents the intended group in code.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DeviceSafeConnectedTest

/**
 * Compose UI tests that should run on AOSP emulators or non-Xiaomi devices.
 *
 * Redmi/HyperOS has repeatedly timed out and crashed instrumentation on this
 * group, so the connected-safe script keeps these tests off restricted devices.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ComposeUiConnectedTest
