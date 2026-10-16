/*
 * Copyright 2026 The OpenNMS Group, Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Created by Ronny Trommer <ronny@opennms.com>
 */

package org.opennms.plugins.promsd.sd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TargetAddressTest {

    @Test
    void ipv4() {
        assertEquals("10.42.0.1", TargetAddress.parse("10.42.0.1").host());
    }

    @Test
    void ipv4WithPort() {
        assertEquals("10.42.0.1", TargetAddress.parse("10.42.0.1:1161").host());
    }

    @Test
    void bracketedIpv6WithPort() {
        assertEquals("2001:db8::1", TargetAddress.parse("[2001:db8::1]:161").host());
    }

    @Test
    void bracketedIpv6() {
        assertEquals("2001:db8::1", TargetAddress.parse("[2001:db8::1]").host());
    }

    @Test
    void bareIpv6() {
        assertEquals("2001:db8::1", TargetAddress.parse("2001:db8::1").host());
    }

    @Test
    void hostnameWithPort() {
        assertEquals("router.example", TargetAddress.parse("router.example:161").host());
    }

    @Test
    void rejectsBlankAndUnclosedBracket() {
        assertThrows(IllegalArgumentException.class, () -> TargetAddress.parse(""));
        assertThrows(IllegalArgumentException.class, () -> TargetAddress.parse("  "));
        assertThrows(IllegalArgumentException.class, () -> TargetAddress.parse("[2001:db8::1"));
    }

    @Test
    void sanitizeReplacesUnsafeCharacters() {
        assertEquals("2001_db8__1", TargetAddress.sanitizeForeignId("2001:db8::1"));
        assertEquals("core_rtr_01_x", TargetAddress.sanitizeForeignId("core rtr/01?x"));
        assertEquals("core-rtr-01.lab", TargetAddress.sanitizeForeignId("core-rtr-01.lab"));
    }
}
