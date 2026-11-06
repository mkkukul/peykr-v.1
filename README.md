# PEYKR

**Privacy-first, accountless communication for Android.**

PEYKR is an Android communication application designed to connect people directly, with a strong focus on privacy, device identity, secure pairing and user-controlled data.

The long-term vision is to combine secure messaging, decentralized communication, local network file sharing and emergency location sharing in one application.

> **Development status:** PEYKR is actively under development. Some features are implemented as foundations or prototypes and still require real-world, multi-device verification.

## Core Vision

- No mandatory phone number or email registration.
- Device-based identity.
- QR code and PIN-based device pairing.
- Private communication between trusted devices.
- End-to-end encrypted messaging.
- Offline message queuing.
- Local network file sharing.
- Bluetooth-based nearby communication.
- Emergency location sharing.
- Voice and video communication.

## Technology Stack

| Component               | Technology                            |
| ----------------------- | ------------------------------------- |
| Platform                | Android                               |
| Language                | Kotlin                                |
| UI                      | Jetpack Compose                       |
| Design system           | Material 3                            |
| Navigation              | Navigation 3                          |
| Local database          | SQLite / SQLCipher                    |
| Cryptography            | Android Keystore, AES-GCM, ECDH, HKDF |
| Device pairing          | QR code and PIN                       |
| Local discovery         | Android NSD / mDNS                    |
| Local networking        | HTTP-based local transport            |
| Image and QR processing | CameraX, ML Kit, ZXing                |

## Architecture

PEYKR is organized around modular application layers:

- **Core:** Identity, cryptographic primitives and security.
- **Domain:** Messaging, pairing, contacts, transport, file transfer and location.
- **Data:** Local persistence and repositories.
- **UI:** Jetpack Compose screens and application navigation.

The architecture is intended to keep identity, security, communication and user-interface responsibilities separate.

## Features and Development Status

### 1. Device Identity and Pairing

- Device-based identity foundation.
- QR-based pairing flow.
- Inline PIN creation.
- Temporary pairing codes.
- Pairing approval and verification.

<sub>Status: Implemented foundation; further security and physical-device verification required.</sub>

### 2. Secure Messaging

- Message composition and conversation interface.
- Local message persistence.
- Encryption and ratchet-session foundations.
- Offline message queue.
- Local network transport integration.
- Delivery-state management.

<sub>Status: Under active development. Reliable bidirectional communication between separate physical devices remains to be verified.</sub>

### 3. Local Network Communication

- Nearby device discovery using Android NSD/mDNS.
- Local HTTP server and client.
- Device authentication.
- Transport management.

<sub>Status: Implemented foundation; end-to-end delivery and reliability require multi-device testing.</sub>

### 4. File Sharing

Inspired by LocalSend's local network sharing approach.

Planned capabilities include:

- Nearby device discovery.
- File selection and transfer.
- Receiver approval.
- Transfer progress.
- File integrity verification.

<sub>Status: Transfer foundations exist; complete real-device transfer verification is pending.</sub>

### 5. Emergency Location Sharing

- Android location permission handling.
- Location acquisition.
- Device identity-based signing.
- Trusted recipient-based sharing.

<sub>Status: Location acquisition and signing foundations exist. Successful delivery and recipient-side confirmation require physical-device testing.</sub>

### 6. Bluetooth Mesh Communication

Inspired by BitChat's decentralized communication approach.

Planned capabilities:

- BLE device discovery.
- Nearby communication.
- Offline message delivery.
- Mesh packet routing.
- Multi-device communication.

<sub>Status: Development foundation and packet/queue components exist. Real BLE radio communication and multi-device mesh routing are not yet verified.</sub>

### 7. Voice and Video Calling

Planned capabilities:

- Voice calls.
- Video calls.
- Secure call signalling.
- Peer-to-peer media communication.

<sub>Status: Signalling models and UI foundations exist. Real WebRTC audio/video communication is not implemented.</sub>

## Installation

Download or build the Android debug APK.

Current application ID:

`com.peykr.app`

## Development Setup

Requirements:

- Android Studio
- Android SDK
- JDK compatible with the project Gradle configuration
- Git

Clone the repository:

```bash
git clone https://github.com/mkkukul/peykr-v.1.git
cd peykr-v.1/android
```

Build the debug APK:

```bash
gradlew.bat assembleDebug
```

Run unit tests:

```bash
gradlew.bat testDebugUnitTest
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Testing

PEYKR uses unit tests to validate application components.

Important real-world tests still required include:

- Pairing between two physical Android devices.
- Bidirectional encrypted messaging.
- Offline queue delivery after reconnection.
- Local network file transfer.
- Emergency location delivery.
- BLE discovery and communication.
- WebRTC calling.

Passing unit tests does not automatically establish successful communication between physical devices.

## Privacy and Security Principles

PEYKR is designed around the following principles:

- No unnecessary account registration.
- Device-based identity.
- Explicit trusted-device pairing.
- Encryption of private communications.
- Local-first data handling.
- User-controlled location sharing.
- Authentication of incoming communication.
- No silent plaintext fallback.

The cryptographic implementation and security architecture are still undergoing development and verification. PEYKR should not yet be considered a fully audited production-grade secure messenger.

## Roadmap

- [ ] Complete reliable two-device pairing.
- [ ] Verify bidirectional end-to-end encrypted messaging.
- [ ] Complete offline message queue and delivery acknowledgements.
- [ ] Complete local network file transfer.
- [ ] Complete emergency location delivery.
- [ ] Implement and verify BLE device communication.
- [ ] Implement Bluetooth mesh routing.
- [ ] Integrate real WebRTC voice and video calls.
- [ ] Complete independent security review.
- [ ] Prepare production release.

## Project Philosophy

PEYKR aims to give users control over their communication and personal data.

The goal is not simply to build another messaging application, but to create a privacy-first communication platform that can operate across internet, local network and nearby-device communication methods.

**One identity. Trusted devices. Private communication.**
