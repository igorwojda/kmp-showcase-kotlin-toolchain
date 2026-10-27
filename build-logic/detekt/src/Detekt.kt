package com.igorwojda.showcase.buildlogic.detekt

import io.gitlab.arturbosch.detekt.cli.CliRunner
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

/**
 * Runs Detekt over every Kotlin source in the module, in all source folders (`src`, `src@android`, `test`, ...).
 * The module root is scanned instead of `module.kotlinJavaSources`, which is empty for Android and KMP modules.
 */
@TaskAction
fun detekt(
    @Input moduleDir: Path,
    @Input config: Path,
    @Output reportDir: Path,
    // A string, so the whole project isn't a task input. Report paths are relative to it.
    basePath: String,
    autoCorrect: Boolean,
) {
    reportDir.createDirectories()

    val args =
        buildList {
            add("--input")
            add(moduleDir.pathString)
            add("--excludes")
            add("**/build/**")
            add("--config")
            add(config.pathString)
            // New rules from Detekt upgrades work without touching detekt.yml
            add("--build-upon-default-config")
            add("--base-path")
            add(basePath)
            add("--report")
            add("html:${reportDir.resolve("detekt.html")}")
            add("--report")
            add("xml:${reportDir.resolve("detekt.xml")}")
            add("--parallel")
            if (autoCorrect) add("--auto-correct")
        }

    // CliRunner, unlike detekt's main(), reports the result instead of calling System.exit, which would stop the build
    val error = CliRunner().run(args.toTypedArray(), System.out, System.err).error
    if (error != null) {
        error("${error.message} Report: ${reportDir.resolve("detekt.html")}")
    }
}
