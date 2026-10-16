/*
 * Copyright 2026 The OpenNMS Group, Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Created by Ronny Trommer <ronny@opennms.com>
 */

package org.opennms.plugins.promsd.sd;

public record TargetAddress(String host) {

    public static TargetAddress parse(String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Blank SD target");
        }
        final String t = target.trim();
        if (t.startsWith("[")) {
            final int close = t.indexOf(']');
            if (close < 0) {
                throw new IllegalArgumentException("Unclosed '[' in SD target: " + target);
            }
            return new TargetAddress(t.substring(1, close));
        }
        final int first = t.indexOf(':');
        if (first < 0) {
            return new TargetAddress(t);
        }
        if (t.indexOf(':', first + 1) >= 0) {
            return new TargetAddress(t);
        }
        return new TargetAddress(t.substring(0, first));
    }

    public static String sanitizeForeignId(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
