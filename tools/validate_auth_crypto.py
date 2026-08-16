#!/usr/bin/env python3
"""Static security-contract checks for the authentication/crypto slice."""

from __future__ import annotations

import pathlib
import sys
import xml.etree.ElementTree as ET

import yaml


ROOT = pathlib.Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
SECURITY_ROOT = (
    BACKEND
    / "iam-framework/iam-common-security/src/main/java"
    / "com/enterprise/iam/common/security/delegation"
)
AUTH_ROOT = BACKEND / "iam-auth-service/src/main/java/com/enterprise/iam/auth"
STARTER_ROOT = (
    BACKEND
    / "iam-framework/iam-security-spring-boot-starter/src/main/java"
    / "com/enterprise/iam/security"
)


def source(path: pathlib.Path, errors: list[str]) -> str:
    if not path.is_file():
        errors.append(f"required implementation missing: {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")


def require_markers(
    path: pathlib.Path,
    markers: tuple[str, ...],
    errors: list[str],
) -> str:
    text = source(path, errors)
    for marker in markers:
        if text and marker not in text:
            errors.append(f"{path.relative_to(ROOT)} missing frozen marker: {marker}")
    return text


def dependencies(path: pathlib.Path) -> set[str]:
    root = ET.parse(path).getroot()
    return {
        value.text.strip()
        for value in root.findall("m:dependencies/m:dependency/m:artifactId", NS)
    }


def validate_crypto_dependency_management(errors: list[str]) -> None:
    root = ET.parse(BACKEND / "pom.xml").getroot()
    expected_properties = {
        "nimbus-jose-jwt.version": "10.9.1",
        "bouncycastle.version": "1.85.2",
    }
    for name, expected in expected_properties.items():
        value = root.findtext(f"m:properties/m:{name}", default="", namespaces=NS).strip()
        if value != expected:
            errors.append(f"backend/pom.xml {name} must be {expected}")
    managed = {
        dependency.findtext("m:artifactId", default="", namespaces=NS).strip():
        dependency.findtext("m:version", default="", namespaces=NS).strip()
        for dependency in root.findall("m:dependencyManagement/m:dependencies/m:dependency", NS)
    }
    expected_managed = {
        "nimbus-jose-jwt": "${nimbus-jose-jwt.version}",
        "bcprov-jdk18on": "${bouncycastle.version}",
    }
    for artifact, expected in expected_managed.items():
        if managed.get(artifact) != expected:
            errors.append(f"backend/pom.xml must manage {artifact} with {expected}")
    bom = ET.parse(BACKEND / "iam-dependencies/pom.xml").getroot()
    bom_managed = {
        dependency.findtext("m:artifactId", default="", namespaces=NS).strip():
        dependency.findtext("m:version", default="", namespaces=NS).strip()
        for dependency in bom.findall("m:dependencyManagement/m:dependencies/m:dependency", NS)
    }
    for artifact, expected in expected_managed.items():
        if bom_managed.get(artifact) != expected:
            errors.append(f"iam-dependencies BOM must manage {artifact} with {expected}")


def validate_parameters(errors: list[str]) -> None:
    config = yaml.safe_load(
        (ROOT / "docs/security/SECURITY-PARAMETERS.yaml").read_text(encoding="utf-8")
    )
    password = config.get("password", {})
    expected_password = {
        "algorithm": "argon2id",
        "normalization": "NFC",
        "memoryKiB": 19_456,
        "iterations": 2,
        "parallelism": 1,
        "saltBytes": 16,
        "outputBytes": 32,
        "storageFormat": "PHC",
        "rejectSilentTruncation": True,
    }
    for key, value in expected_password.items():
        if password.get(key) != value:
            errors.append(f"password.{key} must be {value!r}")

    delegation = config.get("jwtDelegationToken", {})
    expected_delegation = {
        "algorithmAllowlist": ["ES256"],
        "requiredTyp": "iam-delegation+jwt",
        "ttlSeconds": 30,
        "maximumClockSkewSeconds": 5,
        "maximumCompactTokenBytes": 4096,
        "keyIdRequired": True,
        "audienceCardinality": 1,
        "externalTokenAcceptedByDownstream": False,
        "missingOrInvalidBehavior": "DENY",
    }
    for key, value in expected_delegation.items():
        if delegation.get(key) != value:
            errors.append(f"jwtDelegationToken.{key} must be {value!r}")

    if config.get("authenticationProtection", {}).get("responseEnumerationResistance") is not True:
        errors.append("authenticationProtection.responseEnumerationResistance must be true")


