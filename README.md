# Peykr

**Secure. Private. Device-to-Device Communication.**

Peykr is an Android application focused on privacy, device identity, mutual trust, and secure communication foundations. It is designed around the principle that users should remain in control of their trusted devices and locally stored data.

[English](#english) | [Türkçe](#türkçe)

---

# English

## About Peykr

Peykr is a privacy-focused Android application that aims to establish a secure foundation for trusted device-to-device communication.

The application combines device-bound cryptographic identity, encrypted local storage, biometric authorization, and QR-based mutual device pairing.

Peykr follows a privacy-first approach: sensitive identity material and application data are designed to remain under local device control.

## Key Features

### Device Identity

- Device-bound cryptographic identity.
- P-256 ECDSA key pair generated and protected by Android Keystore.
- Private identity key is never exported.
- Public device identifier derived from the public key.

### Encrypted Local Storage

- SQLCipher-based encrypted database.
- AES-256 database encryption.
- Database encryption key protected through Android Keystore.
- Encrypted database migration and local data protection.

### Biometric Security

- Android BiometricPrompt integration.
- Support for strong biometrics and device credentials.
- Scope-specific authorization tokens.
- Single-use authorization and expiration controls.

### Trusted Device Pairing

- Camera-based QR code pairing.
- Mutual device identity verification.
- Four-step pairing confirmation protocol.
- Device fingerprint verification.
- Explicit user confirmation before establishing trust.
- Pairing state validation and recovery handling.

### Privacy-Focused Design

- Local-first data architecture.
- No mandatory phone number or email account.
- No cloud account requirement in the current architecture.
- Clear distinction between implemented functionality and planned capabilities.

## Technology Stack

| Technology              | Purpose                      |
| ----------------------- | ---------------------------- |
| Kotlin                  | Primary programming language |
| Jetpack Compose         | Declarative UI               |
| Material 3              | Design system                |
| Android Keystore        | Cryptographic key protection |
| SQLCipher               | Encrypted local database     |
| SQLite                  | Local data storage           |
| AndroidX Biometric      | Biometric authentication     |
| CameraX                 | Camera integration           |
| ML Kit Barcode Scanning | QR code detection            |
| ZXing                   | QR code generation           |
| Kotlin Coroutines       | Asynchronous operations      |
| StateFlow               | Reactive application state   |

## Security Architecture

Peykr uses multiple security layers:

1. **Device Identity:** Each installation has a cryptographic identity protected by Android Keystore.
2. **Local Encryption:** Application database content is encrypted using SQLCipher.
3. **Key Protection:** The database encryption key is protected using an Android Keystore-backed AES-GCM key.
4. **Biometric Authorization:** Sensitive operations can require explicit authorization through the security gate.
5. **Mutual Pairing:** Devices exchange and verify cryptographic identity information through a QR-based handshake.

### Important Security Scope

The current pairing implementation establishes mutual device identity verification and trust.

It does not yet provide a complete end-to-end encrypted communication channel. ECDH-based key agreement, HKDF key derivation, and the complete messaging transport layer remain future development work.

Peykr does not claim that all planned security capabilities are already implemented.

## Project Structure

The Android application is organised around a layered architecture:

```text
peykr/
├── android/
│   ├── app/
│   │   └── src/
│   │       ├── main/
│   │       │   ├── kotlin/com/peykr/app/
│   │       │   │   ├── data/
│   │       │   │   ├── domain/
│   │       │   │   ├── security/
│   │       │   │   └── ui/
│   │       │   └── res/
│   │       └── test/
│   └── gradle/
├── README.md
└── ...
```

The exact module structure may evolve as development progresses.

## Requirements

- Windows, macOS, or Linux
- Android Studio
- JDK compatible with the project Gradle configuration
- Android SDK
- Git

## Getting Started

Clone the repository:

```bash
git clone https://github.com/mkkukul/peykr-v.1.git
```

Open the Android project:

```text
peykr/android
```

Build the debug APK:

```bash
cd android
./gradlew assembleDebug
```

On Windows:

```powershell
cd android
.\gradlew.bat assembleDebug
```

Run unit tests:

```bash
./gradlew testDebugUnitTest
```

Compile Android instrumentation tests:

```bash
./gradlew compileDebugAndroidTestKotlin
```

The debug APK is generated at:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Development Status

Peykr is under active development.

| Component                        | Status           |
| -------------------------------- | ---------------- |
| Android application foundation   | Implemented      |
| Premium UI and branding          | Implemented      |
| Device cryptographic identity    | Implemented      |
| Encrypted local database         | Implemented      |
| Biometric authorization gate     | Implemented      |
| QR-based mutual device pairing   | Implemented      |
| Pairing security hardening       | Implemented      |
| Camera scanner integration       | Implemented      |
| Complete E2EE communication      | Planned          |
| ECDH and HKDF key agreement      | Planned          |
| Peer-to-peer messaging transport | Planned          |
| Real-time location sharing       | Planned          |
| iOS application                  | Future direction |

Implementation status does not imply that every feature has been independently verified on physical devices.

## Privacy Principles

Peykr is being developed around the following principles:

- Privacy by design.
- Local-first data ownership.
- Minimal collection of personal information.
- Cryptographic device identity.
- Explicit mutual trust.
- Transparent security boundaries.

## Contributing

Contributions, suggestions, and security feedback are welcome.

Before submitting major changes, please open an issue to discuss the proposed implementation.

## Security Issues

Please do not publicly disclose exploitable security vulnerabilities before responsible coordination.

Report security concerns privately to the project maintainer.

## License

The project's licence has not yet been specified.

---

# Türkçe

## Peykr Hakkında

Peykr, gizlilik odaklı cihazlar arası iletişim için güvenli bir temel oluşturmayı amaçlayan bir Android uygulamasıdır.

Uygulama; cihaza bağlı kriptografik kimlik, şifreli yerel depolama, biyometrik yetkilendirme ve QR kod tabanlı karşılıklı cihaz eşleştirme özelliklerini bir araya getirir.

Peykr'nin temel yaklaşımı, hassas kimlik bilgilerinin ve uygulama verilerinin kontrolünü kullanıcının kendi cihazında tutmaktır.

## Temel Özellikler

### Cihaz Kimliği

- Cihaza bağlı kriptografik kimlik.
- Android Keystore tarafından korunan P-256 ECDSA anahtar ç
