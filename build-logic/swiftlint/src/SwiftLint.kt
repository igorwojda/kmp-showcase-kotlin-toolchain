package com.igorwojda.showcase.buildlogic.swiftlint

import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.net.URI
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.ZipInputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText

private const val EXECUTABLE = "swiftlint"

/**
 * Downloads the SwiftLint release set by [versionAlias] in the `[versions]` table of [versionCatalog] and verifies its
 * checksum. The keys of [sha256] are `macos`, `linuxAmd64` and `linuxArm64`. Linux uses the statically linked binary,
 * which runs without a Swift installation.
 */
@TaskAction
@OptIn(ExperimentalPathApi::class)
fun installSwiftLint(
    @Input versionCatalog: Path,
    versionAlias: String,
    sha256: Map<String, String>,
    @Output installDir: Path,
) {
    val version = catalogVersion(versionCatalog, versionAlias)
    // The task reruns on any catalog change; skip the download when the SwiftLint version is the same
    val versionFile = installDir.resolve("version")
    if (versionFile.exists() && versionFile.readText() == version) return

    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch") in setOf("aarch64", "arm64")
    val (platform, asset, binary) =
        when {
            "mac" in os -> Triple("macos", "portable_swiftlint.zip", "swiftlint")
            "linux" in os && arm -> Triple("linuxArm64", "swiftlint_linux_arm64.zip", "swiftlint-static")
            "linux" in os -> Triple("linuxAmd64", "swiftlint_linux_amd64.zip", "swiftlint-static")
            else -> error("SwiftLint is not available for ${System.getProperty("os.name")}")
        }
    val expectedSha256 = sha256[platform] ?: error("Missing SwiftLint checksum for $platform")

    val url = "https://github.com/realm/SwiftLint/releases/download/$version/$asset"
    println("Downloading $url")
    val zip = URI(url).toURL().openStream().use { it.readBytes() }

    val actualSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip))
    check(actualSha256 == expectedSha256) { "Checksum mismatch for $url: expected $expectedSha256, got $actualSha256" }

    installDir.deleteRecursively()
    installDir.createDirectories()
    val executable = installDir.resolve(EXECUTABLE)
    ZipInputStream(zip.inputStream()).use { entries ->
        generateSequence { entries.nextEntry }
            .firstOrNull { it.name == binary }
            ?: error("$binary not found in $url")
        executable.outputStream().use { entries.copyTo(it) }
    }
    executable.setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
    versionFile.writeText(version)
}

/** Reads `alias = "version"` from the `[versions]` table of a Gradle version catalog. */
private fun catalogVersion(
    versionCatalog: Path,
    alias: String,
): String {
    val versions =
        versionCatalog
            .readLines()
            .map { it.substringBefore('#').trim() }
            .dropWhile { it != "[versions]" }
            .drop(1)
            .takeWhile { !it.startsWith("[") }
    val entry = Regex("""^${Regex.escape(alias)}\s*=\s*"([^"]+)"$""")
    return versions.firstNotNullOfOrNull { entry.find(it)?.groupValues?.get(1) }
        ?: error("Version `$alias` not found in the [versions] table of $versionCatalog")
}

/** Runs SwiftLint from [installDir] over [sourceDir]. Warnings fail the task too (`--strict`). */
@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun swiftlint(
    @Input installDir: Path,
    @Input sourceDir: Path,
    @Input config: Path,
    fix: Boolean,
) {
    val command =
        buildList {
            add(installDir.resolve(EXECUTABLE).pathString)
            add(if (fix) "--fix" else "lint")
            add("--strict")
            // Only violations, no per-file progress
            add("--quiet")
            add("--config")
            add(config.pathString)
            add(sourceDir.pathString)
        }

    val exitCode =
        ProcessBuilder(command)
            .inheritIO()
            .start()
            .waitFor()
    check(exitCode == 0) { "SwiftLint failed with exit code $exitCode" }
}
