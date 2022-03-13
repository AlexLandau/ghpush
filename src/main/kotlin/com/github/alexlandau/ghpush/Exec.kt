package com.github.alexlandau.ghpush

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch

internal fun exec(command: List<String>, dir: File): ExecResult {
    val process = ProcessBuilder(command)
        .directory(dir)
        .start()

    val cdLatch = CountDownLatch(2)

    val outSink = ByteArrayOutputStream()
    val outCopier = Thread {
        while (true) {
            process.inputStream.copyTo(outSink)
            if (!process.isAlive) {
                cdLatch.countDown()
                break
            }
        }
    }
    outCopier.start()
    val errSink = ByteArrayOutputStream()
    val errCopier = Thread {
        while (true) {
            process.errorStream.copyTo(errSink)
            if (!process.isAlive) {
                cdLatch.countDown()
                break
            }
        }
    }
    errCopier.start()

    process.waitFor()

    cdLatch.await()

    return ExecResult(
        exitValue = process.exitValue(),
        stdOut = outSink.toString(StandardCharsets.UTF_8),
        stdErr = outSink.toString(StandardCharsets.UTF_8)
    )
}

internal data class ExecResult(
    val exitValue: Int,
    val stdOut: String,
    val stdErr: String,
)

/**
 * Runs the given command and returns its output.
 */
internal fun getCommandOutput(command: List<String>, dir: File): String {
    val result = exec(command, dir)
    if (result.exitValue != 0) {
        val outputToShow = if (result.stdOut.isEmpty()) {
            result.stdErr
        } else {
            if (result.stdErr.isEmpty()) {
                result.stdOut
            } else {
                "Standard output:\n${result.stdOut}\nStandard error:\n${result.stdErr}"
            }
        }
        throw GhssException("The command '${command.toCommandString()}' failed. Its output was:\n${outputToShow}")
    }
    return result.stdOut
}

internal fun List<String>.toCommandString(): String {
    return this.map { if (it.contains(" ")) "\"$it\"" else it }.joinToString(" ")
}