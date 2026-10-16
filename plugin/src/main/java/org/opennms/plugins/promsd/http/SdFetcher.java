/*
 * Copyright 2026 The OpenNMS Group, Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Created by Ronny Trommer <ronny@opennms.com>
 */
package org.opennms.plugins.promsd.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import org.opennms.integration.api.v1.scv.Credentials;

public final class SdFetcher {

    private final Duration connectTimeout;
    private final Duration requestTimeout;

    public SdFetcher() {
        this(Duration.ofSeconds(10), Duration.ofSeconds(30));
    }

    SdFetcher(Duration connectTimeout, Duration requestTimeout) {
        this.connectTimeout = connectTimeout;
        this.requestTimeout = requestTimeout;
    }

    public byte[] fetch(URI url, Credentials credentials, Path truststore) throws SdFetchException {
        String authorization = authorization(credentials);
        HttpClient.Builder cb = HttpClient.newBuilder().connectTimeout(connectTimeout);
        if (truststore != null) {
            cb.sslContext(TrustStores.sslContextFor(truststore));
        }
        HttpRequest.Builder rb = HttpRequest.newBuilder(url)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET();
        if (authorization != null) {
            rb.header("Authorization", authorization);
        }
        try (HttpClient client = cb.build()) {
            HttpResponse<byte[]> resp = client.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
            int code = resp.statusCode();
            if (code < 200 || code > 299) {
                throw new SdFetchException("fetching " + url + " failed with HTTP status " + code);
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SdFetchException("interrupted while fetching " + url, e);
        } catch (IOException e) {
            throw new SdFetchException("fetching " + url + " failed: " + e, e);
        }
    }

    private static String authorization(Credentials c) throws SdFetchException {
        if (c == null) {
            return null;
        }
        String user = c.getUsername();
        String pass = c.getPassword();
        if (hasControlChar(user) || hasControlChar(pass)) {
            throw new SdFetchException("invalid characters in credentials");
        }
        boolean noUser = user == null || user.isBlank();
        boolean noPass = pass == null || pass.isBlank();
        if (noUser && noPass) {
            throw new SdFetchException("credentials alias has no username or password");
        }
        if (noUser) {
            // header values must be Latin-1; the JDK echoes rejected values in its exception
            if (pass.chars().anyMatch(ch -> ch > 0xFF)) {
                throw new SdFetchException("invalid characters in credentials");
            }
            return "Bearer " + pass;
        }
        String raw = user + ":" + (pass == null ? "" : pass);
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean hasControlChar(String s) {
        return s != null && s.chars().anyMatch(ch -> ch < 0x20 || ch == 0x7F);
    }
}
