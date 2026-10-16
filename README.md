# OpenNMS Prometheus HTTP SD plugin

## What it does

This plugin turns a Prometheus HTTP service discovery (SD) endpoint into an OpenNMS requisition.
Provisiond fetches the endpoint on every import and returns one node per target.
When a target disappears from the endpoint, its node is removed on the next import.
It works with any endpoint that follows the Prometheus HTTP SD format, such as the nl6 SNMP simulator.
It targets OpenNMS Horizon 36.0.5.

## Install

Build the KAR with `make build`.
Copy `assembly/kar/target/opennms-prometheus-sd-plugin.kar` to `$OPENNMS_HOME/deploy/`.
Then run this in the Karaf shell on port 8101:

```
feature:install opennms-plugins-prometheus-sd
```

## Requisition URL

Use this as the import URL of a requisition:

```
requisition://prometheus-sd?url=<sd-endpoint>&foreignSource=<name>[&option=value...]
```

| Parameter | Required | Meaning |
|---|---|---|
| `url` | yes | The HTTP SD endpoint. `http` or `https`. |
| `foreignSource` | yes | Foreign source name of the produced requisition. |
| `foreignIdLabel` | no | SD label whose value becomes the foreign ID. Default: the target host, sanitized. |
| `nodeLabelLabel` | no | SD label whose value becomes the node label. Default: the target host. For nl6 use `__meta_nl6_sys_name`. |
| `categoryLabel` | no | SD label whose value is added as a surveillance category. Default: none. |
| `nodeLocation` | no | Monitoring location for all nodes. Default: the Default location. |
| `credentials` | no | Secure Credentials Vault alias. |
| `truststore` | no | Path on the OpenNMS server to a PEM (`.pem`, `.crt`, `.cer`) or JKS file with the CA certificates to trust. Default: the JVM trust store. |

Each node gets one SNMP primary interface with the target host, port stripped.
The foreign ID defaults to the host, with every character outside `[A-Za-z0-9._-]` replaced by `_`.
Every label of the target group is copied to node metadata in the context `prometheus`, with the full label name as key.
A missing label falls back to the default for that field.
A failed fetch, a non-2xx response or malformed JSON fails the import and leaves existing nodes alone.
A valid empty array `[]` produces an empty requisition and removes all nodes of that foreign source.

## Credentials

Keep secrets out of the URL.
Store them in the Secure Credentials Vault from the Karaf shell:

```
scv:set <alias> <user> <password>
```

Then pass `credentials=<alias>`.
A user and a password mean Basic auth.
A blank user means `Authorization: Bearer <password>`.
An unknown alias fails the import.

## Example: nl6

```
requisition://prometheus-sd?url=https://nl6.example:8080/api/v1/prometheus/sd&foreignSource=nl6&nodeLabelLabel=__meta_nl6_sys_name&credentials=nl6-prod&truststore=/opt/opennms/etc/nl6-ca.pem
```

To trigger an import without waiting for the schedule, post a `reloadImport` event with the URL as its `url` parameter:

```
curl -u admin:admin -H 'Content-Type: application/xml' -X POST http://localhost:8980/opennms/rest/events -d '<event>
  <uei>uei.opennms.org/internal/importer/reloadImport</uei>
  <source>manual</source>
  <parms><parm><parmName>url</parmName><value><![CDATA[requisition://prometheus-sd?url=...&foreignSource=nl6]]></value></parm></parms>
</event>'
```

## Scheduling the import

Horizon 36.0.5 does not offer plugin requisition providers in the "External Source" list of the External Requisitions page.
The list is fixed in the OpenNMS UI.
Add the schedule through the provisiond configuration in the Config Manager REST API instead.
The commands need `curl` and `jq`, and an OpenNMS user with the admin role.

