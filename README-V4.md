# Fol Bazar Admin Desktop v4

This rebuild continues the Android Admin feature parity as a cross-platform Electron desktop app.

## v4 focus
- Product editor + server storage image upload using current saved settings
- Product variant add/edit/delete
- Order details and status/payment updates
- CSV export
- PHP/MySQL admin authentication
- Windows NSIS, Linux AppImage/deb, macOS dmg packaging

## Windows build
1. Install Node.js LTS.
2. Open this folder in PowerShell.
3. `npm install`
4. `npm run dist`
5. Installer appears under `release/`.

The app uses the same PHP/MySQL project as the existing Fol Bazar admin. Never put a PHP/MySQL service-role key or server storage API secret in this desktop app.
