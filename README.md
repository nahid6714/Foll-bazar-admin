# Fol Bazar Admin — Server API Update

The Android Admin app in this project targets:

`https://lakebazar.com/backend/api`

Replace these three files on the production server:

`public_html/backend/api/admin.php`
`public_html/backend/api/orders.php`
`public_html/backend/api/upload.php`

with the files in `server-backend-update/backend/api/`.

Why:
- `admin.php` adds admin user-role update and wishlist summary.
- `orders.php` adds authenticated admin order-detail + items endpoint.
- `upload.php` allows category image uploads in addition to products/banners.

Do NOT replace `backend/config/config.php` from this package and do not upload database credentials to GitHub.

The existing database/schema does not need a migration for these three API changes.
