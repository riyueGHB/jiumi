// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0
package com.ybblab.jiumi.access;

/** Helper status and, when the command completes, its exit code and combined stdout and stderr. */
parcelable ShizukuCommandResult {
    int status = 0;
    int exitCode = -1;
    String output = "";
}
