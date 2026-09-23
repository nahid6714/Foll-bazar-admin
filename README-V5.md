# Fol Bazar Admin Desktop — v5

This release continues the desktop rebuild of the Android Admin app.

## Added in v5
- Product inventory filters: Out of stock / Active only.
- Quick stock editor from the product table.
- Product variant management retained.
- Order detail and status/payment workflows retained.
- server storage image upload retained for product/banner image fields.
- Cross-platform Electron packaging names corrected for Windows, Linux and macOS.

## Build on Windows
```powershell
npm install
npm run build
npm run dist
```
The installer will be created in `release/`.

## Build Linux/macOS
```bash
npm install
npm run dist
```

## Important
Use a PHP/MySQL publishable key only. Never put a PHP/MySQL service-role key or server storage API secret in this desktop application.
