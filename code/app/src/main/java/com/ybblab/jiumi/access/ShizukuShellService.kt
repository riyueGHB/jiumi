// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.access

import android.os.Process
import androidx.annotation.Keep
import com.ybblab.jiumi.access.IShizukuShellService.ERROR_EXECUTE
import com.ybblab.jiumi.access.IShizukuShellService.ERROR_IDENTITY
import com.ybblab.jiumi.access.IShizukuShellService.ERROR_TIMEOUT
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Started by Shizuku in a process of its own, as the shell or, when Shizuku was started with root, as
 * root. It only runs the few commands that start the monitor (which then lives on by itself) or change
 * a package; this process is let go right after.
 */
@Keep
class ShizukuShellService : IShizukuShellService.Stub() {
    override fun getUid(): Int = Process.myUid()

    override fun execute(command: String, timeoutMs: Long): ShizukuCommandResult {
        if (Process.myUid() != SHELL_UID && Process.myUid() != ROOT_UID) {
            return failure(ERROR_IDENTITY)
        }
        val process = try {
            ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        } catch (_: IOException) {
            return failure(ERROR_EXECUTE)
        } catch (_: SecurityException) {
            return failure(ERROR_EXECUTE)
        }
        val text = StringBuffer()
        val pump = Thread({
            runCatching {
                process.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(4096)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        text.append(buffer, 0, count.coerceAtMost(MAX_OUTPUT_CHARS - text.length))
                    }
                }
            }
        }, "shizuku-output").apply { isDaemon = true }
        return try {
            process.outputStream.close()
            pump.start()
            if (!process.waitFor(timeoutMs.coerceIn(1, MAX_TIMEOUT_MS), TimeUnit.MILLISECONDS)) {
                return failure(ERROR_TIMEOUT)
            }
            pump.join(2_000)
            ShizukuCommandResult().apply {
                exitCode = process.exitValue()
                output = text.toString().trimEnd()
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            failure(ERROR_EXECUTE)
        } catch (_: IOException) {
            failure(ERROR_EXECUTE)
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun failure(code: Int) = ShizukuCommandResult().apply { status = code }

    override fun destroy() = exitProcess(0)

    private companion object {
        const val SHELL_UID = 2000
        const val ROOT_UID = 0
        const val MAX_OUTPUT_CHARS = 128_000
        const val MAX_TIMEOUT_MS = 120_000L
    }
}
