/*
 * Copyright 2026 The OpenNMS Group, Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Created by Ronny Trommer <ronny@opennms.com>
 */
package org.opennms.plugins.promsd.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opennms.integration.api.v1.scv.immutables.ImmutableCredentials;

class SdFetcherTest {

    private static final Path TLS = Paths.get("src/test/resources/tls");

    private HttpServer server;
    private final AtomicReference<String> auth = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String body = "[]";
    private volatile long sleepMs = 0;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private int start(boolean tls) throws Exception {
        InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0);
        if (tls) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = java.nio.file.Files.newInputStream(TLS.resolve("server.p12"))) {
                ks.load(in, "changeit".toCharArray());
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, "changeit".toCharArray());
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, null);
            HttpsServer https = HttpsServer.create(addr, 0);
            https.setHttpsConfigurator(new HttpsConfigurator(ctx));
            server = https;
        } else {
            server = HttpServer.create(addr, 0);
        }
        server.createContext("/", ex -> {
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
            if (b.length > 0) {
                ex.getResponseBody().write(b);
            }
            ex.close();
        });
        server.start();
        return server.getAddress().getPort();
    }

    @Test
    void plainHttpReturnsBody() throws Exception {
        int port = start(false);
        byte[] out = new SdFetcher().fetch(URI.create("http://localhost:" + port + "/"), null, null);
        assertArrayEquals("[]".getBytes(StandardCharsets.UTF_8), out);
    }

    @Test
    void basicAuthHeader() throws Exception {
        int port = start(false);
        new SdFetcher().fetch(URI.create("http://localhost:" + port + "/"), new ImmutableCredentials("u", "p"), null);
        assertEquals("Basic dTpw", auth.get());
    }

    @Test
    void bearerAuthHeader() throws Exception {
        int port = start(false);
        new SdFetcher().fetch(URI.create("http://localhost:" + port + "/"), new ImmutableCredentials("", "tok"), null);
        assertEquals("Bearer tok", auth.get());
    }

    @Test
    void noCredentialsNoHeader() throws Exception {
        int port = start(false);
        new SdFetcher().fetch(URI.create("http://localhost:" + port + "/"), null, null);
        assertNull(auth.get());
    }

    @Test
    void emptyCredentialsRejected() {
        assertThrows(SdFetchException.class, () -> new SdFetcher()
                .fetch(URI.create("http://localhost:1/"), new ImmutableCredentials("", ""), null));
    }

    @Test
    void httpsWithPemTruststore() throws Exception {
        int port = start(true);
        byte[] out = new SdFetcher().fetch(URI.create("https://localhost:" + port + "/"), null, TLS.resolve("ca.pem"));
        assertEquals("[]", new String(out, StandardCharsets.UTF_8));
    }

    @Test
    void httpsWithJksTruststore() throws Exception {
        int port = start(true);
        byte[] out = new SdFetcher().fetch(URI.create("https://localhost:" + port + "/"), null, TLS.resolve("ca.jks"));
        assertEquals("[]", new String(out, StandardCharsets.UTF_8));
    }

    @Test
    void httpsWithoutTruststoreFails() throws Exception {
        int port = start(true);
        assertThrows(SdFetchException.class, () -> new SdFetcher()
                .fetch(URI.create("https://localhost:" + port + "/"), null, null));
    }

    @Test
    void hostnameStillVerified() throws Exception {
        int port = start(true);
        SdFetchException e = assertThrows(SdFetchException.class, () -> new SdFetcher()
                .fetch(URI.create("https://127.0.0.1:" + port + "/"), null, TLS.resolve("ca.pem")));
        boolean tlsCause = false;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SSLHandshakeException
                    || t instanceof SSLPeerUnverifiedException
                    || t instanceof CertificateException) {
                tlsCause = true;
            }
        }
        assertTrue(tlsCause, "expected a TLS verification failure in the cause chain: " + e);
    }

    @Test
    void controlCharInTokenRejected() {
        SdFetchException e = assertThrows(SdFetchException.class, () -> new SdFetcher()
                .fetch(URI.create("http://localhost:1/"), new ImmutableCredentials("", "tok\n"), null));
        assertFalse(e.getMessage().contains("tok"));
        SdFetchException b = assertThrows(SdFetchException.class, () -> new SdFetcher()
                .fetch(URI.create("http://localhost:1/"), new ImmutableCredentials("u", "p\r"), null));
        assertFalse(b.getMessage().contains("p\r"));
    }

    @Test
    void nonLatin1BearerTokenRejected() {
        SdFetchException e = assertThrows(SdFetchException.class, () -> new SdFetcher()
                .fetch(URI.create("http://localhost:1/"), new ImmutableCredentials("", "tok€"), null));
        assertFalse(e.getMessage().contains("tok"));
    }

    @Test
    void non2xxFails() throws Exception {
        status = 503;
        int port = start(false);
        SdFetchException e = assertThrows(SdFetchException.class, () -> new SdFetcher()
                .fetch(URI.create("http://localhost:" + port + "/"), null, null));
        assertEquals(true, e.getMessage().contains("503"));
    }

    @Test
    void failsOnTimeout() throws Exception {
        sleepMs = 2000;
        int port = start(false);
        assertThrows(SdFetchException.class, () -> new SdFetcher(Duration.ofSeconds(1), Duration.ofMillis(300))
                .fetch(URI.create("http://localhost:" + port + "/"), null, null));
    }

    @Test
    void secretNotInMessage() throws Exception {
        status = 401;
        int port = start(false);
        SdFetchException e = assertThrows(SdFetchException.class, () -> new SdFetcher()
                .fetch(URI.create("http://localhost:" + port + "/"), new ImmutableCredentials("u", "s3cret"), null));
        assertFalse(e.getMessage().contains("s3cret"));
    }
}
