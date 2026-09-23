# Fol Bazar Admin Desktop v1.8.3

Startup fix: when no saved PHP/MySQL configuration exists, the app now exits the splash screen and shows the Login screen. PHP/MySQL session startup also has an 8-second timeout so a stalled auth request cannot leave the app stuck on the splash screen.

Build on Windows:

```cmd
npm install
npm run dist
```

Installer: `release/Fol-Bazar-Admin-Setup-1.8.3.exe`
