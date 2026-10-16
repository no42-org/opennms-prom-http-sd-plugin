# Copyright 2026 Ronny Trommer <ronny@no42.org>
# SPDX-License-Identifier: Apache-2.0
#
# Maven. Pinned by the mvnw wrapper when present. Formatting needs spotless-maven-plugin in the pom.
# audit runs trivy fs, which scans the tree and resolves pom.xml transitively. It needs no API key.
MVN ?= $(if $(wildcard mvnw),./mvnw,mvn)
MVN_FLAGS ?= -B -ntp
CACHE_PATHS += ~/.m2/repository
LOCKFILES += pom.xml
# Runners and package managers preset JAVA_HOME to their own JDK, and the wrapper prefers it over PATH.
# Point it at the JDK pinned in .tool-versions once mise has installed it.
MISE_JAVA_HOME := $(shell mise where java 2>/dev/null)
ifneq ($(MISE_JAVA_HOME),)
export JAVA_HOME := $(MISE_JAVA_HOME)
endif
# The shipped artifact. Empty selects target/$(NAME)-*.jar of a single-module build.
# A multi-module build names its artifact as a path glob below the include line of the root Makefile,
# e.g. MAVEN_ARTIFACT := assembly/kar/target/*.kar for a Karaf KAR. release-build keeps its extension.
MAVEN_ARTIFACT ?=
maven_find = $(if $(MAVEN_ARTIFACT),ls -d $(MAVEN_ARTIFACT) 2>/dev/null,find target -maxdepth 1 -name '$(NAME)-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name '*-tests.jar' ! -name '*-plain.jar')
maven_one = art=$$($(maven_find)); \
	[ "$$(printf '%s\n' "$$art" | grep -c .)" -eq 1 ] || { echo "error: expected exactly one artifact matching $(or $(MAVEN_ARTIFACT),target/$(NAME)-*.jar), got: $$art" >&2; exit 1; }

setup::
	$(MVN) $(MVN_FLAGS) dependency:go-offline

fmt::
	$(MVN) $(MVN_FLAGS) spotless:apply

fmt-check::
	$(MVN) $(MVN_FLAGS) spotless:check

lint::
	$(MVN) $(MVN_FLAGS) -DskipTests verify

test::
	@mkdir -p build/reports
	$(MVN) $(MVN_FLAGS) test; s=$$?; find . -path '*/surefire-reports/TEST-*.xml' -exec cp {} build/reports/ \; 2>/dev/null; exit $$s

build::
	@mkdir -p build
	$(MVN) $(MVN_FLAGS) -DskipTests package
	$(maven_one); \
	cp "$$art" build/

audit::
	@$(call require,trivy)
	trivy fs --scanners vuln --exit-code 1 --severity HIGH,CRITICAL --ignore-unfixed --skip-dirs '**/target,build' .

release-build::
	@mkdir -p dist
	$(MVN) $(MVN_FLAGS) -DskipTests package
	$(maven_one); \
	cp "$$art" "dist/$(NAME)_$(VERSION).$${art##*.}"
