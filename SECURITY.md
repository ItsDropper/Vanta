# Security Policy

## Reporting a vulnerability

Please report suspected security vulnerabilities privately rather than publishing exploit details in a public issue.

Use GitHub's **Private vulnerability reporting** feature for this repository if it is enabled. If it is unavailable, contact the repository owner through their GitHub profile and request a private channel. Include the affected version or commit, impact, and minimal reproduction steps. Do not include real credentials, access tokens, or other private data.

## Supported versions

Security fixes are currently considered for the latest code on the `main` branch. No formal long-term-support policy is established unless a release explicitly says otherwise.

## Java runtime security

Vanta disables HotSpot's hardware-accelerated Base64 intrinsic for x86-64
Java 17+ Minecraft child processes and for its own x86-64 packaged runtime.
This is a defense-in-depth mitigation for the reported Java4Shell
(CVE-2026-114938) issue. Keep Vanta and its managed Java runtimes updated;
the mitigation does not replace vendor security updates and does not protect
Java processes started outside Vanta.

## Scope and limitations

Vanta is developed on an evolving basis. It has not necessarily undergone an independent security audit or penetration test. No software can be guaranteed free of vulnerabilities. Keep dependencies updated and avoid using untrusted builds.

Please allow time to investigate and prepare a fix before public disclosure. This policy does not promise a specific response time or bounty.
