# Fol Bazar Admin Desktop v6

This release continues the desktop rebuild from v5 with fuller management workflows for customers, complaints, coupons and banners.

## Added/updated
- CSV export for Customers, Complaints, Coupons and Banners
- Complaint status editor: open, in_progress, resolved, closed
- Customer role selector
- Banner type selector and server storage image upload through the shared editor
- Coupon discount type selector
- Existing Product, Variant, Stock and Order workflows retained
- PHP/MySQL + server storage configuration retained
- Windows/Linux/macOS Electron packaging retained

## Build
```bash
npm install
npm run dist
```
Windows installer output: `release/Fol-Bazar-Admin-Setup-1.3.0.exe`
