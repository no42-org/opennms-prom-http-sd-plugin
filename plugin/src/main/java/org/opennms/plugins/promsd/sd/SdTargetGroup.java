/*
 * Copyright 2026 The OpenNMS Group, Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Created by Ronny Trommer <ronny@opennms.com>
 */

package org.opennms.plugins.promsd.sd;

import java.util.List;
import java.util.Map;

public record SdTargetGroup(List<String> targets, Map<String, String> labels) {
    public SdTargetGroup {
        targets = List.copyOf(targets);
        labels = Map.copyOf(labels);
    }
}
