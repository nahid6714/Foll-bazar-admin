# Fol Bazar Admin — cPanel PHP/MySQL setup

This version uses **the same Laravel MySQL database and the same cPanel server storage as the website**.

Removed from the Admin runtime:
- Supabase
- Cloudinary
- Cloudinary upload settings

The Android Admin app talks to:

`https://lakebazar.com/api/admin.php`

Images are uploaded to:

`https://lakebazar.com/uploads/admin/...`

## 1. Website files

Keep the Laravel project outside `public_html` as already configured:

`/home/lakebazar/Fol-Bazar-Laravel/`

The public files go in:

`/home/lakebazar/public_html/`

Copy these backend files from this package into the Laravel project:

- `public/api/admin.php`
- `public/setup-admin.php` (only for first admin creation)

The API reads the Laravel root `.env`, so **do not put the MySQL password in the APK**.

## 2. Database

The API needs two tables:

- `admin_users`
- `admin_sessions`

You can import `backend/sql/admin_users.sql` in phpMyAdmin, or use `setup-admin.php`, which creates them automatically.

## 3. Create the first Admin without cPanel Terminal

Because this cPanel account has no Terminal:

1. Copy `public/setup-admin.php` into the live `public_html/`.
2. Open the one-time setup URL using the setup key shown in your private deployment notes.
3. Enter the admin name, email and a strong password (10+ characters).
4. Create the admin.
5. **Immediately delete `public_html/setup-admin.php`.**

Never leave the setup file online.

## 4. API URL in Android Admin

Set `ADMIN_API_BASE_URL` in `admin_src/local.properties` to:

`https://lakebazar.com/api`

The release build also defaults to this URL.

## 5. Build

Open `admin_src` in Android Studio and build the release APK.

The app can control the same database records used by the website:

- Products
- Product variants
- Categories
- Orders and order status/payment status
- Customers/profiles
- Complaints
- Coupons
- Homepage/site banners
- Site settings
- Wishlist summary
- Product/category/banner/logo image uploads to the site's own storage

## 6. Image storage

Uploaded images are saved under:

`public/uploads/admin/`

The server generates random filenames and accepts JPG, PNG, WEBP and GIF up to 10 MB.

## 7. Important security notes

- Keep `.env` outside `public_html`.
- Never put `DB_PASSWORD` in `local.properties`, Kotlin code, or the APK.
- Delete `setup-admin.php` immediately after creating the first admin.
- Use HTTPS for the live API.
- Keep the existing WordPress backup until the Laravel site is fully verified.
