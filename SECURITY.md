# Security Policy

## Reporting Vulnerabilities

If you discover a security vulnerability, please email: devteam.dozo@gmail.com

**DO NOT** open a public issue if a security vulnerability is found.

## Security Measures

✅ **Authentication**
- Firebase Auth with email verification
- JWT tokens with automatic refresh
- Session management

✅ **Data Protection**
- HTTPS for all communication
- Auth tokens never logged
- Sensitive data sanitized in logs
- ProGuard obfuscation in release builds

✅ **Location Privacy**
- Location only tracked when app is open
- Users can delete all data
- Device ID for counting only (not tracking)
- Clear privacy policy in-app

✅ **Rate Limiting**
- Server-side: 6 requests/minute per IP
- Client-side: 10-second cooldown
- Prevents abuse and DoS attacks

✅ **Input Validation**
- All API inputs validated server-side
- SQL injection prevention (using Firestore)
- XSS prevention

## Security Best Practices for Contributors

1. **Never commit sensitive files:**
    - `google-services.json`
    - `secrets.properties`
    - Keystores (`.jks`, `.keystore`)

2. **Always use:**
    - BuildConfig for API URLs
    - ProGuard for release builds
    - Proper logging (no sensitive data)

3. **Before committing:**
    - Check for hardcoded credentials
    - Verify `.gitignore` is working
    - Test with ProGuard enabled

## Update Policy

Security updates released within 48 hours of discovery.

## Audit Log

- 2026-02-25: Initial security audit and hardening
    - Removed hardcoded API URLs
    - Added ProGuard rules
    - Secured logging configuration
    - Added privacy policy
