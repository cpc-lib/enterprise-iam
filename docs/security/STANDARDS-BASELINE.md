# Security Standards Baseline

Checked: 2026-08-11

| Standard | Adoption in this project | Scope boundary |
|---|---|---|
| [OWASP ASVS](https://owasp.org/www-project-application-security-verification-standard/) | Level 2 verification target | Control IDs must be snapshotted when the release test plan is approved. |
| [RFC 8725 — JWT Best Current Practices](https://www.rfc-editor.org/info/rfc8725/) | Normative for JWT validation and key handling | Algorithm pinning, explicit typing, audience separation and cryptographic-input validation are mandatory. |
| [OWASP API Security Top 10 — 2023](https://owasp.org/API-Security/editions/2023/en/0x11-t10/) | Threat/test baseline | Object, function and property authorization plus resource-consumption abuse are release-gated. |
| [FAPI 2.0 Security Profile](https://openid.net/specs/fapi-security-profile-2_0-final.html) | Reference only | It becomes normative only if external OAuth/OIDC authorization-server scope is explicitly approved. |

Standards do not replace project invariants. Where a standard permits several
choices, `SECURITY-PARAMETERS.yaml` and SPEC 37 select the project default.
When a pinned standard version changes, update the threat-model delta,
verification mapping and compatibility impact in one reviewed change.
