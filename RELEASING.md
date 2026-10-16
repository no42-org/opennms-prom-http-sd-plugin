# Releasing

Versions follow [SemVer](https://semver.org/), derived from Conventional Commits.

## Cutting a release

1. Ensure CI on `main` is green.
2. Open a PR bumping the version in the package manifest to `X.Y.Z` (`chore(release): vX.Y.Z`) and merge it.
3. Tag the merged bump commit: `git tag -a vX.Y.Z -m "vX.Y.Z" && git push origin vX.Y.Z`.
4. The `release` workflow runs the gates, builds the artifacts and an SBOM, signs them, and pushes container images to `ghcr.io/no42-org/opennms-prom-http-sd-plugin`.
   Stable tags get the image tags `X.Y.Z`, `X.Y` and `latest`.
   It then creates a **draft** GitHub Release with these files attached:
   - `opennms-prometheus-sd-plugin_<version>_<os>_<arch>` binaries
   - `checksums.txt`
   - `checksums.txt.sigstore.json`
   - `opennms-prometheus-sd-plugin-<version>.cdx.json`
   - `opennms-prometheus-sd-plugin-<version>-sbom.html`
5. Add curated notes (TL;DR, breaking changes, features, fixes, security and performance, maintenance) and publish: `gh release edit vX.Y.Z --notes-file notes.md --draft=false`.

Prerelease tags (`vX.Y.Z-rc1`) are marked as prereleases and never move `latest`.

Every push to `main` publishes a preview image at `ghcr.io/no42-org/opennms-prom-http-sd-plugin:rc`.
The `rc` tag is mutable and overwritten on each build.
The same push refreshes the rolling `preview` prerelease with binaries from the latest main commit.

## Signing and provenance

Images and `checksums.txt` are signed with cosign keyless signing, using the GitHub OIDC identity of the `release` workflow.
Build provenance is attested with `actions/attest-build-provenance`.

## Verifying signatures

    cosign verify ghcr.io/no42-org/opennms-prom-http-sd-plugin:X.Y.Z \
      --certificate-identity-regexp 'https://github.com/no42-org/opennms-prom-http-sd-plugin/\.github/workflows/release\.yml@.*' \
      --certificate-oidc-issuer https://token.actions.githubusercontent.com

    cosign verify-blob --bundle checksums.txt.sigstore.json \
      --certificate-identity-regexp 'https://github.com/no42-org/opennms-prom-http-sd-plugin/\.github/workflows/release\.yml@.*' \
      --certificate-oidc-issuer https://token.actions.githubusercontent.com \
      checksums.txt

    gh attestation verify <file> --repo no42-org/opennms-prom-http-sd-plugin
