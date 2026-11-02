# Platform Limitations

PEYKR must treat Android and iOS capabilities as platform-dependent. APIs and policies change by operating-system version, device, region, carrier, and application entitlement. The items below are constraints to validate during implementation, not guarantees.

## Normal phone-call detection

Reliable detection of every normal cellular phone-call state is not available uniformly:

- Android call-state access and detection capabilities depend on the Android version, available APIs, granted permissions, device and manufacturer behaviour, and dialer role or special privileges in certain scenarios. Background execution and event delivery can also be restricted. Becoming the default dialer is not universally required or universally sufficient.
- iOS does not provide a general-purpose API that gives an ordinary third-party application unrestricted, reliable access to all cellular call events or call history. CallKit supports defined VoIP and system call-integration scenarios, not arbitrary observation of every normal phone call.

Therefore, missed-call-related location sharing must be offered only where the target platform and permitted APIs can provide the required signal. PEYKR must not promise that a missed normal call will always trigger location sharing.

## NFC peer-to-peer pairing

NFC is not a uniform peer-to-peer transport:

- Android NFC capabilities and background behavior vary by device, OS version, and supported NFC technologies. Device-to-device exchange may require a platform-supported handoff or another transport rather than unrestricted peer networking.
- iOS exposes NFC through entitlement-controlled, purpose-specific APIs and does not provide a general unrestricted peer-to-peer NFC channel for arbitrary application pairing.

NFC pairing must therefore be treated as optional and capability-detected. It must not be required for the product's core pairing flow. QR code and one-time pairing code flows also require platform-appropriate secure handling and explicit owner approval.

## Biometrics and device credentials

Both platforms provide operating-system authentication facilities, but available biometric types, fallback rules, lockout behavior, device-credential requirements, and user settings differ. Pairing approval and other sensitive actions must use the platform's secure biometric or device-credential mechanism rather than an application-defined credential.

The implementation must handle unsupported, unavailable, cancelled, failed, and locked-out authentication states without treating them as approval.

## Location and background execution

Location permission levels, background execution, notification delivery, and battery policies differ across Android and iOS. The operating system or user may deny, limit, pause, or revoke access. Missed-call-related location behavior is consequently conditional and must not be represented as universally available.

The product must avoid location history by default and request explicit, per-trusted-device consent before sharing location.

## Native implementation boundary

Android and iOS applications should use their respective native APIs and permission models. The repository does not yet select a cross-platform framework, shared runtime, transport, storage technology, or dependency set.
