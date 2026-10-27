package com.igorwojda.showcase.buildlogic.ktlint

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.api.LintError
import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import java.util.ServiceLoader
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText

/**
 * Runs ktlint over every Kotlin source and script in the module, in all source folders (`src`, `src@android`, `test`,
 * ...). Each file picks up the `.editorconfig` files above it, like the ktlint CLI.
 */
@TaskAction
fun ktlint(
    @Input moduleDir: Path,
    // A string, so the whole project isn't a task input. Reported paths are relative to it.
    basePath: String,
    format: Boolean,
) {
    // Standard rules plus the Compose rules, both found on the plugin classpath
    val ruleProviders =
        ServiceLoader
            .load(RuleSetProviderV3::class.java, RuleSetProviderV3::class.java.classLoader)
            .flatMap { it.getRuleProviders() }
            .toSet()
    val engine = KtLintRuleEngine(ruleProviders = ruleProviders)

    val violations =
        kotlinFiles(moduleDir).flatMap { file ->
            val path = file.relativeTo(Path(basePath))
            val errors = if (format) engine.formatFile(file) else engine.lintFile(file)
            errors.map { "$path:${it.line}:${it.col}: ${it.detail} (${it.ruleId.value})" }
        }

    violations.forEach(::println)
    if (violations.isNotEmpty()) {
        error("ktlint found ${violations.size} violation(s)" + if (format) " that can't be fixed automatically" else "")
    }
}

private fun kotlinFiles(moduleDir: Path): List<Path> =
    moduleDir
        .walk()
        .filter { it.isRegularFile() && it.extension in setOf("kt", "kts") }
        .filterNot { file -> file.relativeTo(moduleDir).any { it.toString() == "build" } }
        .sorted()
        .toList()

private fun KtLintRuleEngine.lintFile(file: Path): List<LintError> =
    buildList {
        lint(Code.fromFile(file.toFile())) { add(it) }
    }

/** Fixes [file] in place and returns the violations that can't be fixed automatically. */
private fun KtLintRuleEngine.formatFile(file: Path): List<LintError> {
    val errors = mutableListOf<LintError>()
    val code = file.readText()
    val formatted =
        format(Code.fromFile(file.toFile())) { error ->
            if (!error.canBeAutoCorrected) errors += error
            AutocorrectDecision.ALLOW_AUTOCORRECT
        }
    if (formatted != code) file.writeText(formatted)
    return errors
}
