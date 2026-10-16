#!/bin/sh
#
# Copyright 2026 The OpenNMS Group, Inc.
# SPDX-License-Identifier: Apache-2.0
#
# Created by Ronny Trommer <ronny@opennms.com>
#
# Generates the test CA and server certificate. Outputs are committed,
# so the build does not need keytool. Safe to re-run (overwrites outputs).
set -eu
cd "$(dirname "$0")"
PASS=changeit
rm -f ca.pem ca.jks server.p12 .ca.p12 .server.csr .server.pem

keytool -genkeypair -alias ca -keyalg RSA -keysize 2048 -validity 36500 \
  -dname "CN=promsd-test-ca" -ext bc:c -ext ku:c=keyCertSign,cRLSign \
  -keystore .ca.p12 -storetype PKCS12 -storepass $PASS
keytool -exportcert -rfc -alias ca -keystore .ca.p12 -storepass $PASS -file ca.pem
keytool -importcert -noprompt -alias ca -file ca.pem \
  -keystore ca.jks -storetype JKS -storepass $PASS

keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -validity 36500 \
  -dname "CN=localhost" -ext "SAN=dns:localhost" \
  -keystore server.p12 -storetype PKCS12 -storepass $PASS
keytool -certreq -alias server -keystore server.p12 -storepass $PASS \
  -ext "SAN=dns:localhost" -file .server.csr
keytool -gencert -alias ca -keystore .ca.p12 -storepass $PASS -validity 36500 \
  -ext "SAN=dns:localhost" -rfc -infile .server.csr -outfile .server.pem
cat ca.pem .server.pem > .chain.pem
keytool -importcert -noprompt -alias server -file .chain.pem \
  -keystore server.p12 -storepass $PASS
rm -f .ca.p12 .server.csr .server.pem .chain.pem
