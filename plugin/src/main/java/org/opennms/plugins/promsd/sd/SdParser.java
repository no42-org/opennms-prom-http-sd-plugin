/*
 * Copyright 2026 The OpenNMS Group, Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Created by Ronny Trommer <ronny@opennms.com>
 */

package org.opennms.plugins.promsd.sd;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SdParser {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private SdParser() {}

    public static List<SdTargetGroup> parse(byte[] body) throws SdFormatException {
        final JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (IOException e) {
            throw new SdFormatException("Response is not valid JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isArray()) {
            throw new SdFormatException("Expected a JSON array of target groups");
        }
        final List<SdTargetGroup> groups = new ArrayList<>();
        for (JsonNode node : root) {
            if (!node.isObject()) {
                throw new SdFormatException("Target group is not a JSON object");
            }
            groups.add(new SdTargetGroup(targets(node.get("targets")), labels(node.get("labels"))));
        }
        return List.copyOf(groups);
    }

    private static List<String> targets(JsonNode node) throws SdFormatException {
        final List<String> targets = new ArrayList<>();
        if (node == null || node.isNull()) {
            return targets;
        }
        if (!node.isArray()) {
            throw new SdFormatException("'targets' is not an array");
        }
        for (JsonNode t : node) {
            if (!t.isTextual()) {
                throw new SdFormatException("Target is not a string: " + t);
            }
            targets.add(t.asText());
        }
        return targets;
    }

    private static Map<String, String> labels(JsonNode node) throws SdFormatException {
        final Map<String, String> labels = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            return labels;
        }
        if (!node.isObject()) {
            throw new SdFormatException("'labels' is not an object");
        }
        for (Map.Entry<String, JsonNode> e : node.properties()) {
            if (!e.getValue().isTextual()) {
                throw new SdFormatException("Label '" + e.getKey() + "' is not a string");
            }
            labels.put(e.getKey(), e.getValue().asText());
        }
        return labels;
    }
}
