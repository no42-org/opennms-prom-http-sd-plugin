# Copyright 2026 Ronny Trommer <ronny@no42.org>
# SPDX-License-Identifier: Apache-2.0
#
# Ecosystem-neutral recipes. Included last by the root Makefile so its release-build
# step (checksums) and its lint steps run after every ecosystem's.

WORKFLOW_FILES := $(wildcard $(foreach d,.github/workflows .forgejo/workflows,$(d)/*.yml $(d)/*.yaml))
CACHE_PATHS +=
LOCKFILES +=

# $(call require,tool): fail with a pointer to make setup when a tool is missing.
require = command -v $(1) >/dev/null 2>&1 || { echo "error: $(1) not found; run 'make setup'" >&2; exit 1; }

lint::
ifneq ($(WORKFLOW_FILES),)
	@$(call require,actionlint)
	@$(call require,zizmor)
	actionlint $(WORKFLOW_FILES)
	zizmor --min-severity medium $(WORKFLOW_FILES)
endif
	@$(call require,gitleaks)
	gitleaks dir . --no-banner --redact --exit-code 1

commitlint::
	sh mk/commitlint.sh $(COMMIT_RANGE)

sast::
	@$(call require,semgrep)
	semgrep scan --config p/default --error --metrics=off --quiet .

release-build::
	@$(MAKE) -s checksums

# SBOM: dist/<name>-<version>.cdx.json. An ecosystem with a better source (rust: cargo-cyclonedx, which
# knows licences and authors) writes the file in its own sbom recipe, which runs first; syft covers the rest.
# The supplier is the owner in the origin URL, because no generator fills it; set SBOM_SUPPLIER to override.
SBOM_FILE = dist/$(NAME)-$(VERSION).cdx.json
SBOM_SUPPLIER ?= $(shell git remote get-url origin 2>/dev/null | sed -E 's|\.git$$||; s|.*[:/]([^/]+)/[^/]+$$|\1|')

ifeq ($(filter rust,$(ECOSYSTEMS)),)
sbom::
	@$(call require,syft)
	@mkdir -p dist
	syft scan "dir:." --source-name "$(NAME)" --source-version "$(VERSION)" -o "cyclonedx-json=$(SBOM_FILE)"
endif

sbom::
	@if [ -n "$(SBOM_SUPPLIER)" ] && command -v python3 >/dev/null 2>&1; then \
	  python3 -c 'import json, sys; p, s = sys.argv[1:]; d = json.load(open(p)); c = d.setdefault("metadata", {}).setdefault("component", {}); c.setdefault("supplier", {"name": s}); json.dump(d, open(p, "w"), indent=2)' "$(SBOM_FILE)" "$(SBOM_SUPPLIER)"; \
	else echo "sbom: supplier not set (no origin remote or no python3)"; fi

# Recompute dist/checksums.txt over every file in dist/ except itself.
# The release pipeline calls this again after adding the SBOM and the SBOM report.
checksums:
	@if [ -d dist ] && [ -n "$$(ls -A dist 2>/dev/null)" ]; then \
	  cd dist && find . -type f ! -name checksums.txt | sed 's|^\./||' | sort | xargs shasum -a 256 > ../checksums.tmp && mv ../checksums.tmp checksums.txt; \
	fi

# For CI cache steps: the paths to persist and a key derived from every lockfile present.
cache-info:
	@echo "paths=$(strip $(CACHE_PATHS))"
	@echo "key=$$(cat $(wildcard $(LOCKFILES)) /dev/null | shasum -a 256 | cut -c1-16)"

clean::
	rm -rf build dist

print-%:
	@echo $($*)
