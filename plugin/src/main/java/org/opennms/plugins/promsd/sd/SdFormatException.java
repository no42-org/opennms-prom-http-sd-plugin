/*
 * Copyright 2026 The OpenNMS Group, Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Created by Ronny Trommer <ronny@opennms.com>
 */

package org.opennms.plugins.promsd.sd;

public class SdFormatException extends Exception {
    public SdFormatException(String message) {
        super(message);
    }

    public SdFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
