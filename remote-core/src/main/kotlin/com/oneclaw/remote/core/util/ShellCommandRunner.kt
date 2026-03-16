package com.oneclaw.remote.core.util

import java.io.ByteArrayOutputStream

data class ShellCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

data class ShellBinaryResult(
    val exitCode: Int,
    val stdout: ByteArray,
    val stderr: String
)

class ShellCommandRunner {

    fun run(command: String, privileged: Boolean = false): ShellCommandResult {
        val binary = if (privileged) "su" else "sh"
        val process = ProcessBuilder(binary, "-c", command)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        return ShellCommandResult(exitCode = exitCode, stdout = stdout, stderr = stderr)
    }

    fun runBinary(command: String, privileged: Boolean = false): ShellBinaryResult {
        val binary = if (privileged) "su" else "sh"
        val process = ProcessBuilder(binary, "-c", command)
            .redirectErrorStream(false)
            .start()

        val stdoutBuffer = ByteArrayOutputStream()
        process.inputStream.use { input -> input.copyTo(stdoutBuffer) }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        return ShellBinaryResult(
            exitCode = exitCode,
            stdout = stdoutBuffer.toByteArray(),
            stderr = stderr
        )
    }
}
