/*
 * Copyright 2026 The OpenNMS Group, Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Created by Ronny Trommer <ronny@opennms.com>
 */
package org.opennms.plugins.promsd.http;

public class SdFetchException extends Exception {

    private static final long serialVersionUID = 1L;

    public SdFetchException(String message) {
        super(message);
    }

    public SdFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
