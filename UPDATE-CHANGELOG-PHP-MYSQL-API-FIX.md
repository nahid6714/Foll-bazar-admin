# Fol Bazar Admin — PHP/MySQL API connection fix

## Changes
- Production API base changed from `https://lakebazar.com/backend/api` to `https://lakebazar.com/backend/api`.
- Admin login now calls `auth.php?action=login`.
- Product/category/variant/coupon/settings CRUD now uses the PHP `manage.php` API.
- Orders now use `orders.php`.
- Customers/role management uses `admin.php`.
- Complaints use `complaints.php`.
- Banners use `banners.php`.
- Wishlist summary uses `admin.php?action=wishlist-summary`.
- Image upload now calls `upload.php` and keeps Bearer authentication.
- PHP `{ok:true,data:...}` responses are unwrapped for the existing Repository.
- Object responses are safely wrapped for existing list parsers.
- Existing UI and database models were preserved; this update targets the API transport layer.

## Required production URL
`https://lakebazar.com/backend/api/`

## Important
This source project was updated; an APK still needs to be built and signed with the same signing key as any already-installed release.
