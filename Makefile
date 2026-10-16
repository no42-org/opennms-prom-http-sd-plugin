# Copyright 2026 Ronny Trommer <ronny@no42.org>
# SPDX-License-Identifier: Apache-2.0
#
# ci-standard root Makefile. Identical across repos except the ECOSYSTEMS and NAME lines.
# Public targets are declared here with a ## description and implemented in mk/<ecosystem>.mk.
# Every target is a double-colon rule so several ecosystems can stack recipes on one name.

# One or more of: go rust maven gradle python node container
ECOSYSTEMS := maven

.DEFAULT_GOAL := help
SHELL := /bin/sh
# The artifact name. Set it when it differs from the directory name.
NAME ?= opennms-prometheus-sd-plugin
ifeq ($(origin VERSION),undefined)
VERSION := $(or $(shell git describe --tags --match 'v*' --always --dirty 2>/dev/null | sed 's/^v//'),dev)
endif
COMMIT_RANGE ?= origin/main..HEAD
export NAME VERSION

.PHONY: help setup fmt fmt-check lint test build check commitlint audit sast release-build image image-scan sbom clean checksums cache-info

help: ## List public targets
	@sed -nE 's/^([a-z-]+):+[^#]*## (.*)$$/\1|\2/p' $(firstword $(MAKEFILE_LIST)) | awk -F'|' '{printf "%-14s %s\n", $$1, $$2}'

setup:: ## Install pinned toolchain and dependencies
	@command -v mise >/dev/null 2>&1 || { echo "error: mise not found; install it first (https://mise.run)" >&2; exit 1; }
	mise install
fmt:: ## Format code in place
fmt-check:: ## Fail if fmt would change anything
lint:: ## Static analysis, workflow lint, secret scan
test:: ## Run tests, JUnit XML in build/reports/
build:: ## Build for the host platform into build/
check:: fmt-check lint test build ## PR gate: fmt-check lint test build
commitlint:: ## Validate commits in COMMIT_RANGE
audit:: ## Dependency vulnerability check
sast:: ## Static security analysis
release-build:: ## Cross-platform artifacts and checksums into dist/
	@rm -rf dist
image:: ## Build the container image
image-scan:: ## Scan the container image
sbom:: ## CycloneDX SBOM of the tree into dist/
clean:: ## Remove build/ and dist/

include $(foreach e,$(ECOSYSTEMS),mk/$(e).mk) mk/common.mk

# Multi-module Karaf build: the shipped artifact is the KAR.
MAVEN_ARTIFACT := assembly/kar/target/opennms-prometheus-sd-plugin.kar