def validate_es256_codec(errors: list[str]) -> None:
    signer = require_markers(
        SECURITY_ROOT / "Es256DelegationTokenSigner.java",
        (
            "JWSAlgorithm.ES256",
            "DelegationTokenPolicy.REQUIRED_TYPE",
            ".keyID(keyId)",
            ".audience(request.audience())",
            ".notBeforeTime",
            ".expirationTime",
            "new ECDSASigner(privateKey)",
            "P256Keys.isP256",
        ),
        errors,
    )
    decoder = require_markers(
        SECURITY_ROOT / "Es256DelegationTokenDecoder.java",
        (
            "MAX_COMPACT_TOKEN_LENGTH = 4096",
            "JWSAlgorithm.ES256.equals",
            "DelegationTokenPolicy.REQUIRED_TYPE.equals",
            "KEY_ID.matcher(keyId).matches()",
            "keyResolver.resolve(keyId)",
            "KEY_RESOLUTION_UNAVAILABLE",
            "token.verify(new ECDSAVerifier(publicKey))",
            "P256Keys.isP256",
            "return policy.validate(verifiedClaims)",
        ),
        errors,
    )
    filter_source = require_markers(
        STARTER_ROOT / "delegation/TrustedDelegationFilter.java",
        (
            'DELEGATION_HEADER = "X-IAM-Delegation"',
            "decoder.decode",
            "if (!result.isValid())",
            "SC_UNAUTHORIZED",
            "IAM_AUTHENTICATION_REQUIRED",
            "request.setAttribute(TRUSTED_CONTEXT_ATTRIBUTE",
        ),
        errors,
    )
    main_security_source = "\n".join(
        path.read_text(encoding="utf-8") for path in SECURITY_ROOT.glob("*.java")
    )
    for forbidden in ("JWSAlgorithm.HS256", "MACSigner", "acceptEmbeddedJwk"):
        if forbidden in main_security_source:
            errors.append(f"delegation main source contains forbidden trust mechanism: {forbidden}")
    for secret_marker in ("BEGIN PRIVATE KEY", "default-secret", "changeit"):
        if secret_marker in signer + decoder + filter_source:
            errors.append(f"delegation source embeds key/secret marker: {secret_marker}")

    codec_test = source(
        BACKEND
        / "iam-framework/iam-common-security/src/test/java"
        / "com/enterprise/iam/common/security/delegation/Es256DelegationTokenCodecTest.java",
        errors,
    )
    filter_test = source(
        BACKEND
        / "iam-framework/iam-security-spring-boot-starter/src/test/java"
        / "com/enterprise/iam/security/delegation/TrustedDelegationFilterTest.java",
        errors,
    )
    for marker in (
        "signsAndCryptographicallyVerifiesAudienceBoundEs256Token",
        "rejectsSignatureTampering",
        "rejectsUnknownKeyBeforeClaimTrust",
        "distinguishesKeyResolverOutageFromInvalidToken",
        "rejectsNonP256EcKeyEvenWhenItIsOtherwiseAnEcKey",
        "rejectsAlgorithmSubstitutionWithoutResolvingKey",
        "rejectsUnsafeKeyIdBeforeCallingResolver",
    ):
        if marker not in codec_test:
            errors.append(f"ES256 codec test evidence missing: {marker}")
    for marker in (
        "rejectsMissingOrInvalidDelegationWithoutLeakingValidationReason",
        "publishesTrustedContextOnlyAfterDecoderSuccess",
        "reportsKeyResolutionOutageAsGenericServiceUnavailable",
    ):
        if marker not in filter_test:
            errors.append(f"delegation filter test evidence missing: {marker}")

    common_dependencies = dependencies(BACKEND / "iam-framework/iam-common-security/pom.xml")
    if "nimbus-jose-jwt" not in common_dependencies:
        errors.append("iam-common-security dependency missing: nimbus-jose-jwt")
    if "spring-security-web" in common_dependencies:
        errors.append("iam-common-security must remain independent of Servlet security")
    starter_dependencies = dependencies(
        BACKEND / "iam-framework/iam-security-spring-boot-starter/pom.xml"
    )
    if "spring-security-web" not in starter_dependencies:
        errors.append("iam-security-spring-boot-starter dependency missing: spring-security-web")


