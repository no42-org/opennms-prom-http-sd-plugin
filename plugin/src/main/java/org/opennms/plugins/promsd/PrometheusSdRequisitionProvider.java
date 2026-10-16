/*
 * Copyright 2026 The OpenNMS Group, Inc.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Created by Ronny Trommer <ronny@opennms.com>
 */
package org.opennms.plugins.promsd;

import java.util.List;
import java.util.Map;
import org.opennms.integration.api.v1.config.requisition.Requisition;
import org.opennms.integration.api.v1.requisition.RequisitionProvider;
import org.opennms.integration.api.v1.requisition.RequisitionRequest;
import org.opennms.integration.api.v1.scv.Credentials;
import org.opennms.integration.api.v1.scv.SecureCredentialsVault;
import org.opennms.plugins.promsd.http.SdFetchException;
import org.opennms.plugins.promsd.http.SdFetcher;
import org.opennms.plugins.promsd.mapping.HostResolver;
import org.opennms.plugins.promsd.mapping.RequisitionMapper;
import org.opennms.plugins.promsd.sd.SdFormatException;
import org.opennms.plugins.promsd.sd.SdParser;
import org.opennms.plugins.promsd.sd.SdTargetGroup;
import org.osgi.service.blueprint.container.ServiceUnavailableException;

public class PrometheusSdRequisitionProvider implements RequisitionProvider {

    public static final String TYPE = "prometheus-sd";

    private static final String CORE_ONLY = "prometheus-sd runs on the OpenNMS core only. "
            + "Use nodeLocation, not location, to set the node location.";

    private final SecureCredentialsVault vault;
    private final SdFetcher fetcher;
    private final RequisitionMapper mapper;

    public PrometheusSdRequisitionProvider(SecureCredentialsVault vault) {
        this(vault, new SdFetcher(), new RequisitionMapper(HostResolver.DNS));
    }

    PrometheusSdRequisitionProvider(SecureCredentialsVault vault, SdFetcher fetcher, RequisitionMapper mapper) {
        this.vault = vault;
        this.fetcher = fetcher;
        this.mapper = mapper;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public RequisitionRequest getRequest(Map<String, String> parameters) {
        return PrometheusSdRequest.fromParameters(parameters);
    }

    @Override
    public Requisition getRequisition(RequisitionRequest request) {
        PrometheusSdRequest r = (PrometheusSdRequest) request;
        Credentials credentials = null;
        String alias = r.credentialsAlias();
        if (alias != null) {
            try {
                credentials = vault.getCredentials(alias);
            } catch (ServiceUnavailableException e) {
                throw new IllegalStateException("Secure Credentials Vault unavailable", e);
            }
            if (credentials == null) {
                throw new IllegalStateException("unknown credentials alias: " + alias);
            }
        }
        // Throw on any failure: an empty requisition would make provisiond delete nodes.
        try {
            byte[] body = fetcher.fetch(r.url(), credentials, r.truststore());
            List<SdTargetGroup> groups = SdParser.parse(body);
            return mapper.map(groups, r.options());
        } catch (SdFetchException | SdFormatException e) {
            throw new IllegalStateException("import from " + r.url() + " failed: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] marshalRequest(RequisitionRequest request) {
        throw new UnsupportedOperationException(CORE_ONLY);
    }

    @Override
    public RequisitionRequest unmarshalRequest(byte[] bytes) {
        throw new UnsupportedOperationException(CORE_ONLY);
    }
}
