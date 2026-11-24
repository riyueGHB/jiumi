// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import org.json.JSONObject

/** What the user may do to an app; [id] is what the monitor and the action log call it. */
enum class ManageAction(val id: String) {
    DISABLE("disable"), ENABLE("enable"), UNSUSPEND("unsuspend"), UNINSTALL("uninstall"), REMOVE("remove"), RESTORE("restore"),
    /** Installs a deleted app again from its backup. */
    REINSTALL("reinstall");

    companion object {
        fun of(id: String): ManageAction? = entries.firstOrNull { it.id == id }
    }
}

/** Why an app is left alone. [role] names the default role it holds, for [ROLE]. */
data class Refusal(val reason: Reason, val role: String? = null) {
    enum class Reason { SELF, PLATFORM, ROLE, PROTECTED }
}

/** The facts about one package that management decisions rest on. */
data class PackageFacts(
    val pkg: String,
    val uid: Int,
    val preinstalled: Boolean,
    val persistent: Boolean,
    val sharedUser: String?,
    /** Installed for this user; a preinstalled app removed for the user is not. */
    val installed: Boolean,
    /**
     * Package Manager reports it turned off for this user: COMPONENT_ENABLED_STATE_DISABLED_USER (as
     * the shell turns it off) or COMPONENT_ENABLED_STATE_DISABLED (as root or the system does).
     */
    val disabled: Boolean,
    /** Has code of its own. Null if the package list did not report the flags. */
    val hasCode: Boolean? = true,
    /** Shows an icon in the launcher. */
    val launchable: Boolean = true,
    /** Default roles held: "home", "keyboard", "sms", "dialer". */
    val roles: Set<String> = emptySet(),
    /** The package that installed it, as the system records it; null when nothing did (adb, preloads). */
    val installer: String? = null,
    /**
     * Put on the phone by its maker outside the system partition, so it can be uninstalled like any
     * app the user added, but came with the phone: first installed at the first boot, with the system.
     */
    val factoryPreload: Boolean = false,
    /** A shell suspension can still be lifted after upgrading from an older monitor. */
    val suspendedByShell: Boolean = false
)

/**
 * Where an app came from: [PREINSTALLED] came with the phone (in the system image, updated or not, or
 * preloaded by the phone's maker with the first boot); the rest the user added, from an app store
 * ([STORE]) or by other means ([SIDELOADED]: an APK opened from files or a browser, or adb).
 */
enum class AppSource { STORE, SIDELOADED, PREINSTALLED }

/**
 * Why changing an app may disturb the phone even though it is allowed: [CORE] for a core system
 * component that expert mode lets through; [LISTED] for the services and providers other apps lean on,
 * named in the rules; [BACKGROUND] for a preinstalled app with no icon in the launcher, which is
 * usually part of how the phone works rather than something the user opens.
 */
enum class Risk { LISTED, BACKGROUND,
    /** A core system component, changeable only in expert mode. */
    CORE,
    /** Named in the rules as something the phone needs, changeable only in expert mode. */
    PROTECTED,
    /** Holds a default role (home, SMS, phone), changeable only in expert mode. */
    ROLE }

/** From assets/rules/manage.json: shared identities and packages that are part of the phone. */
class ManageRules(
    val sharedUsers: Set<String>,
    val protectedPackages: Set<String>,
    val cautionPackages: Set<String> = emptySet(),
    val cautionPrefixes: List<String> = emptyList(),
    /** Core components that stay out of reach even in expert mode. */
    val corePackages: Set<String> = emptySet()
) {
    companion object {
        fun load(context: Context): ManageRules = parse(context.assets.open("rules/manage.json").bufferedReader().use { it.readText() })

        fun parse(text: String): ManageRules {
            val root = JSONObject(text)
            fun strings(name: String) = root.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()
            return ManageRules(strings("sharedUsers"), strings("protectedPackages"), strings("cautionPackages"),
                strings("cautionPrefixes").toList(), strings("corePackages"))
        }
    }
}

/** Decides what may be done to a package. Pure, so it is tested without a phone. */
object Management {
    const val FIRST_APP_UID = 10_000

