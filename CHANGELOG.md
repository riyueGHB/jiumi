# Changelog

Notable changes to Jiumi, newest first. The English in-app update notes come from this file.
Chinese in-app notes are supplied when preparing the release.
Versions follow [Semantic Versioning](https://semver.org).

## [0.6.0] - 2026-11-24

- **Hide Jiumi from recent apps.** Turn on **Hide task card** in **Settings → Advanced** to avoid swiping Jiumi away by accident. You can still open it from your home screen.
- **New system switches in Permissions.** Limit supported operations such as clipboard reads and background activity. Re-enabling a switch restores the setting Jiumi saved for that app. Tap a package name to copy it.
- **Expanded Expert mode.** Manage more default and system apps and change eligible role-granted or shared-UID permissions. Jiumi warns about wider effects and now reads shared-UID permission states more accurately.
- **Fixed Wireless debugging pairing stalls.** An unfinished monitoring session resumes when Wi-Fi and Wireless debugging return and the existing pairing is still valid.
- **Fixed an Android 17 monitoring crash** when reading location providers. Per-app network blocking requires Android 14 or later, and Android clears its rules on restart.

## [0.5.0] - 2026-11-23

- **More ways to enter the wireless pairing code.** If notification entry is unavailable, use split screen, a small window, or the “Jiumi pairing” Quick Settings tile. The setup guide includes animations and steps.
- The code entry screen helps keep both apps visible, can open Wireless debugging alongside Jiumi in split screen, and confirms successful pairing.
- Fixed missing permission accesses on Android 11 by checking system records periodically. Jiumi now recognizes location providers on Android 11.
- Fixed unreadable text in MIUI dark mode and restored system access history on Android 17.
- Clarified when removing a preinstalled app for the current user requires Root.

## [0.4.1] - 2026-11-23

- Improved freeze and unfreeze compatibility. Jiumi tries an alternate method after verifying that the primary method left the app state unchanged.
- Timeline and record details now load in pages. List updates wait until scrolling stops.
- Speeded up grouping repeated events, reducing the time it takes to build large timelines.

## [0.4.0] - 2026-11-22

- **Add activity charts and app rankings.** See access trends, attention heatmaps and per-app charts. Rank apps by all or background accesses.
- **Add network debugging.** Find an existing ADB port on the phone automatically or enter it manually. Use this mode on trusted networks.
- **Improve monitoring recovery.** Retry when Jiumi returns to the foreground, show recovery status and exclude interruptions from monitoring time.
- **Update notifications and settings.** Show access and background counts in the optional notification summary, rename ADB persistence to Backup connection, and link to App management to enable preinstalled app removal.

## [0.3.2]

- **Add a monitoring allowlist.** Find it under Settings → Monitoring. Allowlisted apps appear at the top, generate no new monitoring records, and have their saved records hidden. Removing an app resumes monitoring and shows its saved records. Activity during the excluded period is never backfilled.
- **Standardize app management terms.** Use Disable, Enable, and Uninstall consistently. Confirmation dialogs for disabling and uninstalling explain whether app data is kept.

## [0.3.1]

- Reorganize Settings into four cards, separate connection from monitoring, move Expert mode under Advanced, and show full links on the About page.
- Include system components and APEX modules in the package inventory. Keep the total fixed during search and filtering, and retain the cached list offline.
- Improve foreground and background counts, shared-UID and domain attribution, and access deduplication. Fix history pagination and interrupted writes, and warn when traces could not be fully saved.
- Keep APK backups when an uninstall result is uncertain. Viewing access details no longer starts monitoring. ADB persistence is off by default, and domain recording supports DNS over TCP.

## [0.3.0]

- Use root or Shizuku started with root to freeze and unfreeze apps, or remove and restore preinstalled apps for the current user.
- See when a freeze is lifted and optionally reapply it. Uninstalling apps you installed now affects only the current user.
- The compact notification shows actual monitoring time, excluding interruptions, and has a stop button. It can be turned off. Session counts and attention alerts are optional.
- Automatically resume unfinished sessions when the selected connection is available. Fix stalls after updates and reboots, and defer ADB persistence changes during monitoring.

## [0.2.3]

- Fix setup getting stuck after root access has been granted. Jiumi checks existing grants automatically and reports when switching to the Android shell user fails.

## [0.2.2]

- Verify app freezing and check that preinstalled apps were deleted or restored for the current user.
- Explain when the phone blocks an action and fix freeze status checks on some ColorOS devices.
- Add snowflake and trash badges to frozen and deleted apps while keeping their original icon colors.
- Fix process starts appearing under the wrong app for some system components.

## [0.2.1]

- Streamlined setup for root and Shizuku, with clearer authorization steps.
- Refresh root and Shizuku authorization when returning to Jiumi.
- Improved navigation to Developer options for wireless and USB debugging.
- Fixed stale authorization errors after access is granted.

## [0.2.0]

- Manage permissions from each app’s details. Turn them off individually or together, and restore permissions Jiumi previously revoked.
- Block or restore network access per app on supported devices, without a VPN.
- See why a control is unavailable and check the state reported by Android when a change fails.

## [0.1.0]

The first public release.

### Added

- Monitoring through Wireless debugging, with no root: 56 kinds of personal data in 12 groups, from AppOps records and traced system-service calls, plus network connections; each record notes whether the app was in the foreground and the screen on.
- An overview of what needs a look: sensitive data accessed in the background, frequent access, repeated refused attempts, and data sent in the background (the bytes measured, with nothing inferred about why).
- Three ways to start the monitor: Wireless debugging, root (Magisk, KernelSU, APatch; used only to switch to the shell identity) or Shizuku, chosen on the first page and in Settings.
- Android's own records are read too: its per-access record (about a week, with times) and its long-term counts (about a month, every permission), so an app's page shows what happened before monitoring began and parts of the system that cannot be watched live. Permissions a phone maker added are listed apart as the maker's own.
- Connections appear within seconds of being seen, and are updated while they last.
- An app's page shows how many times its alarms woke the sleeping phone since it was last restarted.
- Monitoring picks itself up again after Jiumi is updated.
- Optional domain recording: a VPN on the phone that receives DNS queries only records the domains each app looks up, and connections show them.
- First-launch pairing guide: one card walks through allowing notifications, turning on Developer options and pairing with Wireless debugging, ticking each step off when done.
- Timeline: an app's network connections close together fold into one line, and the timeline can be narrowed to Background or Refused; Connect to the network sums up by app, each opening the domains and addresses it reached.
- Judgements lean towards missing rather than misreporting: a system call made without the permission it needs is told as refused; calls that return only the app's own information are left out; always-on system processes do not count as background; records with no known caller are not shown; a traced call is shown only when Android's own record confirms the access, since without it what the call returned cannot be told; work an app does for another app, such as a location service scanning Wi‑Fi for the app that asked, is left out.
- ADB persistence, on by default: after one connection over Wireless debugging, monitoring starts through a local channel even offline, until the phone restarts.
- App management: disable, uninstall or remove for this user, with backups to bring apps back, warnings before changing apps the phone relies on, and an expert mode for core system components. On phones that refuse to disable system apps, such as HyperOS, the app is suspended and stopped instead.
- Apps grouped by where they came from (app stores, installed directly, preinstalled, system components), with counts that jump to each group.
- Updates from the project's GitHub releases: checked on opening at most every six hours (can be turned off), offered once, and verified by size, SHA-256 and signing key before installing.
- Export of the records as a ZIP; nothing ever leaves the phone otherwise.
