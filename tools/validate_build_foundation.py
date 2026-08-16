#!/usr/bin/env python3
"""Deterministic checks for the Enterprise IAM 1.10 build foundation."""

from __future__ import annotations

import argparse
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from collections import Counter

import yaml


ROOT = pathlib.Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}

ROOT_MODULES = [
    "iam-dependencies",
    "iam-framework",
    "iam-gateway",
    "iam-auth-service",
    "iam-identity-service",
    "iam-organization-service",
    "iam-authorization-service",
    "iam-sharing-service",
    "iam-file-service",
    "iam-audit-service",
    "iam-job-service",
    "iam-test-support",
]

FRAMEWORK_MODULES = [
    "iam-common-core",
    "iam-common-web",
    "iam-common-tenant",
    "iam-common-security",
    "iam-common-mybatis",
    "iam-common-redis",
    "iam-common-mq",
    "iam-common-lock",
    "iam-common-transaction",
    "iam-common-observability",
    "iam-authorization-client-spring-boot-starter",
    "iam-api-discovery-spring-boot-starter",
    "iam-security-spring-boot-starter",
    "iam-data-permission-spring-boot-starter",
    "iam-field-permission-spring-boot-starter",
    "iam-idempotent-spring-boot-starter",
    "iam-outbox-spring-boot-starter",
    "iam-audit-spring-boot-starter",
]

SERVICES = {
    "iam-gateway": ("com.enterprise.iam.gateway", "GatewayApplication", "iam-gateway", 8080),
    "iam-auth-service": ("com.enterprise.iam.auth", "AuthApplication", "iam-auth-service", 8081),
    "iam-identity-service": ("com.enterprise.iam.identity", "IdentityApplication", "iam-identity-service", 8082),
    "iam-organization-service": ("com.enterprise.iam.organization", "OrganizationApplication", "iam-organization-service", 8083),
    "iam-authorization-service": ("com.enterprise.iam.authorization", "AuthorizationApplication", "iam-authorization-service", 8084),
    "iam-sharing-service": ("com.enterprise.iam.sharing", "SharingApplication", "iam-sharing-service", 8085),
    "iam-file-service": ("com.enterprise.iam.file", "FileApplication", "iam-file-service", 8086),
    "iam-audit-service": ("com.enterprise.iam.audit", "AuditApplication", "iam-audit-service", 8087),
    "iam-job-service": ("com.enterprise.iam.job", "JobApplication", "iam-job-service", 8088),
}


def text(node: ET.Element, path: str) -> str:
    value = node.findtext(path, default="", namespaces=NS)
    return value.strip()


def parse_pom(path: pathlib.Path, errors: list[str]) -> ET.Element | None:
    try:
        return ET.parse(path).getroot()
    except (ET.ParseError, OSError) as exc:
        errors.append(f"{path.relative_to(ROOT)}: invalid POM XML: {exc}")
        return None


