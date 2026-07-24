# DOZO 🚀

DOZO is a modern, security-focused Android application designed with a unique **Brutalist UI** aesthetic. It combines essential utility with an engaging user experience, featuring offline capabilities and robust data protection.

## ✨ Features

- **Brutalist Design**: A bold, high-contrast user interface that stands out.
- **Offline Dino Game**: Never get bored when you're offline. Play the classic dino run directly in-app.
- **Secure Authentication**: Integrated with Firebase Auth for reliable and secure user management.
- **Real-time Notifications**: Powered by Firebase Cloud Messaging (FCM).
- **Location Awareness**: Efficient location tracking and activity recognition.
- **Privacy First**: Sensitive data is never logged, and all communications are secured via HTTPS.

## 🛠 Building from Source

### Prerequisites
- **Android Studio** (Koala or later recommended)
- **JDK 17** or higher
- **Firebase Account** (for Auth and Messaging features)

### Setup Instructions

1. **Clone the Repository**
   ```bash
   git clone https://github.com/kartheek0107/dozo.git
   cd dozo
   ```

2. **Configure Secrets**
   DOZO uses a template-based system to keep secrets out of source control.
   ```bash
   cp secrets.properties.template secrets.properties
   ```
   Edit `secrets.properties` and provide your `API_BASE_URL`.

3. **Firebase Setup**
   - Create a new project in the [Firebase Console](https://console.firebase.google.com/).
   - Add an Android App with package name `com.smallbasket.dozo`.
   - Download `google-services.json` and place it in the `app/` directory.

4. **Keystore Configuration (Optional for Release)**
   ```bash
   cp keystore.properties.template keystore.properties
   ```
   Edit `keystore.properties` with your signing information.

5. **Build and Run**
   - For a **Debug** build (recommended for development):
     ```bash
     ./gradlew assembleDebug
     ```
   - For a **Release** build:
     ```bash
     ./gradlew assembleRelease
     ```

## 📂 Project Structure

- `app/src/main/java`: Core Kotlin source code.
- `app/src/main/res`: UI layouts, brutalist drawables, and styling resources.
- `app/src/main/assets`: Offline assets (including the Dino game).
- `gradle/`: Build configuration and dependency management.

## 🤝 Contributing

We welcome contributions! To get started:
1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/amazing-feature`).
3. Commit your changes (`git commit -m 'Add some amazing feature'`).
4. Push to the branch (`git push origin feature/amazing-feature`).
5. Open a Pull Request.

## 🔒 Security

We take security seriously. Please refer to our [SECURITY.md](SECURITY.md) for detailed policies and how to report vulnerabilities.

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
