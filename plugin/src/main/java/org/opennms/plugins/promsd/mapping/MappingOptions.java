/*
 * Copyright 2026 The OpenNMS Group, Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Created by Ronny Trommer <ronny@opennms.com>
 */

package org.opennms.plugins.promsd.mapping;

/** Mapping settings. Every field except {@code foreignSource} may be null. */
public record MappingOptions(
        String foreignSource,
        String foreignIdLabel,
        String nodeLabelLabel,
        String categoryLabel,
        String nodeLocation) {}
