/*
 * Copyright 2026 The OpenNMS Group, Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Created by Ronny Trommer <ronny@opennms.com>
 */

package org.opennms.plugins.promsd.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.opennms.integration.api.v1.config.requisition.Requisition;
import org.opennms.integration.api.v1.config.requisition.RequisitionNode;
import org.opennms.integration.api.v1.config.requisition.SnmpPrimaryType;
import org.opennms.integration.api.v1.config.requisition.immutables.ImmutableRequisitionMetaData;
import org.opennms.plugins.promsd.sd.SdParser;
import org.opennms.plugins.promsd.sd.SdTargetGroup;

class RequisitionMapperTest {

    private static final HostResolver FAKE = host -> {
        if (host.equals("unresolvable.example")) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(host));
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    };

    private final RequisitionMapper mapper = new RequisitionMapper(FAKE);

    private static MappingOptions opts(String fidLabel, String nodeLabelLabel, String catLabel, String loc) {
        return new MappingOptions("nl6", fidLabel, nodeLabelLabel, catLabel, loc);
    }

    private static MappingOptions plain() {
        return opts(null, null, null, null);
    }

    private static SdTargetGroup group(Map<String, String> labels, String... targets) {
        return new SdTargetGroup(List.of(targets), labels);
    }

    @Test
    void nl6ExampleWithSysName() throws Exception {
        final byte[] json = Files.readAllBytes(Path.of("src/test/resources/nl6-sd.json"));
        final Requisition r = mapper.map(SdParser.parse(json), opts(null, "__meta_nl6_sys_name", null, null));
        assertEquals("nl6", r.getForeignSource());
        assertEquals(2, r.getNodes().size());
        final RequisitionNode n0 = r.getNodes().get(0);
        assertEquals("10.42.0.1", n0.getForeignId());
        assertEquals("core-rtr-01", n0.getNodeLabel());
        assertEquals(1, n0.getInterfaces().size());
        assertEquals("10.42.0.1", n0.getInterfaces().get(0).getIpAddress().getHostAddress());
        assertEquals(SnmpPrimaryType.PRIMARY, n0.getInterfaces().get(0).getSnmpPrimary());
        assertTrue(n0.getMetaData()
                .contains(ImmutableRequisitionMetaData.newInstance("prometheus", "__meta_nl6_snmp_port", "161")));
        assertEquals("10.42.0.2", r.getNodes().get(1).getForeignId());
    }

    @Test
    void oneNodePerTargetInGroup() {
        final Requisition r = mapper.map(List.of(group(Map.of("a", "b"), "10.0.0.1", "10.0.0.2")), plain());
        assertEquals(2, r.getNodes().size());
        for (RequisitionNode n : r.getNodes()) {
            assertTrue(n.getMetaData().contains(ImmutableRequisitionMetaData.newInstance("prometheus", "a", "b")));
        }
    }

    @Test
    void sameIpDifferentPortKeepsFirst() {
        final Requisition r = mapper.map(List.of(group(Map.of(), "10.0.0.1:161", "10.0.0.1:1161")), plain());
        assertEquals(1, r.getNodes().size());
    }

    @Test
    void ipv6ForeignId() {
        final Requisition r = mapper.map(List.of(group(Map.of(), "[2001:db8::1]:161")), plain());
        assertEquals("2001_db8__1", r.getNodes().get(0).getForeignId());
    }

    @Test
    void foreignIdLabelUsedAndSanitized() {
        final Requisition r =
                mapper.map(List.of(group(Map.of("name", "core rtr/01"), "10.0.0.1")), opts("name", null, null, null));
        assertEquals("core_rtr_01", r.getNodes().get(0).getForeignId());
    }

    @Test
    void duplicateForeignIdKeepsFirst() {
        final Requisition r =
                mapper.map(List.of(group(Map.of("name", "x"), "10.0.0.1", "10.0.0.2")), opts("name", null, null, null));
        assertEquals(1, r.getNodes().size());
    }

    @Test
    void blankForeignIdLabelFallsBackToHost() {
        final Requisition r =
                mapper.map(List.of(group(Map.of("name", ""), "10.0.0.1")), opts("name", null, null, null));
        assertEquals("10.0.0.1", r.getNodes().get(0).getForeignId());
    }

    @Test
    void missingNodeLabelLabelFallsBackToHost() {
        final Requisition r = mapper.map(List.of(group(Map.of(), "10.0.0.1")), opts(null, "nope", null, null));
        assertEquals("10.0.0.1", r.getNodes().get(0).getNodeLabel());
    }

    @Test
    void categoryFromLabel() throws Exception {
        final List<SdTargetGroup> groups =
                SdParser.parse(Files.readAllBytes(Path.of("src/test/resources/nl6-sd.json")));
        final Requisition r = mapper.map(groups, opts(null, null, "__meta_nl6_device_type", null));
        assertEquals(List.of("Cisco IOS"), r.getNodes().get(0).getCategories());
        final Requisition none = mapper.map(groups, plain());
        assertTrue(none.getNodes().get(0).getCategories().isEmpty());
    }

    @Test
    void nodeLocationApplied() {
        final Requisition r =
                mapper.map(List.of(group(Map.of(), "10.0.0.1", "10.0.0.2")), opts(null, null, null, "Lab"));
        for (RequisitionNode n : r.getNodes()) {
            assertEquals("Lab", n.getLocation());
        }
    }

    @Test
    void singleUnresolvableTargetThrows() {
        assertThrows(
                IllegalStateException.class,
                () -> mapper.map(List.of(group(Map.of(), "unresolvable.example")), plain()));
    }

    @Test
    void allUnresolvableThrows() {
        assertThrows(
                IllegalStateException.class,
                () -> mapper.map(
                        List.of(group(Map.of(), "unresolvable.example", "unresolvable.example:161")), plain()));
    }

    @Test
    void partiallyUnresolvableSkips() {
        assertEquals(
                1,
                mapper.map(List.of(group(Map.of(), "unresolvable.example", "10.0.0.1")), plain())
                        .getNodes()
                        .size());
    }

    @Test
    void emptyHostSkipped() {
        assertEquals(
                0,
                mapper.map(List.of(group(Map.of(), ":161")), plain()).getNodes().size());
    }

    @Test
    void emptyTargetsContributeNothing() {
        assertEquals(0, mapper.map(List.of(group(Map.of())), plain()).getNodes().size());
    }

    @Test
    void emptyInputGivesEmptyRequisition() {
        final Requisition r = mapper.map(List.of(), plain());
        assertEquals(0, r.getNodes().size());
        assertEquals("nl6", r.getForeignSource());
    }
}
