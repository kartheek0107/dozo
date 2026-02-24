## 🔒 Security

This app takes security seriously:

- ✅ No hardcoded credentials
- ✅ ProGuard obfuscation in release builds
- ✅ Secure logging (no sensitive data in logs)
- ✅ HTTPS for all communication
- ✅ User data deletion on request

See [SECURITY.md](SECURITY.md) for details.

## Building from Source

### Prerequisites
- Android Studio Arctic Fox or later
- JDK 17
- Firebase account

### Setup

1. Clone the repository:
```bash
git clone https://github.com/YOUR_USERNAME/small-basket.git
cd small-basket
```

2. Create `secrets.properties` from template:
```bash
cp secrets.properties.template secrets.properties
```

3. Edit `secrets.properties` with your backend URL:
```properties
API_BASE_URL=https://your-backend.com/
```

4. Add your `google-services.json` to `app/` directory (get from Firebase Console)

5. Create signing keystore (for release builds):
```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias smallbasket
```

6. Create `keystore.properties`:
```bash
cp keystore.properties.template keystore.properties
```

7. Edit `keystore.properties` with your keystore info

8. Build:
```bash
./gradlew assembleRelease
```

### Debug Build (No keystore needed)
```bash
./gradlew assembleDebug
```