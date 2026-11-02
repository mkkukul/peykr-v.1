# Product Scope

## Overview

PEYKR is a privacy-first application for communication and safety between mutually approved trusted devices. A trusted relationship is persistent and remains active until either device owner explicitly revokes it.

PEYKR does not use:

- User accounts
- Email registration
- Phone-number registration
- Central user profiles
- Account recovery
- No cloud backup.

## Trusted-device relationship

Two device owners establish a relationship through mutual approval. Both owners must explicitly authenticate with the operating system's secure biometric mechanism or device credential (such as a secure device PIN or passcode) before approving pairing or another sensitive action.

Planned pairing methods are:

- QR code
- One-time pairing code
- NFC, where technically supported by the platform and device

Pairing is an approval flow between the device owners. The product must not treat possession of a pairing artifact alone as approval.

## Planned capabilities

### Trusted-device management

The application will provide a way to view trusted devices and revoke an existing relationship. Revocation by either owner ends the relationship; the exact propagation behavior when devices are temporarily offline remains to be specified.

### Communication

- Internet-based voice calls are planned.
- Normal phone calling may be offered as an optional, platform-dependent feature.

The product must not assume that either platform can reliably observe every normal phone-call state.

### Location sharing

- Location sharing is explicit and permissioned per trusted device.
- Location sharing is considered only where the operating system and platform policies allow it.
- Missed-call-related location sharing is planned only where the operating system provides the required call and background capabilities.
- Location history is not collected by default.

The product must clearly communicate when a platform cannot support a requested location or call-related behavior.

## Explicitly out of scope for this initial repository stage

This stage does not implement authentication, networking, calling, location, NFC, QR, databases, or cryptography. It establishes structure and documents requirements only.