def validate_login(errors: list[str]) -> None:
    verifier = require_markers(
        AUTH_ROOT / "infrastructure/security/Argon2idPasswordVerifier.java",
        (
            "SALT_LENGTH_BYTES = 16",
            "HASH_LENGTH_BYTES = 32",
            "PARALLELISM = 1",
            "MEMORY_KIB = 19_456",
            "ITERATIONS = 2",
            "new Argon2PasswordEncoder",
            'startsWith("$argon2id$")',
            "verifyAgainstDummy(rawPassword)",
            "Normalizer.Form.NFC",
        ),
        errors,
    )
    use_case = require_markers(
        AUTH_ROOT / "application/service/AuthenticateLoginUseCase.java",
        (
            "SEC-AUTHN-001",
            "performEnumerationResistantDummyLookup",
            "passwordVerifier.verifyAgainstDummy",
            "LoginResult.rejected()",
            "Arrays.fill(rawPassword, '\\0')",
            "command.destroy()",
        ),
        errors,
    )
    public_result = source(AUTH_ROOT / "application/model/LoginResult.java", errors)
    if "LoginFailureReason" in public_result or "failureReason" in public_result:
        errors.append("LoginResult must not expose an internal authentication failure reason")

    login_test = source(
        BACKEND
        / "iam-auth-service/src/test/java/com/enterprise/iam/auth/application/service"
        / "AuthenticateLoginUseCaseTest.java",
        errors,
    )
    argon_test = source(
        BACKEND
        / "iam-auth-service/src/test/java/com/enterprise/iam/auth/infrastructure/security"
        / "Argon2idPasswordVerifierTest.java",
        errors,
    )
    for marker in (
        "SEC-AUTHN-001",
        "isEqualTo(wrongResult)",
        "totalLookupPaths()).isEqualTo(1)",
        "totalCalls()).isEqualTo(1)",
        "isDestroyed()).isTrue()",
        "unknownCommand::passwordCopy",
        "crossTenantCredentialIsNotTrusted",
    ):
        if marker not in login_test:
            errors.append(f"enumeration-resistance test evidence missing: {marker}")
    for marker in ("$argon2id$", "m=19456,t=2,p=1", "malformedOrNonArgon2idPhcFailsClosed"):
        if marker not in argon_test:
            errors.append(f"Argon2id test evidence missing: {marker}")

    auth_dependencies = dependencies(BACKEND / "iam-auth-service/pom.xml")
    for dependency in ("spring-security-crypto", "bcprov-jdk18on"):
        if dependency not in auth_dependencies:
            errors.append(f"iam-auth-service dependency missing: {dependency}")

    combined = verifier + use_case
    for secret_marker in ("password=", "default-password", "changeit"):
        if secret_marker in combined:
            errors.append(f"authentication source embeds secret marker: {secret_marker}")


def main() -> int:
    errors: list[str] = []
    validate_parameters(errors)
    validate_crypto_dependency_management(errors)
    validate_es256_codec(errors)
    validate_login(errors)
    if errors:
        print("\n".join(f"ERROR: {error}" for error in errors))
        return 1
    print(
        "Authentication/crypto validation passed: ES256 sign/verify, fail-closed "
        "downstream filter, Argon2id PHC and enumeration-resistant login core"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
