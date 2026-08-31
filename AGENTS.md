# AI / Contributor Guide

This repository must be precise about what is real versus simulated. Do not present a UI mockup or heuristic as an active VPN, encryption layer, threat detector, or security guarantee unless the underlying networking/security function is actually implemented and tested.

## Priorities
1. Label demo/mock/security-simulation features clearly.
2. Never claim traffic is encrypted, tunneled, blocked, or protected unless the code actually performs that function.
3. AI security advice must be advisory and explain its evidence/limitations.
4. Never expose API keys, credentials, browsing history, DNS data, or network identifiers to remote AI without explicit user action.
5. Prefer local analysis for diagnostics when practical.
6. Fail closed for genuine security controls; fail clearly for demo features.
7. Keep logs free of secrets and sensitive URLs.
8. Document architecture, threat model, and unsupported protections.

## Before merging
- Verify every security claim against implemented behavior.
- Test offline/network failure paths.
- Confirm no secrets or browsing data are logged.
- Keep AI optional and clearly labeled.
- Update README when capabilities change.
