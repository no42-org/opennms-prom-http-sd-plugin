/*
 * Copyright 2026 The OpenNMS Group, Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Created by Ronny Trommer <ronny@opennms.com>
 */

package org.opennms.plugins.promsd.sd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SdParserTest {

    private static List<SdTargetGroup> parse(String json) throws SdFormatException {
        return SdParser.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesNl6Example() throws Exception {
        final byte[] body;
        try (var in = getClass().getResourceAsStream("/nl6-sd.json")) {
            body = in.readAllBytes();
        }
        final List<SdTargetGroup> groups = SdParser.parse(body);
        assertEquals(2, groups.size());
        assertEquals(List.of("10.42.0.1"), groups.get(0).targets());
        assertEquals("core-rtr-01", groups.get(0).labels().get("__meta_nl6_sys_name"));
        assertEquals(List.of("10.42.0.2:1161"), groups.get(1).targets());
    }

    @Test
    void parsesEmptyArray() throws Exception {
        assertTrue(parse("[]").isEmpty());
    }

    @Test
    void missingLabelsBecomeEmptyMap() throws Exception {
        assertEquals(Map.of(), parse("[{\"targets\":[\"a\"]}]").get(0).labels());
        assertEquals(
                Map.of(),
                parse("[{\"targets\":[\"a\"],\"labels\":null}]").get(0).labels());
    }

    @Test
    void missingTargetsBecomeEmptyList() throws Exception {
        assertTrue(parse("[{\"labels\":{}}]").get(0).targets().isEmpty());
    }

    @Test
    void rejectsHtmlBody() {
        assertThrows(SdFormatException.class, () -> parse("<html>login</html>"));
    }

    @Test
    void rejectsJsonObject() {
        assertThrows(SdFormatException.class, () -> parse("{}"));
    }

    @Test
    void rejectsJsonNull() {
        assertThrows(SdFormatException.class, () -> parse("null"));
    }

    @Test
    void rejectsNonStringLabelValue() {
        assertThrows(SdFormatException.class, () -> parse("[{\"targets\":[\"a\"],\"labels\":{\"x\":1}}]"));
    }

    @Test
    void rejectsTrailingContent() {
        assertThrows(
                SdFormatException.class,
                () -> SdParser.parse("[]<html>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
