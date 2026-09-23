# Fol Bazar Admin — PHP/MySQL + cPanel storage

The Android Admin UI talks directly to the same PHP/MySQL database used by the Laravel website. Supabase and Cloudinary are not used by the runtime.

## Production API

`https://lakebazar.com/api`

The API reads database credentials from the Laravel root `.env`.

## Images

Product, category, banner and logo images are uploaded to the website server under:

`public/uploads/admin/`

The Android app only receives the public image URL; no storage-provider secret is stored in the APK.

## First admin

With no cPanel Terminal, use the one-time `public/setup-admin.php` flow described in `ADMIN-CPANEL-PHP-MYSQL-SETUP.md`, then delete the setup file immediately.

## Build

Set:

`ADMIN_API_BASE_URL=https://lakebazar.com/api`

in `local.properties`, then build the release APK from this `admin_src` project.
