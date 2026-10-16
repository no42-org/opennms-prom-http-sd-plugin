/*
 * Copyright 2026 The OpenNMS Group, Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Created by Ronny Trommer <ronny@opennms.com>
 */
package org.opennms.plugins.promsd.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Locale;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public final class TrustStores {

    private TrustStores() {}

    public static SSLContext sslContextFor(Path truststore) throws SdFetchException {
        try {
            KeyStore ks;
            String name = truststore.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".pem") || name.endsWith(".crt") || name.endsWith(".cer")) {
                ks = KeyStore.getInstance(KeyStore.getDefaultType());
                ks.load(null, null);
                try (InputStream in = Files.newInputStream(truststore)) {
                    int i = 0;
                    for (Certificate c : java.security.cert.CertificateFactory.getInstance("X.509")
                            .generateCertificates(in)) {
                        ks.setCertificateEntry("cert-" + i++, c);
                    }
                    if (i == 0) {
                        throw new SdFetchException("no certificates found in truststore " + truststore);
                    }
                }
            } else {
                ks = KeyStore.getInstance(truststore.toFile(), (char[]) null);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx;
        } catch (IOException | GeneralSecurityException e) {
            throw new SdFetchException("cannot load truststore " + truststore + ": " + e.getMessage(), e);
        }
    }
}
