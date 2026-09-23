# Fol Bazar Admin — Android + Laravel/MySQL v2.1

This is the cleaned Android Admin app source for Fol Bazar.

## Backend architecture

`Android Admin App → HTTPS PHP API → Laravel site's MySQL database`

The Android runtime does not use external database/storage services. Product, category, banner and logo images are uploaded to the same cPanel server storage used by the website.

### API

Production default:

`https://lakebazar.com/api`

The API entry point is:

`public/api/admin.php`

The API reads the Laravel root `.env`, so database credentials are never compiled into the APK.

## Included Admin features

- Admin login/session
- Dashboard and store metrics
- Products
- Product variants
- Categories
- Stock and pricing
- Orders
- Payment/order status updates
- Customers/profiles
- Complaints
- Coupons
- Homepage/site banners
- Site settings
- Wishlist summary
- Server-side image upload
- Receipt/export tools
- Theme/preferences
- In-app APK update checker

## Important build fix

The previous GitHub Actions failure came from stale Kotlin references to configuration constants that no longer exist. This version contains no runtime source references to those old services, so the missing-constant errors are removed at the source instead of being hidden with fake secrets.

## Local build

1. Open this folder in Android Studio.
2. Let Android Studio sync the Gradle project.
3. `local.properties` is optional because the production API URL is now the safe default.
4. For a different API, add:

`ADMIN_API_BASE_URL=https://your-domain.example/api`

Never put a database password or private API secret in `local.properties`, Kotlin code, or the APK.

## cPanel backend deployment

Copy:

- `backend/api/admin.php` → Laravel `public/api/admin.php`
- `public/setup-admin.php` → Laravel `public/setup-admin.php` only during first-admin setup
- `backend/sql/admin_users.sql` can be imported in phpMyAdmin if the admin tables do not exist

Delete `setup-admin.php` immediately after creating the first admin.

## GitHub Actions

`.github/workflows/build-apk.yml` builds the release APK on every push/PR and uploads the APK as a workflow artifact. It uses Java 17 and Gradle 8.9, compatible with the project's Android Gradle Plugin 8.7.3.

The workflow also fails early if stale legacy provider references are reintroduced into the Android Kotlin source.
