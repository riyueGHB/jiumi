// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.probe

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal class ProbeService(
    private val context: Context,
    private val packageName: String,
    private val ownerUid: Int,
    private val dataDirectory: File,
    private val spool: EventSpool,
    private val accessWatcher: AccessWatcher,
    private val tracer: PerfettoTracer,
    private val connectionWatcher: ConnectionWatcher,
    private val onDetached: () -> Unit
) : IProbeService.Stub() {
    private val startedAt = SystemClock.elapsedRealtime()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val configFile = File(dataDirectory, "config.json")
    @Volatile private var attachedToken: IBinder? = null
    @Volatile private var watchedOps: Set<String> = emptySet()

    val isAttached: Boolean get() = attachedToken?.isBinderAlive == true

    override fun protocolVersion(): Int = ProbeProtocol.VERSION

    override fun attach(token: IBinder) {
        enforceOwner()
        attachedToken = token
        runCatching {
            token.linkToDeath({
                if (attachedToken === token) {
                    attachedToken = null
                    mainHandler.post(onDetached)
                }
            }, 0)
        }.onFailure {
            attachedToken = null
            mainHandler.post(onDetached)
        }
    }

    @Synchronized
    override fun configure(config: String) {
        enforceOwner()
        if (stopping) return
        val parsed = JSONObject(config)
        val temp = File(configFile.parentFile, ".config.json")
        temp.writeText(parsed.toString())
        java.nio.file.Files.move(temp.toPath(), configFile.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        apply(parsed)
    }

    /** The monitor keeps watching after restarts with the last configuration it was given. */
    fun restoreConfiguration() {
        if (configFile.exists()) apply(JSONObject(configFile.readText()))
    }

    private fun apply(config: JSONObject) {
        spool.exclusions = MonitoringExclusions.parse(config.optJSONArray("exclusions"))
        // 0 keeps everything until the app reads it, short of filling the phone.
        config.optJSONObject("spool")?.let { spool.limitBytes = it.optLong("limitMb", 256) * 1024 * 1024 }
        val ops = config.optJSONArray("ops") ?: JSONArray()
        val list = (0 until ops.length()).map { ops.getString(it) }
        val selectedOps = list.toSet()
        if (watchedOps != selectedOps) {
            watchedOps = selectedOps
            accessWatcher.watch(list)
        }
        val calls = config.optJSONObject("calls")
        val methods = calls?.optJSONArray("methods") ?: JSONArray()
        val network = config.optJSONObject("network")
        connectionWatcher.configure(network?.optBoolean("enabled") == true, network?.optLong("intervalMs") ?: 0L)
        tracer.configure(
            calls = calls?.optBoolean("enabled") == true,
            selected = (0 until methods.length()).map { methods.getString(it) },
            providerOperations = calls?.optJSONObject("providerOperations")?.let { o ->
                o.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to o.getString(k) } }.toMap()
            }.orEmpty(),
            packets = network?.optBoolean("enabled") == true && network.optBoolean("packets")
        )
    }

    override fun readEvents(epoch: Long, afterSeq: Long, max: Int): String {
        enforceOwner()
        return spool.read(epoch, afterSeq, max.coerceIn(1, MAX_EVENTS_PER_READ)).toString()
    }

    private data class DiscreteSnapshot(val from: Long, val rows: List<JSONObject>)
    private val discreteLock = Any()
    private var discreteSnapshot: DiscreteSnapshot? = null

    override fun readDiscreteHistory(fromTime: Long, offset: Int, max: Int): String {
        enforceOwner()
        synchronized(discreteLock) {
            val rows = if (offset == 0) {
                discreteSnapshot = null
                val dump = readAppOpsDump(discrete = true)
                DiscreteHistory.parse(dump, watchedOps, fromTime, spool.exclusions)
                    .also { discreteSnapshot = DiscreteSnapshot(fromTime, it) }
            } else {
                checkNotNull(discreteSnapshot?.takeIf { it.from == fromTime }?.rows) { "Missing discrete AppOps snapshot" }
            }
            val page = DiscreteHistory.page(rows, offset, max.coerceIn(1, MAX_HISTORY_PER_READ))
            if (!page.getBoolean("more")) discreteSnapshot = null
            return page.toString()
        }
    }

    private data class HistorySnapshot(val since: Long, val slots: org.json.JSONArray)
    private var historySnapshot: HistorySnapshot? = null

    override fun readHistoricalOps(sinceTime: Long, skip: Int, max: Int): String {
        enforceOwner()
        synchronized(historyLock) {
            val slots = if (skip == 0) {
                historySnapshot = null
                val dump = readAppOpsDump(discrete = false)
                val discreteDump = if (AppOpsHistoryTable.contains(dump, AppOpsHistoryTable.AGGREGATED))
                    readAppOpsDump(discrete = true) else null
                AppOpsHistory.parse(dump, watchedOps, sinceTime, System.currentTimeMillis(),
                    spool.exclusions, discreteDump, ::isMakerOp)
                    .also { historySnapshot = HistorySnapshot(sinceTime, it) }
            } else {
                checkNotNull(historySnapshot?.takeIf { it.since == sinceTime }?.slots) { "Missing AppOps snapshot" }
            }
            val page = org.json.JSONArray()
            val end = minOf(slots.length(), skip + max.coerceIn(1, MAX_HISTORY_SLOTS))
            for (i in skip until end) page.put(slots.get(i))
            if (end == slots.length() && page.length() < max) historySnapshot = null
            return page.toString()
        }
    }

    private val historyLock = Any()

    private val hasHistoryLimit by lazy {
        val help = Shell.run(listOf("dumpsys", "appops", "-h"))
        check(help.exitCode == 0 && !help.truncated) {
            "AppOps dump options could not be read"
        }
        help.output.lineSequence().any { it.trimStart().startsWith("--history-limit ") }
    }

    private fun readAppOpsDump(discrete: Boolean): String {
        val args = when {
            discrete && hasHistoryLimit -> listOf("--history", "--include-discrete", "0")
            discrete -> listOf("--include-discrete", "0")
            hasHistoryLimit -> listOf("--history", "--history-limit", "0")
            else -> listOf("--history")
        }
        val result = Shell.run(listOf("dumpsys", "appops") + args, timeoutMs = 30_000)
        val found = result.output.lineSequence().any { raw ->
            val line = raw.trim()
            if (discrete) line == DiscreteHistory.SECTION || line == AppOpsHistoryTable.DISCRETE
            else line.startsWith(AppOpsHistory.SECTION) &&
                line.substringAfter(':').trimStart().startsWith("mode=HISTORICAL_MODE_ENABLED")
        }
        check(result.exitCode == 0 && !result.truncated && found && !result.output.contains("DUMP TIMEOUT")) {
            "AppOps history could not be read"
        }
        return result.output
    }

    /**
     * An operation the maker added: the platform does not know it, or numbers it apart from its own
     * (makers that register theirs start far above the platform's hundred-odd).
     */
    private fun isMakerOp(op: String): Boolean {
        val code = runCatching {
            android.app.AppOpsManager::class.java.getMethod("strOpToOp", String::class.java).invoke(null, op) as Int
        }.getOrDefault(-1)
        return code < 0 || code >= FIRST_MAKER_OP
    }

    override fun footprint(): String {
        enforceOwner()
        val status = runCatching { File("/proc/self/status").readLines() }.getOrDefault(emptyList())
        // Proportional memory counts shared framework pages fairly; RSS would count them in full.
        val rollup = runCatching { File("/proc/self/smaps_rollup").readLines() }.getOrDefault(emptyList())
        fun kb(lines: List<String>, key: String) = lines.firstOrNull { it.startsWith("$key:") }
            ?.filter { it.isDigit() }?.toLongOrNull() ?: -1L
        val stat = runCatching { File("/proc/self/stat").readText().substringAfterLast(')').trim().split(' ') }
            .getOrDefault(emptyList())
        // utime and stime are fields 14 and 15 of /proc/<pid>/stat, i.e. 11 and 12 after the name.
        val cpuTicks = (stat.getOrNull(11)?.toLongOrNull() ?: 0L) + (stat.getOrNull(12)?.toLongOrNull() ?: 0L)
        return JSONObject()
            .put("pid", Process.myPid())
            .put("pssKb", kb(rollup, "Pss"))
            .put("privateDirtyKb", kb(rollup, "Private_Dirty"))
            .put("threads", kb(status, "Threads"))
            .put("cpuMs", cpuTicks * 10)
            .put("uptimeMs", SystemClock.elapsedRealtime() - startedAt)
            .put("spoolBytes", spool.sizeBytes())
            .toString()
    }

    private var stopPartial: Boolean? = null
    @Volatile private var stopping = false
    val isRecording get() = !stopping
    var finishEventLog: () -> Unit = {}

    @Synchronized
    override fun finishRecording(): Boolean {
        enforceOwner()
        stopPartial?.let { return it }
        stopping = true
        accessWatcher.finishRecording()
        val traceIncomplete = tracer.finishRecording()
        val connectionsIncomplete = connectionWatcher.finishRecording()
        finishEventLog()
        val partial = traceIncomplete || connectionsIncomplete
        val reason = if (partial) ProbeProtocol.STOP_PARTIAL_TRACE else "requested"
        spool.append(JSONObject().put("kind", "monitor").put("state", "stop").put("reason", reason)
            .put("t", System.currentTimeMillis()))
        stopPartial = partial
        return partial
    }

    override fun shutdown() {
        finishRecording()
        mainHandler.postDelayed({ ProbeMain.exit(0, "shutdown requested") }, 100)
    }

    private val appAccess by lazy { AppAccess(context, dataDirectory, packageName) }

    override fun appAccess(packageName: String, uid: Int, action: String, permission: String, expert: Boolean): String {
        enforceOwner()
        require(PACKAGE_NAME.matches(packageName))
        val user = Binder.getCallingUid() / PER_USER_RANGE
        val token = Binder.clearCallingIdentity()
        return try {
            appAccess.run(packageName, uid, user, action, permission, expert).toString()
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    override fun ownedNetworkRules(): String {
        enforceOwner()
        val user = Binder.getCallingUid() / PER_USER_RANGE
        val token = Binder.clearCallingIdentity()
        return try {
            appAccess.ownedNetworkRules(user).toString()
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    override fun managePackage(action: String, packageName: String): String {
        enforceOwner()
        // The app decides what may be changed; the monitor only refuses what is never a target.
        require(PACKAGE_NAME.matches(packageName)) { "not a package name" }
        require(packageName != this.packageName) { "the monitor does not change its own app" }
        val user = Binder.getCallingUid() / PER_USER_RANGE
        val target = listOf("--user", "$user", packageName)
        return when (action) {
            "disable" -> changeEnabled(listOf("pm", "disable-user") + target, user, packageName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)
            "enable" -> changeEnabled(listOf("pm", "enable") + target, user, packageName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            "unsuspend" -> changePackageState(listOf("pm", "unsuspend") + target, user, packageName) {
                it.installed && it.suspended == false
            }
            "remove" -> changePackageState(listOf("pm", "uninstall", "-k") + target, user, packageName, checkRunning = true) {
                !it.installed
            }
            "restore" -> changePackageState(listOf("cmd", "package", "install-existing") + target, user, packageName) { it.installed }
            // For this user only: the same app in a work profile or a second-space user stays.
            "uninstall" -> runPm(listOf("pm", "uninstall") + target).let { (ok, output) -> manageResult(ok, output) }
            "reinstall" -> reinstall(user, packageName).let { (ok, output) -> manageResult(ok, output) }
            else -> throw IllegalArgumentException("unknown action $action")
        }
    }

    private fun manageResult(
        ok: Boolean, output: String, failure: PackageChangeFailure? = null, running: Boolean? = null, path: String? = null
    ) =
        JSONObject().put("ok", ok).put("output", output.take(MAX_OUTPUT)).apply {
            if (!ok) (failure ?: PackageChangeFailure.fromCommandOutput(output))?.let { put("failure", it.name) }
            if (running != null) put("running", running)
            if (path != null) put("path", path)
        }.toString()

    private fun changeEnabled(command: List<String>, user: Int, pkg: String, target: Int): String {
        val token = Binder.clearCallingIdentity()
        val result = try {
            changeEnabledSetting(target, { readEnabled(pkg, user) }, { runEnabledCommand(command) }) {
                PackageEnabledBinder.write(pkg, user, target)
            }
        } finally {
            Binder.restoreCallingIdentity(token)
        }
        if (result.failure != null || result.path == "pm,binder") ProbeMain.log("Package enable state $target: ${result.attempts}")
        val running = if (result.failure == null && target == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)
            packageProcessRunning(pkg, user) else null
        return manageResult(result.failure == null, result.output, result.failure, running, result.path)
    }

    private fun readEnabled(pkg: String, user: Int): Int? = PackageEnabledBinder.read(pkg, user)
        ?: runCatching { readPackageDump(pkg, user)?.userStates?.get(pkg)?.takeIf { it.installed }?.enabled }.getOrNull()

    private fun runEnabledCommand(command: List<String>): PackageCommand = try {
        val result = Shell.run(command, timeoutMs = 60_000)
        val output = result.output.trim()
        val outcome = when {
            PackageChangeFailure.fromCommandOutput(output) == PackageChangeFailure.PERMISSION_DENIED -> PackageCommandOutcome.DENIED
            result.truncated -> PackageCommandOutcome.UNKNOWN
            else -> PackageCommandOutcome.FINISHED
        }
        PackageCommand(outcome, output)
    } catch (error: Shell.StartFailed) {
        PackageCommand(PackageCommandOutcome.UNAVAILABLE, error.toString())
    } catch (error: Exception) {
        PackageCommand(PackageCommandOutcome.UNKNOWN, error.toString())
    }

    /** Runs a package command; pm prints "Success" or the new state, and a failure exits non-zero or names it. */
    private fun runPm(command: List<String>): Pair<Boolean, String> {
        val result = runCatching { Shell.run(command, timeoutMs = 60_000) }
        val output = result.getOrNull()?.output?.trim() ?: result.exceptionOrNull()?.message.orEmpty()
        val ok = result.getOrNull()?.let {
            it.exitCode == 0 && !it.output.contains("Failure") && !it.output.contains("Error") && !it.output.contains("Exception") &&
                PackageChangeFailure.fromCommandOutput(output) == null
        } == true
        return ok to output
    }

    /** Verify the active package entry for the same user before reporting the requested state. */
    private fun changePackageState(
        command: List<String>, user: Int, pkg: String, checkRunning: Boolean = false,
        matches: (PackageDump.UserState) -> Boolean
    ): String {
        val (ok, output) = runPm(command)
        if (!ok) return manageResult(false, output)
        val state = runCatching { readPackageDump(pkg, user)?.userStates?.get(pkg) }.getOrNull()
            ?: return manageResult(false, output, failure = PackageChangeFailure.UNVERIFIED)
        if (!matches(state)) return manageResult(false, output, failure = PackageChangeFailure.NOT_APPLIED)
        val running = if (checkRunning) packageProcessRunning(pkg, user) else null
        return manageResult(true, output, running = running)
    }

    override fun packageState(packageName: String, checkRunning: Boolean): String {
        enforceOwner()
        require(PACKAGE_NAME.matches(packageName)) { "not a package name" }
        val user = Binder.getCallingUid() / PER_USER_RANGE
        val state = runCatching { readPackageDump(packageName, user)?.userStates?.get(packageName) }.getOrNull()
            ?: return "{}"
        return JSONObject().put("installed", state.installed).put("enabled", state.enabled)
            .put("suspended", state.suspended == true).apply {
                if (checkRunning) packageProcessRunning(packageName, user)?.let { put("running", it) }
            }.toString()
    }

    private fun packageProcessRunning(pkg: String, user: Int): Boolean? = runCatching {
        val result = Shell.run(listOf("dumpsys", "activity", "processes", pkg), timeoutMs = 5_000)
        if (result.exitCode == 0) PackageDump.processRunning(result.output, user) else null
    }.getOrNull()

    override fun alarmWakeups(packageName: String): String {
        enforceOwner()
        require(PACKAGE_NAME.matches(packageName)) { "not a package name" }
        val user = Binder.getCallingUid() / PER_USER_RANGE
        val dump = Shell.run(listOf("dumpsys", "alarm"), timeoutMs = 30_000).output
        return (AlarmWakeups.parse(dump, packageName, user) ?: JSONObject())
            .put("sinceBootMs", SystemClock.elapsedRealtime()).toString()
    }

    override fun locationProviders(): String {
        enforceOwner()
        val dump = Shell.run(listOf("dumpsys", "location"), timeoutMs = 30_000).output
        // Android 11 names a provider's package without its uid.
        return LocationProviders.parse(dump) { pkg ->
            runCatching { context.packageManager.getPackageUid(pkg, 0) }.getOrNull()
        }.toString()
    }

    override fun listPackages(): String {
        enforceOwner()
        val user = Binder.getCallingUid() / PER_USER_RANGE
        // "package:<name> uid:<uid>" per line; -u adds packages removed for the user but kept on the phone.
        // "package:<name> installer=<pkg> uid:<uid>" per line; -i adds the installer, -u packages removed
        // for the user but kept on the phone.
        val installers = HashMap<String, String>()
        fun list(vararg flags: String): Map<String, Int>? =
            packageList("-U", "--user", "$user", *flags)
                ?.lineSequence()?.mapNotNull { line ->
                    val name = line.substringAfter("package:", "").substringBefore(' ').trim()
                    val uid = line.substringAfter("uid:", "").trim().substringBefore(',').substringBefore(' ').toIntOrNull() ?: -1
                    if ("installer=" in line) {
                        val installer = line.substringAfter("installer=").substringBefore(' ').trim()
                        if (installer.isNotEmpty() && installer != "null") synchronized(installers) { installers[name] = installer }
                    }
                    name.takeIf(PackageInventory::acceptsName)?.let { it to uid }
                }?.toMap()
        // Each list is its own shell command; run side by side they take as long as the slowest one.
        // The packages with an icon in the launcher, which some phones hide from the app itself.
        val launcherFuture = java.util.concurrent.CompletableFuture.supplyAsync {
            runCatching {
                Shell.run(listOf("cmd", "package", "query-activities", "--components", "--user", "$user",
                    "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER"), timeoutMs = 30_000).output
                    .lineSequence().map { it.substringBefore('/').trim() }.filter { PACKAGE_NAME.matches(it) }.toSet()
            }.getOrNull()
        }
        // When each package was first installed: the phone maker's preloads in the data partition carry
        // the time of the first boot, as the system itself does; anything the user added comes later.
        val dumpFuture = java.util.concurrent.CompletableFuture.supplyAsync {
            runCatching { readPackageDump("packages", user) }
                .onFailure { ProbeMain.log("package inventory dump failed: ${it.javaClass.simpleName}") }.getOrNull()
        }
        val modulesFuture = java.util.concurrent.CompletableFuture.supplyAsync {
            packageList("--apex-only", "-f", "--show-versioncode")?.let(PackageInventory::modules)
        }
        val lists = listOf(arrayOf("-u", "-i"), emptyArray(), arrayOf("-s", "-u"))
            .map { flags -> java.util.concurrent.CompletableFuture.supplyAsync { list(*flags) } }
            .map { it.join() }
        val launcher = launcherFuture.join()
        val dump = dumpFuture.join()
        val packages = PackageInventory.packages(user, dump, lists[0].orEmpty(), lists[1], lists[2], installers, launcher)
        val complete = dump != null && "android" in dump.userStates &&
            lists[1].orEmpty().keys.all { it in dump.userStates }
        return JSONObject().put("packages", packages).put("packagesComplete", complete)
            .put("modules", modulesFuture.join()).toString()
    }

    private fun packageList(vararg flags: String): String? = runCatching {
        Shell.run(listOf("pm", "list", "packages") + flags, timeoutMs = 30_000)
            .takeIf { it.exitCode == 0 && !it.truncated }?.output
    }.getOrNull()

    private fun readPackageDump(target: String, user: Int): PackageDump? {
        val result = Shell.run(listOf("dumpsys", "package", target), timeoutMs = 30_000)
        return if (result.exitCode == 0 && !result.truncated) PackageDump.parse(result.output, user) else null
    }

    /**
     * Installs the APKs the app backed up in its external files. The system server cannot open files
     * on shared storage, so they are streamed to an install session through the shell instead.
     */
    private fun reinstall(user: Int, pkg: String): Pair<Boolean, String> {
        val dir = File("/storage/emulated/$user/Android/data/$packageName/files/backups/$pkg")
        val apks = dir.listFiles { f -> f.isFile && f.name.endsWith(".apk") }.orEmpty().sortedBy { it.name }
        if (apks.isEmpty()) return false to "no backup"
        val created = Shell.run(listOf("pm", "install-create", "-r", "--user", "$user")).output
        val session = Regex("\\[(\\d+)]").find(created)?.groupValues?.get(1) ?: return false to created
        for (apk in apks) {
            val written = runCatching {
                Shell.run(listOf("pm", "install-write", "-S", "${apk.length()}", session, apk.name, "-"), timeoutMs = 120_000, stdinFile = apk)
            }.getOrNull()
            if (written == null || !written.output.contains("Success")) {
                runCatching { Shell.run(listOf("pm", "install-abandon", session)) }
                return false to (written?.output ?: "write failed")
            }
        }
        val committed = runCatching { Shell.run(listOf("pm", "install-commit", session), timeoutMs = 120_000) }.getOrNull()
        return (committed?.output?.contains("Success") == true) to committed?.output.orEmpty()
    }

    private fun enforceOwner() {
        if (Binder.getCallingUid() != ownerUid) throw SecurityException("caller is not the owning app")
    }

    private companion object {
        const val MAX_EVENTS_PER_READ = 500
        const val MAX_HISTORY_PER_READ = 2_000
        const val MAX_HISTORY_SLOTS = 8
        const val FIRST_MAKER_OP = 1_000
        const val PER_USER_RANGE = 100_000
        const val MAX_OUTPUT = 2_000
        val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
    }
}