    /**
     * Why [f] is left alone, or null. [expert] mode lets through core components, the rules' protected
     * packages and default home, SMS and phone apps. What would leave no way back stays refused even
     * then: Jiumi itself, persistent apps and the rules' core packages, and the keyboard in use.
     */
    fun refusal(f: PackageFacts, rules: ManageRules, self: String, expert: Boolean = false): Refusal? = when {
        f.pkg == self -> Refusal(Refusal.Reason.SELF)
        isCore(f, rules) && !(expert && expertMay(f, rules)) -> Refusal(Refusal.Reason.PLATFORM)
        KEYBOARD in f.roles -> Refusal(Refusal.Reason.ROLE, KEYBOARD)
        expert -> null
        f.pkg in rules.protectedPackages -> Refusal(Refusal.Reason.PROTECTED)
        f.roles.isNotEmpty() -> Refusal(Refusal.Reason.ROLE, f.roles.first())
        else -> null
    }

    /** Whether turning on expert mode would let [f] be changed, while it is refused now. */
    fun expertUnlocks(f: PackageFacts, rules: ManageRules, self: String): Boolean =
        refusal(f, rules, self) != null && refusal(f, rules, self, expert = true) == null

    private const val KEYBOARD = "keyboard"

    /**
     * A removed preinstalled app can only be restored. A suspended or disabled app can be brought back.
     * Preinstalled apps are removed for this user only, and only when [allowRemove].
     */
    fun actions(f: PackageFacts, allowRemove: Boolean): List<ManageAction> = when {
        f.hasCode != true || f.uid < 0 || !isPackageName(f.pkg) -> emptyList()
        !f.installed -> if (f.preinstalled) listOf(ManageAction.RESTORE) else emptyList()
        else -> listOfNotNull(when {
            f.suspendedByShell -> ManageAction.UNSUSPEND
            f.disabled -> ManageAction.ENABLE
            else -> ManageAction.DISABLE
        },
            if (!f.preinstalled) ManageAction.UNINSTALL else ManageAction.REMOVE.takeIf { allowRemove })
    }

    /**
     * Whether turning [f] off or removing it may stop the phone working as it should; asked only of
     * apps that may be changed at all. Null for an ordinary app.
     */
    fun risk(f: PackageFacts, rules: ManageRules): Risk? = when {
        isCore(f, rules) -> Risk.CORE
        f.pkg in rules.protectedPackages -> Risk.PROTECTED
        f.roles.isNotEmpty() -> Risk.ROLE
        f.pkg in rules.cautionPackages || rules.cautionPrefixes.any { f.pkg.startsWith(it) } -> Risk.LISTED
        f.preinstalled && !f.launchable -> Risk.BACKGROUND
        else -> null
    }

    /**
     * Where [f] came from. A maker's preload counts as preinstalled even when a store or the maker's own
     * updater has updated it since. A user app is from a store when a store installed it; the system's package
     * installer (any phone maker's), the shell and no installer at all mean it was installed directly.
     */
    fun source(f: PackageFacts): AppSource = when {
        f.preinstalled || f.factoryPreload -> AppSource.PREINSTALLED
        isDirectInstaller(f.installer) -> AppSource.SIDELOADED
        else -> AppSource.STORE
    }

    private fun isDirectInstaller(installer: String?) = installer.isNullOrBlank() || installer == "null" ||
        installer.contains("packageinstaller", ignoreCase = true) || installer == "com.android.shell" || installer == "adb"

    /** Part of the platform: running as a system identity, kept alive by the system, or sharing its identity. */
    fun isCore(f: PackageFacts, rules: ManageRules) =
        f.uid % 100_000 < FIRST_APP_UID || f.persistent || f.sharedUser in rules.sharedUsers

    /** A core component expert mode may change: not persistent, and not one the phone cannot do without. */
    fun expertMay(f: PackageFacts, rules: ManageRules) = !f.persistent && f.pkg !in rules.corePackages

    /** A package first installed this soon after the system came with the phone: before setup was done. */
    const val FACTORY_WINDOW_MS = 5 * 60_000L

    fun isFactoryTime(firstInstall: Long, firstBoot: Long) = firstInstall - firstBoot in 0..FACTORY_WINDOW_MS

