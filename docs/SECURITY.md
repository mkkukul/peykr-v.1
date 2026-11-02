# Security and Privacy Requirements

This document records requirements and design constraints. It is not a security certification and does not claim that PEYKR is currently secure.

## Product constraints

- No user accounts, email registrations, phone-number registrations, or central user profiles.
- No account recovery flow.
- No cloud backup.
- Trusted relationships remain active until either device owner revokes them.
- Location sharing requires explicit permission for each trusted device.
- Location history is not collected by default.

## Authentication for sensitive actions

Both device owners must explicitly authenticate before approving a pairing. Pairing and other sensitive actions must use the operating system's secure biometric or device-credential mechanisms, such as a secure device PIN or passcode.

The application must not collect, store, or validate raw biometric data or device passcodes. It must invoke the platform-provided authentication mechanism and handle its success, failure, cancellation, lockout, and unavailable states explicitly. Whether a biometric fallback to device credentials is offered will follow the platform API and the final product decision.

## Trusted-device lifecycle

Pairing must require mutual approval from both device owners. A QR code, one-time pairing code, or NFC exchange is an input to the pairing flow, not proof of owner approval by itself.

Revocation must be an explicit owner action and must be designed to prevent continued trust after revocation. Offline revocation semantics, replay protection, device replacement, and key lifecycle behavior remain unresolved and require a future threat model and protocol design.

## Privacy requirements

- Request only the platform permissions required for an enabled feature.
- Explain the purpose of location access before requesting it where platform guidance permits.
- Keep location sharing per trusted device and revocable.
- Do not retain location history by default.
- Do not add analytics, centralized profiles, backup, or recovery mechanisms without an explicit product decision.

## Future security work

Before production implementation, the project needs a documented threat model, pairing and revocation protocol, key-management design, secure local-storage design, transport design, abuse cases, logging policy, dependency review, and platform-specific security review. None of those mechanisms are implemented in this initial repository structure.
