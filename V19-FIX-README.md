Fol Bazar Admin v19 compile-fix

Based on FolBazar-Admin-v18-Upload-URL-Fixed.

Fixes included:
- removed separate Analytics bottom navigation option
- Dashboard Delivered Sales opens product-wise delivered sales
- Dashboard Today's Orders opens Orders with Today filter
- Dashboard + Product opens new-product editor directly
- duplicate Dashboard Order shortcut removed; main Orders navigation remains
- Orders filters: all, today, yesterday, this week, this month
- Banner category selector and category_id API payload
- Light/Dark/Device theme with persistent preference
- receipt image/PDF generation and Android 10+ Downloads saving (no FileProvider-root error for downloads)
- restored Dashboard helper composables removed by the previous feature patch
- receipt exporter kept in one source file to avoid enum/helper redeclaration errors

Server:
Run SERVER-UPDATE/site_banners_category_migration.sql once before using banner category targeting. The backend API must accept/return category_id for site_banners.