    /** An enabled setting that keeps the app from running: turned off by the user, root or the system. */
    fun isDisabled(setting: Int?) = setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
        setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    /** Undoing an action never needs a guard: it puts the phone back as it was. */
    fun isUndo(action: ManageAction) = when (action) {
        ManageAction.ENABLE, ManageAction.UNSUSPEND, ManageAction.RESTORE, ManageAction.REINSTALL -> true
        else -> false
    }

    private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
    private val CATALOG_PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*")

    /** A package name, and so safe as a directory name. */
    fun isPackageName(name: String) = PACKAGE_NAME.matches(name)

    fun isCatalogPackage(name: String) = CATALOG_PACKAGE_NAME.matches(name)
}

/** A package as the monitor (the shell) lists it. */
data class ShellPackage(val pkg: String, val uid: Int, val system: Boolean, val enabledSetting: Int?, val installed: Boolean,
                        val installer: String? = null,
                        /** Shows an icon in the launcher; null from a monitor that did not say. */
                        val launcher: Boolean? = null,
                        /** First installed with the system: a preload of the phone's maker. */
                        val factory: Boolean = false,
                        /** Suspended from here in place of being disabled; other apps' suspensions are not counted. */
                        val suspended: Boolean = false,
                        val hasCode: Boolean? = null,
                        val persistent: Boolean = false,
                        val sharedUser: String? = null,
                        val codePath: String? = null) {
    companion object {
        fun parse(array: org.json.JSONArray): List<ShellPackage> =
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val pkg = o.getString("pkg")
                if (!Management.isCatalogPackage(pkg)) return@mapNotNull null
                ShellPackage(pkg, o.optInt("uid", -1), o.optBoolean("system"), o.optInt("enabled", -1).takeIf { it >= 0 },
                    o.getBoolean("installed"), o.stringOrNull("installer"),
                    if (o.has("launcher")) o.optBoolean("launcher") else null, o.optBoolean("factory"), o.optBoolean("suspended"),
                    if (o.has("hasCode")) o.optBoolean("hasCode") else null, o.optBoolean("persistent"),
                    o.stringOrNull("sharedUser"), o.stringOrNull("codePath"))
            }
    }
}

data class SystemModule(val pkg: String, val path: String, val version: Long?)

data class ShellCatalog(val packages: List<ShellPackage> = emptyList(), val modules: List<SystemModule>? = null) {
    companion object {
        /** A partial read can supply a new section but cannot replace one already read in full. */
        fun retainCompleteSections(fresh: JSONObject, saved: JSONObject?): JSONObject = fresh.apply {
            val oldModules = saved?.optJSONArray("modules")
            if (optJSONArray("modules") == null || optJSONArray("modules")?.length() == 0 && oldModules != null) {
                put("modules", oldModules)
            }
            val oldPackages = saved?.optJSONArray("packages")
            if (oldPackages != null && (!optBoolean("packagesComplete") && saved.optBoolean("packagesComplete", true) ||
                    optJSONArray("packages")?.length() == 0)) {
                put("packages", oldPackages)
                put("packagesComplete", saved.optBoolean("packagesComplete", true))
            }
        }

        fun parse(json: JSONObject): ShellCatalog {
            val modules = json.optJSONArray("modules")?.let { array ->
                List(array.length()) { i ->
                    val item = array.getJSONObject(i)
                    SystemModule(item.getString("pkg"), item.getString("path"), item.optLong("version", -1).takeIf { it >= 0 })
                }.filter { Management.isCatalogPackage(it.pkg) }
            }
            return ShellCatalog(ShellPackage.parse(json.getJSONArray("packages")), modules)
        }
    }
}

private fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

/** Reads package facts from the phone. */
class PackageFactsSource(private val context: Context) {
    private val pm = context.packageManager

    /** [pkg]'s facts, from the [shell]'s list when the phone will not show it. */
    fun facts(pkg: String, shell: List<ShellPackage> = emptyList()): PackageFacts? {
        val roles = rolesHeld()
        val listed = shell.firstOrNull { it.pkg == pkg }
        return facts(pkg, roles)?.let { applySuspension(it, listed?.suspended == true) } ?: listed?.let { facts(it, roles) }
    }

    /** Use the shell's suspender information while the current package state still shows a suspension. */
    private fun applySuspension(f: PackageFacts, suspendedByShell: Boolean): PackageFacts =
        if (suspendedByShell && runCatching { pm.isPackageSuspended(f.pkg) }.getOrDefault(true)) {
            f.copy(suspendedByShell = true)
        } else f

