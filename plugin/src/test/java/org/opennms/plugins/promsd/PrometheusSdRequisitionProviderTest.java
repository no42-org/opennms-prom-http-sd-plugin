/*
 * Copyright 2026 The OpenNMS Group, Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Created by Ronny Trommer <ronny@opennms.com>
 */
package org.opennms.plugins.promsd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opennms.integration.api.v1.config.requisition.Requisition;
import org.opennms.integration.api.v1.scv.Credentials;
import org.opennms.integration.api.v1.scv.SecureCredentialsVault;
import org.opennms.integration.api.v1.scv.immutables.ImmutableCredentials;
import org.osgi.service.blueprint.container.ServiceUnavailableException;

class PrometheusSdRequisitionProviderTest {

    private HttpServer server;
    private int status = 200;
    private byte[] body;
    private final AtomicReference<String> authHeader = new AtomicReference<>();
    private final Map<String, Credentials> secrets = new HashMap<>();
    private PrometheusSdRequisitionProvider provider;

    private final SecureCredentialsVault vault = new SecureCredentialsVault() {
        @Override
        public Credentials getCredentials(String alias) {
            return secrets.get(alias);
        }

        @Override
        public Set<String> getAliases() {
            return secrets.keySet();
        }

        @Override
        public void setCredentials(String alias, Credentials credentials) {
            secrets.put(alias, credentials);
        }

        @Override
        public void deleteCredentials(String alias) {
            secrets.remove(alias);
        }
    };

    @BeforeEach
    void start() throws Exception {
        body = Files.readAllBytes(Path.of("src/test/resources/nl6-sd.json"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sd", ex -> {
            authHeader.set(ex.getRequestHeaders().getFirst("Authorization"));
            ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                ex.getResponseBody().write(body);
            }
            ex.close();
        });
        server.start();
        provider = new PrometheusSdRequisitionProvider(vault);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private Map<String, String> params(String... kv) {
        Map<String, String> m = new HashMap<>();
        m.put("url", "http://127.0.0.1:" + server.getAddress().getPort() + "/sd");
        m.put("foreignSource", "nl6");
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("params takes key/value pairs");
        }
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void typeIsPrometheusSd() {
        assertEquals("prometheus-sd", new PrometheusSdRequisitionProvider(null).getType());
    }

    @Test
    void endToEndNl6() {
        Requisition r = provider.getRequisition(provider.getRequest(params("nodeLabelLabel", "__meta_nl6_sys_name")));
        assertEquals("nl6", r.getForeignSource());
        assertEquals(2, r.getNodes().size());
        assertEquals("core-rtr-01", r.getNodes().get(0).getNodeLabel());
    }

    @Test
    void requiresUrlAndForeignSource() {
        Map<String, String> noUrl = params();
        noUrl.remove("url");
        Map<String, String> noFs = params();
        noFs.remove("foreignSource");
        assertThrows(IllegalArgumentException.class, () -> provider.getRequest(noUrl));
        assertThrows(IllegalArgumentException.class, () -> provider.getRequest(noFs));
        assertThrows(IllegalArgumentException.class, () -> provider.getRequest(params("url", "ftp://x")));
    }

    @Test
    void rejectsUserInfoInUrl() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class, () -> provider.getRequest(params("url", "https://user:pass@host/sd")));
        assertFalse(e.getMessage().contains("pass"));
        for (String bad : new String[] {"https://user:pa@ss@host/sd", "https://u:p/ss@host/sd"}) {
            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> provider.getRequest(params("url", bad)));
            assertFalse(ex.getMessage().contains("pa@ss"));
            assertFalse(ex.getMessage().contains("user"));
            assertFalse(ex.getMessage().contains("p/ss"));
        }
    }

    @Test
    void rejectsUrlWithoutHost() {
        assertThrows(IllegalArgumentException.class, () -> provider.getRequest(params("url", "http:///sd")));
    }

    @Test
    void blankOptionalParamsIgnored() {
        Requisition r = provider.getRequisition(provider.getRequest(params("categoryLabel", "")));
        r.getNodes().forEach(n -> assertTrue(n.getCategories().isEmpty()));
    }

    @Test
    void unknownAliasFails() {
        IllegalStateException e = assertThrows(
                IllegalStateException.class,
                () -> provider.getRequisition(provider.getRequest(params("credentials", "nope"))));
        assertTrue(e.getMessage().contains("nope"));
    }

    @Test
    void vaultUnavailableFails() {
        SecureCredentialsVault down = new SecureCredentialsVault() {
            @Override
            public Credentials getCredentials(String alias) {
                throw new ServiceUnavailableException("gone", SecureCredentialsVault.class.getName(), null);
            }

            @Override
            public Set<String> getAliases() {
                return Set.of();
            }

            @Override
            public void setCredentials(String alias, Credentials credentials) {}

            @Override
            public void deleteCredentials(String alias) {}
        };
        PrometheusSdRequisitionProvider p = new PrometheusSdRequisitionProvider(down);
        IllegalStateException e = assertThrows(
                IllegalStateException.class, () -> p.getRequisition(p.getRequest(params("credentials", "prod"))));
        assertEquals("Secure Credentials Vault unavailable", e.getMessage());
    }

    @Test
    void aliasCredentialsSent() {
        secrets.put("prod", new ImmutableCredentials("u", "p"));
        provider.getRequisition(provider.getRequest(params("credentials", "prod")));
        assertEquals("Basic dTpw", authHeader.get());
    }

    @Test
    void fetchErrorFailsImport() {
        status = 500;
        assertThrows(IllegalStateException.class, () -> provider.getRequisition(provider.getRequest(params())));
    }

    @Test
    void malformedBodyFailsImport() {
        body = "<html/>".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, () -> provider.getRequisition(provider.getRequest(params())));
    }

    @Test
    void emptyArrayGivesEmptyRequisition() {
        body = "[]".getBytes(StandardCharsets.UTF_8);
        Requisition r = provider.getRequisition(provider.getRequest(params()));
        assertEquals(0, r.getNodes().size());
        assertEquals("nl6", r.getForeignSource());
    }

    @Test
    void marshalRequestExplainsNodeLocation() {
        UnsupportedOperationException e =
                assertThrows(UnsupportedOperationException.class, () -> provider.marshalRequest(null));
        assertTrue(e.getMessage().contains("nodeLocation"));
    }
}