def validate_reactor(errors: list[str]) -> set[str]:
    root_pom = parse_pom(BACKEND / "pom.xml", errors)
    if root_pom is None:
        return set()

    if text(root_pom, "m:version") != "1.10.1-SNAPSHOT":
        errors.append("backend parent version must be 1.10.1-SNAPSHOT")
    if text(root_pom, "m:properties/m:java.version") != "21":
        errors.append("backend Java release must be 21")
    if text(root_pom, "m:properties/m:maven.minimum.version") != "3.9.0":
        errors.append("minimum Maven version must be 3.9.0")

    root_modules = [value.text.strip() for value in root_pom.findall("m:modules/m:module", NS)]
    if root_modules != ROOT_MODULES:
        errors.append(f"root module order/set differs from freeze: {root_modules}")

    framework_pom = parse_pom(BACKEND / "iam-framework/pom.xml", errors)
    if framework_pom is not None:
        framework_modules = [
            value.text.strip() for value in framework_pom.findall("m:modules/m:module", NS)
        ]
        if framework_modules != FRAMEWORK_MODULES:
            errors.append(f"framework module order/set differs from freeze: {framework_modules}")

    expected_poms = {
        BACKEND / "pom.xml",
        *(BACKEND / module / "pom.xml" for module in ROOT_MODULES),
        *(BACKEND / "iam-framework" / module / "pom.xml" for module in FRAMEWORK_MODULES),
    }
    actual_poms = set(BACKEND.glob("**/pom.xml"))
    missing = sorted(path.relative_to(ROOT).as_posix() for path in expected_poms - actual_poms)
    unexpected = sorted(path.relative_to(ROOT).as_posix() for path in actual_poms - expected_poms)
    if missing:
        errors.append(f"reactor POMs missing: {missing}")
    if unexpected:
        errors.append(f"unexpected reactor POMs: {unexpected}")

    artifacts: list[str] = []
    internal_dependencies: list[tuple[pathlib.Path, str]] = []
    for pom in sorted(actual_poms):
        node = parse_pom(pom, errors)
        if node is None:
            continue
        artifact = text(node, "m:artifactId")
        if not artifact:
            errors.append(f"{pom.relative_to(ROOT)}: artifactId missing")
        artifacts.append(artifact)

        if pom != BACKEND / "pom.xml":
            relative = text(node, "m:parent/m:relativePath")
            if not relative:
                errors.append(f"{pom.relative_to(ROOT)}: parent relativePath missing")
            else:
                resolved_parent = (pom.parent / relative).resolve()
                if not resolved_parent.is_file():
                    errors.append(f"{pom.relative_to(ROOT)}: parent does not resolve: {relative}")
            if text(node, "m:parent/m:version") != "1.10.1-SNAPSHOT":
                errors.append(f"{pom.relative_to(ROOT)}: parent version is not 1.10.1-SNAPSHOT")

        for dependency in node.findall("m:dependencies/m:dependency", NS):
            group = text(dependency, "m:groupId")
            dependency_artifact = text(dependency, "m:artifactId")
            version = text(dependency, "m:version")
            if group == "com.enterprise.iam":
                internal_dependencies.append((pom, dependency_artifact))
                if version and version != "${project.version}":
                    errors.append(
                        f"{pom.relative_to(ROOT)}: internal dependency {dependency_artifact} "
                        "must use ${project.version}"
                    )
            elif version and pom.parent.name not in {"iam-dependencies"}:
                errors.append(
                    f"{pom.relative_to(ROOT)}: external dependency {dependency_artifact} "
                    "declares a local version instead of the BOM"
                )

    duplicates = sorted(value for value, count in Counter(artifacts).items() if count > 1)
    if duplicates:
        errors.append(f"duplicate reactor artifactIds: {duplicates}")
    artifact_set = set(artifacts)
    for pom, dependency in internal_dependencies:
        if dependency not in artifact_set:
            errors.append(f"{pom.relative_to(ROOT)}: unresolved internal artifact {dependency}")
    return artifact_set


