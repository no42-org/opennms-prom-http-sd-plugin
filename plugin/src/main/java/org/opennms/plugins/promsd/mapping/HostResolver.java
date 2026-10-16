/*
 * Copyright 2026 The OpenNMS Group, Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Created by Ronny Trommer <ronny@opennms.com>
 */

package org.opennms.plugins.promsd.mapping;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

@FunctionalInterface
public interface HostResolver {

    Optional<InetAddress> resolve(String host);

    HostResolver DNS = host -> {
        try {
            return Optional.of(InetAddress.getByName(host));
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    };
}
