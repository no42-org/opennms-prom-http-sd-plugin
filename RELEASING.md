# Releasing

Versions follow [SemVer](https://semver.org/), derived from Conventional Commits.
The release artifact is a Karaf KAR for OpenNMS Horizon 36.0.5.
There is no container image.

## Cutting a release

1. Ensure CI on `main` is green.
2. Open a PR that sets the version in all poms and merge it:

   ```
   ./mvnw versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false
   git commit -s -am "chore(release): vX.Y.Z"
   ```

   The tag only names the files in `dist/`.
   The feature and bundle versions inside the KAR come from the poms, so skipping this step ships a `-SNAPSHOT` inside a release.
3. Tag the merged bump commit: `git tag -a vX.Y.Z -m "vX.Y.Z" && git push origin vX.Y.Z`.
4. The `release` workflow runs the gates, builds the KAR, writes an SBOM, signs `checksums.txt`, attests build provenance and creates a **draft** GitHub Release with these files:
   - `opennms-prometheus-sd-plugin_X.Y.Z.kar`
   - `checksums.txt`
   - `checksums.txt.sigstore.json`
   - `opennms-prometheus-sd-plugin-X.Y.Z.cdx.json`
   - `opennms-prometheus-sd-plugin-X.Y.Z-sbom.html`
5. Add curated notes (TL;DR, breaking changes, features, fixes, security and performance, maintenance) and publish: `gh release edit vX.Y.Z --notes-file notes.md --draft=false`.
6. Open a PR that sets the next development version, for example `./mvnw versions:set -DnewVersion=X.Y.(Z+1)-SNAPSHOT -DgenerateBackupPoms=false`.

Prerelease tags (`vX.Y.Z-rc1`) are marked as prereleases.

Every push to `main` refreshes the rolling `preview` prerelease with the KAR built from the latest main commit.
It is unstable and overwritten on each push.

## Signing and provenance

`checksums.txt` is signed with cosign keyless signing, using the GitHub OIDC identity of the `release` workflow.
The signature covers the KAR through its checksum.
Build provenance for every file in the release is attested with `actions/attest-build-provenance`.

## Verifying a release

```
cosign verify-blob --bundle checksums.txt.sigstore.json \
  --certificate-identity-regexp 'https://github.com/no42-org/opennms-prom-http-sd-plugin/\.github/workflows/release\.yml@.*' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  checksums.txt
shasum -a 256 -c checksums.txt --ignore-missing
gh attestation verify opennms-prometheus-sd-plugin_X.Y.Z.kar --repo no42-org/opennms-prom-http-sd-plugin
```

## Installing a release

On the OpenNMS Horizon 36.0.5 core server:

```
cp opennms-prometheus-sd-plugin_X.Y.Z.kar "$OPENNMS_HOME/deploy/"
```

The KAR registers the feature without starting it.
Install it in the Karaf shell (`ssh -p 8101 admin@localhost`):

```
feature:install opennms-plugins-prometheus-sd
feature:list | grep prometheus-sd
```

The feature shows as `Started`.
To keep it installed across restarts, add it to the boot features:

```
echo opennms-plugins-prometheus-sd > "$OPENNMS_HOME/etc/featuresBoot.d/prometheus-sd.boot"
```

To upgrade, replace the KAR in `deploy/` and restart OpenNMS.
The requisition URL and its parameters are documented in the [README](README.md).
