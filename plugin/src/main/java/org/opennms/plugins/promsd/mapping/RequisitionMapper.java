/*
 * Copyright 2026 The OpenNMS Group, Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Created by Ronny Trommer <ronny@opennms.com>
 */

package org.opennms.plugins.promsd.mapping;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.opennms.integration.api.v1.config.requisition.Requisition;
import org.opennms.integration.api.v1.config.requisition.SnmpPrimaryType;
import org.opennms.integration.api.v1.config.requisition.immutables.ImmutableRequisition;
import org.opennms.integration.api.v1.config.requisition.immutables.ImmutableRequisitionInterface;
import org.opennms.integration.api.v1.config.requisition.immutables.ImmutableRequisitionMetaData;
import org.opennms.integration.api.v1.config.requisition.immutables.ImmutableRequisitionNode;
import org.opennms.plugins.promsd.sd.SdTargetGroup;
import org.opennms.plugins.promsd.sd.TargetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RequisitionMapper {

    private static final Logger LOG = LoggerFactory.getLogger(RequisitionMapper.class);
    private static final String META_CONTEXT = "prometheus";

    private final HostResolver resolver;

    public RequisitionMapper(HostResolver resolver) {
        this.resolver = resolver;
    }

    public Requisition map(List<SdTargetGroup> groups, MappingOptions options) {
        final ImmutableRequisition.Builder requisition =
                ImmutableRequisition.newBuilder().setForeignSource(options.foreignSource());
        final Set<InetAddress> seenAddresses = new HashSet<>();
        final Set<String> seenForeignIds = new HashSet<>();
        int reachedResolution = 0;
        int unresolved = 0;

        for (SdTargetGroup group : groups) {
            final Map<String, String> labels = group.labels();
            for (String target : group.targets()) {
                final String host;
                try {
                    host = TargetAddress.parse(target).host();
                } catch (IllegalArgumentException e) {
                    LOG.warn("Skipping unparsable SD target '{}': {}", target, e.getMessage());
                    continue;
                }
                if (host.isEmpty()) {
                    LOG.warn("Skipping SD target '{}': empty host", target);
                    continue;
                }
                reachedResolution++;
                final Optional<InetAddress> resolved = resolver.resolve(host);
                if (resolved.isEmpty()) {
                    unresolved++;
                    LOG.warn("Skipping SD target '{}': host '{}' does not resolve", target, host);
                    continue;
                }
                final InetAddress address = resolved.get();
                if (!seenAddresses.add(address)) {
                    LOG.warn(
                            "Skipping SD target '{}': address {} already used (one device per IP)",
                            target,
                            address.getHostAddress());
                    continue;
                }
                final String idValue = value(labels, options.foreignIdLabel());
                final String foreignId = TargetAddress.sanitizeForeignId(idValue != null ? idValue : host);
                if (!seenForeignIds.add(foreignId)) {
                    LOG.warn("Skipping SD target '{}': foreign ID '{}' already used", target, foreignId);
                    continue;
                }
                final String labelValue = value(labels, options.nodeLabelLabel());
                final ImmutableRequisitionNode.Builder node = ImmutableRequisitionNode.newBuilder()
                        .setForeignId(foreignId)
                        .setNodeLabel(labelValue != null ? labelValue : host)
                        .addInterface(ImmutableRequisitionInterface.newBuilder()
                                .setIpAddress(address)
                                .setSnmpPrimary(SnmpPrimaryType.PRIMARY)
                                .build());
                if (options.nodeLocation() != null) {
                    node.setLocation(options.nodeLocation());
                }
                final String category = value(labels, options.categoryLabel());
                if (category != null) {
                    node.addCategory(category);
                }
                labels.forEach(
                        (k, v) -> node.addMetaData(ImmutableRequisitionMetaData.newInstance(META_CONTEXT, k, v)));
                requisition.addNode(node.build());
            }
        }
        if (reachedResolution > 0 && unresolved == reachedResolution) {
            throw new IllegalStateException("no SD target resolved; refusing to produce an empty requisition");
        }
        return requisition.build();
    }

    /** Returns the label value, or null when the label name is null, absent or blank. */
    private static String value(Map<String, String> labels, String name) {
        if (name == null) {
            return null;
        }
        final String v = labels.get(name);
        return (v == null || v.isBlank()) ? null : v;
    }
}
