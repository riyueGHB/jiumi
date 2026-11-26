// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0
package com.ybblab.jiumi.access;

import com.ybblab.jiumi.access.ShizukuCommandResult;

/** Runs in a short-lived process Shizuku starts as the shell or root; starts the monitor and changes packages. */
interface IShizukuShellService {
    // Shizuku's reserved transaction for ending a user service.
    void destroy() = 16777114;
    int getUid() = 1;
    const int ERROR_IDENTITY = 1;
    const int ERROR_TIMEOUT = 2;
    const int ERROR_EXECUTE = 3;

    ShizukuCommandResult execute(String command, long timeoutMs) = 2;
}
