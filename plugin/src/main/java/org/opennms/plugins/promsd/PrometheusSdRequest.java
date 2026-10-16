/*
 * Copyright 2026 The OpenNMS Group, Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Created by Ronny Trommer <ronny@opennms.com>
 */
package org.opennms.plugins.promsd;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import org.opennms.integration.api.v1.requisition.RequisitionRequest;
import org.opennms.plugins.promsd.mapping.MappingOptions;

public record PrometheusSdRequest(URI url, String credentialsAlias, Path truststore, MappingOptions options)
        implements RequisitionRequest {

    static PrometheusSdRequest fromParameters(Map<String, String> p) {
        String url = value(p, "url");
        if (url == null) {
            throw new IllegalArgumentException("missing required parameter: url");
        }
        String foreignSource = value(p, "foreignSource");
        if (foreignSource == null) {
            throw new IllegalArgumentException("missing required parameter: foreignSource");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (java.net.URISyntaxException e) {
            // do not echo the input: it may contain credentials
            throw new IllegalArgumentException("invalid url");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("url must use http or https");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("url must have a host");
        }
        if (uri.getRawUserInfo() != null
                || (uri.getRawAuthority() != null && uri.getRawAuthority().contains("@"))) {
            throw new IllegalArgumentException(
                    "url must not contain credentials, use the credentials parameter with an SCV alias");
        }
        String truststore = value(p, "truststore");
        return new PrometheusSdRequest(
                uri,
                value(p, "credentials"),
                truststore == null ? null : Path.of(truststore),
                new MappingOptions(
                        foreignSource,
                        value(p, "foreignIdLabel"),
                        value(p, "nodeLabelLabel"),
                        value(p, "categoryLabel"),
                        value(p, "nodeLocation")));
    }

    private static String value(Map<String, String> p, String key) {
        String v = p.get(key);
        return v == null || v.isBlank() ? null : v.trim();
    }
}
