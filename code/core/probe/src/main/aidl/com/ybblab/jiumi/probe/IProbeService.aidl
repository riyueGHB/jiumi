// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.probe;

/** Shell-identity monitor. Every method except protocolVersion accepts only the owning app. */
interface IProbeService {
    int protocolVersion();
    /** The monitor re-offers itself after this token dies and the app process starts again. */
    void attach(IBinder token);
    /** JSON {"ops":[...],"calls":{...},"network":{...},"spool":{"limitMb":n}}. Kept across monitor restarts. */
    void configure(String config);
    /**
     * JSON {"epoch":n,"events":[...],"last":seq,"first":seq,"more":bool}. A cursor
     * acknowledges events only in the same spool epoch.
     */
    String readEvents(long epoch, long afterSeq, int max);
    /**
     * The system's per-access record (the one behind its privacy dashboard; a few kinds of data, kept
     * about a week), at or after fromTime. Offset zero reads a snapshot; the returned next index
     * continues that same snapshot when more is true. JSON {"events":[...],"next":n,"more":bool}.
     */
    String readDiscreteHistory(long fromTime, int offset, int max);
    /**
     * The system's long-term access counts for the operations being watched: its time slots that ended
     * after sinceTime, newest first, skipping the first skip and returning at most max. JSON array of
     * {"from","to","ops":[{"uid","pkg","op","fg","bg","fgRejected","bgRejected","durationMs"}]}.
     */
    String readHistoricalOps(long sinceTime, int skip, int max);
    /** The monitor's own resource use. Returns JSON. */
    String footprint();
    /** Stops the collectors, writes their remaining events, and returns true if trace records were lost. */
    boolean finishRecording();
    void shutdown();
    /**
     * Changes a package for the calling user: action "disable", "enable", "unsuspend", "uninstall"
     * (removes it and its data for this user; other users keep theirs), "remove" (a preinstalled app, for this user only), "restore"
     * (brings a removed preinstalled app back) or "reinstall" (installs the APKs the app backed up
     * under its external files, backups/<packageName>/). Returns JSON {"ok":bool,"output":text}.
     * "failure", when known, is "PERMISSION_DENIED", "NOT_APPLIED" (the observed state differs from
     * the requested state), or "UNVERIFIED" (the state could not be read after the command).
     * "running", when available after a verified disable or removal, reports the observed process state.
     * For disable and enable, "path" lists the attempted shell command and, when used, framework Binder.
     */
    String managePackage(String action, String packageName);
    /**
     * Every package for the calling user as the shell sees it, which some phones hide from apps.
     * JSON with packages, packagesComplete, and modules when the APEX list could be read.
     * Each package has its name, UID, installation and suspension state, and available metadata.
     */
    String listPackages();
    /**
     * The apps the system has bound as its location providers, from its location dump. JSON array of
     * {"name":provider,"uid":n,"pkg":name}.
     */
    String locationProviders();
    /**
     * How often the calling user's app's alarms woke the phone since boot, from the alarm service.
     * JSON {"sinceBootMs":n,"wakeups":n,"receivers":[{"tag","wakes","lastAgoMs"}]}; "wakeups" and
     * "receivers" are missing when the system lists no alarm of the app.
     */
    String alarmWakeups(String packageName);
    /**
     * Reads or changes a single installed app’s access. Returns verified state and operation results.
     * [expert] lets through changes that reach past the one app; the result names each in "warn".
     */
    String appAccess(String packageName, int uid, String action, String permission, boolean expert);
    /**
     * A package's state for the calling user, read back after a change made outside the monitor (as
     * root). JSON {"installed":bool,"enabled":n,"suspended":bool}, plus "running" when asked for and
     * known; {} when the state could not be read.
     */
    String packageState(String packageName, boolean checkRunning);
}
