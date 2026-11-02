# Architecture

## Direction

PEYKR is intended to be implemented as two separate native applications:

- Android: Kotlin with Jetpack Compose
- iOS: Swift with SwiftUI

The `android/` and `ios/` directories are the homes for platform-specific application code, lifecycle integration, permissions, user interface, and operating-system APIs. The `shared/` directory is reserved for material that can be shared conceptually or technically without weakening native platform integration. No shared implementation technology or dependency has been selected.

## Conceptual boundaries

Future implementation should keep these concerns distinct:

1. **User interface** — trusted-device management, pairing approval, communication controls, and permission status.
2. **Trusted relationship lifecycle** — pairing approval, relationship state, and owner-initiated revocation.
3. **Platform security integration** — operating-system biometric and device-credential authentication for pairing and sensitive actions.
4. **Communication** — internet voice calling and any optional normal-call integration.
5. **Location permissions and delivery** — per-device consent and platform-supported location behavior.
6. **Platform adapters** — QR, one-time codes, NFC, call state, background execution, notifications, and location APIs.

These are design boundaries, not implemented components.

## Data and service direction

The product definition does not include central accounts, central profiles, cloud backup, or account recovery. The future data design must preserve device-local ownership and explicit trusted-device relationships. The choice of transport, persistence technology, protocol, and cryptographic design remains open and requires a separate security review before implementation.

## Sensitive-action flow

Pairing approval and other sensitive actions must request authentication through the operating system's secure biometric or device-credential APIs. PEYKR must not implement or substitute its own PIN, passcode, or biometric storage. The exact Android and iOS APIs and fallback behavior will be selected during platform implementation.

## Current repository state

This repository intentionally contains no application modules, dependencies, networking, database, cryptography, calling, location, NFC, QR, or authentication implementation.