```
ONMS=http://localhost:8980/opennms
AUTH=admin:admin
URL='requisition://prometheus-sd?url=https://nl6.example:8080/api/v1/prometheus/sd&foreignSource=nl6&nodeLabelLabel=__meta_nl6_sys_name'

curl -su "$AUTH" "$ONMS/rest/cm/provisiond/default" \
  | jq --arg url "$URL" '."requisition-def" += [{"import-name": "nl6", "import-url-resource": $url, "cron-schedule": "0 0 * * * ?", "rescan-existing": "true"}]' \
  | curl -su "$AUTH" -X PUT -H 'Content-Type: application/json' -d @- "$ONMS/rest/cm/provisiond/default"
```

- `import-name` is the name shown in the UI.
  It must be unique.
- `cron-schedule` is a Quartz cron expression with a seconds field.
  `0 0 * * * ?` imports at the top of every hour.
- `rescan-existing` is `true` (scan all nodes), `dbonly` (scan added nodes only) or `false` (no scanning).

Provisiond picks up the schedule without a restart.
The requisition then appears on the External Requisitions page with its URL, schedule and rescan behavior.
OpenNMS disables the edit button for `requisition://` URLs, so change the entry through the same API:

```
curl -su "$AUTH" "$ONMS/rest/cm/provisiond/default" \
  | jq '(."requisition-def"[] | select(."import-name" == "nl6") | ."cron-schedule") = "0 */15 * * * ?"' \
  | curl -su "$AUTH" -X PUT -H 'Content-Type: application/json' -d @- "$ONMS/rest/cm/provisiond/default"
```

Remove it with the delete button on the page, or through the API:

```
curl -su "$AUTH" "$ONMS/rest/cm/provisiond/default" \
  | jq '."requisition-def" |= map(select(."import-name" != "nl6"))' \
  | curl -su "$AUTH" -X PUT -H 'Content-Type: application/json' -d @- "$ONMS/rest/cm/provisiond/default"
```

Removing the schedule stops future imports.
The nodes of the foreign source stay in OpenNMS.

## Limitations

- The `url` value must not contain a query string, `&` or `=`, even percent-encoded.
  OpenNMS URL-decodes the whole query before splitting it on `&` and `=`.
- Do not use `location` or `ttl` as parameters.
  OpenNMS reserves them and uses `location` to run the import on a Minion.
  Use `nodeLocation`.
- The `url` must not carry credentials.
- One device per IP.
  Two targets with the same host and different ports are one device.
  The first is kept and the rest are skipped with a warning.
- No mTLS.
- Runs on the OpenNMS core only, not on a Minion.
- The target port is ignored.
  OpenNMS uses its own SNMP configuration per IP.

## Smoke test

`smoke/compose.yml` starts PostgreSQL, Horizon 36.0.5 with the KAR mounted, and `sd-stub`.
The stub serves `smoke/sd/api/v1/prometheus/sd`, an nl6-shaped response with three targets, at `http://sd-stub:8000/api/v1/prometheus/sd`.
It replaces a live nl6, which needs host networking and `/dev/net/tun`.

```
make build
docker compose -f smoke/compose.yml up -d
```

Startup takes several minutes.
Poll `curl -su admin:admin http://localhost:8980/opennms/rest/info` until it returns `"version":"36.0.5"`.
The Karaf shell is on `ssh -p 8101 admin@localhost` (password `admin`).
Install the feature, then import with the nl6 example URL, using `url=http://sd-stub:8000/api/v1/prometheus/sd` and `nodeLabelLabel=__meta_nl6_sys_name`.
Check the nodes with `curl -su admin:admin 'http://localhost:8980/opennms/rest/nodes?foreignSource=nl6'`.
To test removal, delete a target from the stub file and import again.
To test failure, run `docker compose -f smoke/compose.yml stop sd-stub` and import again.
The nodes must stay.
Tear down with `docker compose -f smoke/compose.yml down -v`.

## Build

```
make build
```

## License

Apache-2.0.