    /**
     * Every package on the phone for this user, removed preinstalled apps included. Some phones show
     * an app only part of the list unless it holds their own app-list permission; the [shell]'s list,
     * when there is one, fills it in, and stands for the facts of packages the phone will not show.
     */
    fun all(shell: List<ShellPackage> = emptyList()): List<PackageFacts> {
        val roles = rolesHeld()
        // One call for every package and one for the launcher's apps, rather than a few per package:
        // a phone with hundreds of apps lists them in a fraction of the time.
        val infos = runCatching { pm.getInstalledPackages(LIST_FLAGS) }.getOrDefault(emptyList())
        val firstBoot = firstBoot()
        val launchable = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), LIST_FLAGS)
                .mapTo(HashSet()) { it.activityInfo.packageName }
        }.getOrDefault(emptySet())
        val suspended = shell.filter { it.suspended }.mapTo(HashSet()) { it.pkg }
        val local = infos.mapNotNull { info -> facts(info, roles, info.packageName in launchable, firstBoot) }
            .map { applySuspension(it, it.pkg in suspended) }
        val seen = local.mapTo(HashSet()) { it.pkg }
        return local + shell.filter { it.pkg !in seen }.map { facts(it, roles) }
    }

    /** What the shell knows; without word on the launcher, a hidden system package counts as not in it. */
    private fun facts(s: ShellPackage, roles: Map<String, Set<String>>) = PackageFacts(
        pkg = s.pkg, uid = s.uid, preinstalled = s.system, persistent = s.persistent, sharedUser = s.sharedUser,
        installed = s.installed, disabled = Management.isDisabled(s.enabledSetting),
        hasCode = s.hasCode, launchable = s.launcher ?: !s.system,
        roles = roles[s.pkg].orEmpty(), installer = s.installer, factoryPreload = s.factory && !s.system,
        suspendedByShell = s.suspended
    )

    private fun facts(pkg: String, roles: Map<String, Set<String>>): PackageFacts? {
        val info = runCatching { pm.getPackageInfo(pkg, LIST_FLAGS) }.getOrNull() ?: return null
        return facts(info, roles, pm.getLaunchIntentForPackage(pkg) != null, firstBoot())
    }

    private fun facts(info: PackageInfo, roles: Map<String, Set<String>>, launchable: Boolean, firstBoot: Long?): PackageFacts? {
        val pkg = info.packageName
        val app = info.applicationInfo ?: return null
        // Only a package that is off can have been turned off by the user; asking costs a call each.
        val state = if (app.enabled) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        else runCatching { pm.getApplicationEnabledSetting(pkg) }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        val system = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        @Suppress("DEPRECATION")
        return PackageFacts(
            pkg = pkg,
            uid = app.uid,
            preinstalled = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            persistent = app.flags and ApplicationInfo.FLAG_PERSISTENT != 0,
            sharedUser = info.sharedUserId,
            installed = app.flags and ApplicationInfo.FLAG_INSTALLED != 0,
            disabled = Management.isDisabled(state),
            hasCode = app.flags and ApplicationInfo.FLAG_HAS_CODE != 0,
            launchable = launchable,
            roles = roles[pkg].orEmpty(),
            installer = runCatching { pm.getInstallSourceInfo(pkg).installingPackageName }.getOrNull(),
            factoryPreload = !system && firstBoot != null && Management.isFactoryTime(info.firstInstallTime, firstBoot)
        )
    }

    /** When the system itself was installed: the phone's first boot. */
    private fun firstBoot(): Long? = runCatching { pm.getPackageInfo("android", 0).firstInstallTime }.getOrNull()?.takeIf { it > 0 }

    private companion object {
        const val LIST_FLAGS = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
    }

    /** The packages holding the default home, keyboard, SMS and phone roles, with the roles each holds. */
    private fun rolesHeld(): Map<String, Set<String>> {
        val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        val keyboard = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
        val sms = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
        val dialer = runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
        return listOf("home" to home, "keyboard" to keyboard, "sms" to sms, "dialer" to dialer)
            .mapNotNull { (role, pkg) -> pkg?.let { it to role } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
    }
}