def validate_service_skeletons(errors: list[str]) -> None:
    observed_ports: list[int] = []
    for module, (package, class_name, app_name, port) in SERVICES.items():
        package_path = pathlib.Path(*package.split("."))
        source = BACKEND / module / "src/main/java" / package_path / f"{class_name}.java"
        test = BACKEND / module / "src/test/java" / package_path / f"{class_name}Test.java"
        architecture_test = (
            BACKEND / module / "src/test/java" / package_path / "LayeredArchitectureTest.java"
        )
        config = BACKEND / module / "src/main/resources/application.yml"
        for required in (source, test, architecture_test, config):
            if not required.is_file():
                errors.append(f"service artifact missing: {required.relative_to(ROOT)}")
        if (
            not source.is_file()
            or not test.is_file()
            or not architecture_test.is_file()
            or not config.is_file()
        ):
            continue

        source_text = source.read_text(encoding="utf-8")
        if f"package {package};" not in source_text:
            errors.append(f"{source.relative_to(ROOT)}: package differs from freeze")
        for marker in ("@SpringBootApplication", f"class {class_name}", "SpringApplication.run"):
            if marker not in source_text:
                errors.append(f"{source.relative_to(ROOT)}: missing {marker}")

        test_text = test.read_text(encoding="utf-8")
        if "@SpringBootTest" not in test_text or "contextLoads" not in test_text:
            errors.append(f"{test.relative_to(ROOT)}: executable context smoke test missing")
        if "management.endpoints.enabled-by-default" in test_text:
            errors.append(
                f"{test.relative_to(ROOT)}: deprecated Actuator endpoint property is forbidden"
            )
        if "management.endpoints.access.default=none" not in test_text:
            errors.append(
                f"{test.relative_to(ROOT)}: disabled test endpoint access is not explicit"
            )
        if module == "iam-gateway":
            if "SpringBootTest.WebEnvironment.MOCK" not in test_text:
                errors.append(
                    f"{test.relative_to(ROOT)}: Gateway context test must use reactive MOCK mode"
                )
            if "spring.main.web-application-type=none" in test_text:
                errors.append(
                    f"{test.relative_to(ROOT)}: Gateway must not disable its reactive web context"
                )

        architecture_test_text = architecture_test.read_text(encoding="utf-8")
        if (
            "LayeredArchitectureRules.verify" not in architecture_test_text
            or package not in architecture_test_text
        ):
            errors.append(
                f"{architecture_test.relative_to(ROOT)}: frozen ArchUnit baseline missing"
            )

        try:
            application = yaml.safe_load(config.read_text(encoding="utf-8"))
            if application["spring"]["application"]["name"] != app_name:
                errors.append(f"{config.relative_to(ROOT)}: spring.application.name differs")
            configured_port = application["server"]["port"]
            match = re.fullmatch(r"\$\{SERVER_PORT:(\d+)}", str(configured_port))
            if not match or int(match.group(1)) != port:
                errors.append(f"{config.relative_to(ROOT)}: default port differs from freeze")
            else:
                observed_ports.append(port)
        except (KeyError, TypeError, yaml.YAMLError) as exc:
            errors.append(f"{config.relative_to(ROOT)}: invalid application.yml: {exc}")

    if len(observed_ports) != len(set(observed_ports)):
        errors.append("runtime service default ports must be unique")


def validate_no_default_secrets(errors: list[str]) -> None:
    suspicious = re.compile(
        r"(?im)^\s*(password|secret|access[-_]?key|private[-_]?key)\s*:\s*"
        r"(?!\s*$|\$\{|CHANGE_ME|<required>)([^#\s].*)$"
    )
    for path in sorted(BACKEND.glob("**/application*.y*ml")):
        if match := suspicious.search(path.read_text(encoding="utf-8")):
            errors.append(f"{path.relative_to(ROOT)}: possible production-default secret: {match.group(0)}")


def java_major() -> int | None:
    java = shutil.which("java")
    if java is None:
        return None
    completed = subprocess.run([java, "-version"], capture_output=True, text=True, check=False)
    match = re.search(r'version "(?:1\.)?(\d+)', completed.stderr + completed.stdout)
    return int(match.group(1)) if match else None


def validate_jdk(errors: list[str], require_jdk21: bool) -> str:
    major = java_major()
    if require_jdk21 and (major is None or major < 21):
        errors.append(f"JDK 21+ required for strict validation; detected {major or 'none'}")
        return "not-run"

    javac = shutil.which("javac")
    source = (
        BACKEND
        / "iam-framework/iam-common-core/src/main/java"
        / "com/enterprise/iam/common/core/context/TraceId.java"
    )
    if javac is None:
        return "skipped (javac unavailable)"
    with tempfile.TemporaryDirectory(prefix="iam-foundation-") as output:
        completed = subprocess.run(
            [javac, "-d", output, str(source)], capture_output=True, text=True, check=False
        )
    if completed.returncode != 0:
        errors.append(f"pure-JDK source syntax smoke failed: {completed.stderr.strip()}")
        return "failed"
    if major is not None and major < 21:
        return f"passed on host JDK {major} (syntax only; not Java 21 Gate B evidence)"
    return f"passed on JDK {major or 'unknown'}"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--require-jdk21",
        action="store_true",
        help="fail unless validation runs with JDK 21 or newer (intended for CI)",
    )
    args = parser.parse_args()

    errors: list[str] = []
    artifacts = validate_reactor(errors)
    validate_service_skeletons(errors)
    validate_no_default_secrets(errors)
    java_result = validate_jdk(errors, args.require_jdk21)

    if errors:
        print("\n".join(f"ERROR: {value}" for value in errors))
        return 1
    print(
        "BUILD FOUNDATION validation passed: "
        f"{len(artifacts)} reactor artifacts, {len(SERVICES)} service launchers; "
        f"Java smoke {java_result}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
