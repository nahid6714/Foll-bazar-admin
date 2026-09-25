# ফল বাজার Admin — Full Control Panel v31

This version turns the Android app into the main admin control panel for the PHP/MySQL-backed fruit shop.

## Included
- PHP/MySQL Auth + `profiles.role == admin` login gate
- Products: add / edit / delete, stock, price, old price, description, category, server storage image, active, featured, flash-sale, hot-deal
- Categories: add / edit / delete / active state
- Product variants table support in the repository
- Orders: all orders, customer/address/payment details, order status and payment status
- Customers: profiles list and role management
- Complaints: status + admin note
- Coupons: percent/fixed discount, minimum order, maximum discount, usage limit, active state
- Wishlist summary: product-wise wishlist counts
- Dashboard: product/order/customer/pending/delivered-sales/complaint counts
- No PHP/MySQL service-role/secret key in the APK

## Important: database setup
Run `PHP/MySQL/ADMIN_SETUP.sql` in the PHP/MySQL SQL Editor once. It adds the missing admin authorization policies and the coupon/wishlist/site-settings tables.

The current product schema is the schema from `mousum-bazar-PHP/MySQL-schema.sql`:
- `products.name`
- `products.slug`
- `products.category_id`
- `products.stock_quantity`
- `products.image_url`
- `products.is_active`
- `products.is_featured`
- `products.is_flash_sale`
- `products.is_hot_deal`

The previous Admin v12 app was querying the old names `category`, `stock`, `title`; that caused the 400 schema-cache error. v13 now uses the current schema.

Only run `PHP/MySQL/PRODUCT_SCHEMA_FIX.sql` if your live database is actually missing the new product columns. Do not run it blindly on an already-correct schema.

## Admin account
1. Create/sign in the user in PHP/MySQL Auth.
2. Make that user's `public.profiles.role` equal to `admin`.
3. Run `ADMIN_SETUP.sql` before using CRUD.

The app uses the user's JWT for every database request, and the Laravel admin middleware and the authenticated admin token are the authorization layer.

## server storage
The APK uses an unsigned upload preset. No server storage API secret is stored in the app.

The existing release workflow can build and publish the APK. Required values remain:
- `REMOVED_CLOUD_STORAGE_SETTING`
- `REMOVED_CLOUD_STORAGE_SETTING`

For PHP/MySQL schema changes, prefer migration files / version-controlled SQL rather than repeatedly editing the production database manually.


## In-app automatic update
The Admin app checks a small `update.json` manifest published with the latest
API for normal update checks. From **সেটিংস / App Update** an admin can check
manually, see a newer version, download the APK with progress, and launch
Android's installer. Android may require the app's **Install unknown apps**
permission the first time.

The older `release-apk.yml` is manual-only to prevent duplicate builds.


## App icon and in-app updates
- The launcher icon is the text-free fruit/admin logo in `app/src/main/res/drawable/app_logo.png`.
- For updates to install over an existing APK, every release must use the same signing key.

## APK updater safety


### Current Fol Bazar server storage configuration
- Cloud name: `bak9nabq`
- Unsigned upload preset: `bak9nabq`
- Asset folder: `fol_bazar_products` (configured in server storage preset; the app does not use this as the preset name)

### PHP/MySQL schema alignment
The admin app is aligned with the current public schema: products use `name`, `stock_quantity`, `category_id`, `image_url`, `is_active`; categories use `is_active`; variants use `weight_grams`, `stock_quantity`, `is_active`; coupons use `title`, `min_order`, and `discount_type` values `percent`/`fixed`; orders expose payment sender number, TrxID, coupon, shipping and notes.


## v28 Variant Management
- Added Admin UI for product sizes/variants (e.g. 500g, 1kg, 2kg).
- Admin can add, edit, delete, activate/deactivate variants.
- Variant fields: label, weight_grams, price, old_price, stock_quantity, sort_order.
- Uses the existing `product_variants` PHP/MySQL table; no schema change required.


## v31 UI updates
- Dashboard now links directly to every admin section.
- Dashboard bottom refresh button removed; pull-to-refresh remains available.
- Global top-bar logout removed; logout is available from Settings.
- Customers page now lists all profiles with search/filter by name, phone, or Gmail, user detail view, copy actions, and phone dial action.
- Customer detail includes available profile address and account metadata.
- Order detail includes quick call/copy actions for the customer phone.


## Current backend
This Admin uses the Fol Bazar PHP/MySQL API at `https://lakebazar.com/backend/api`. The runtime uses the Laravel API only. Images are uploaded to the website's own `/uploads/products/`, `/uploads/categories/`, or `/uploads/banners/` storage through `/backend/api/upload.php`.

## v15 direct update path

The app first checks `https://lakebazar.com/backend/api/update.php?action=manifest`, which is intentionally outside the static website SPA rewrite. Put the matching `update.php`, `update.json`, and signed APK under `public_html/backend/api/` and `public_html/backend/updates/` as described in `SERVER-UPDATE/README.txt`.
