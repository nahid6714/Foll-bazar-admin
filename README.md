# ফল বাজার Admin — PHP/MySQL Sync Update

এই আপডেটে Admin Android app-এর UI/feature structure আগের মতো রাখা হয়েছে, কিন্তু data/backend connection বর্তমান Fol Bazar website-এর PHP + MySQL architecture-এর সাথে sync করা হয়েছে।

## Current sync target
- Website data source: PHP API + MySQL (`foll_bazar`)
- Admin data source: the same PHP API + MySQL
- Product/category/variant/order/customer/complaint/coupon/banner/site-setting data একই database থেকে আসবে
- Product gallery URL এবং existing image flow আপাতত Cloudinary-তেই রাখা হয়েছে
- Supabase runtime dependency Android Admin থেকে সরানো হয়েছে

## Included features
- Admin login/session
- Products: add / edit / delete, stock, price, old price, description, category, image, gallery, active, featured, flash-sale, hot-deal
- Categories: add / edit / delete / active state
- Product variants
- Orders + order items + status/payment status
- Customers + role management
- Complaints + admin note/status
- Coupons
- Wishlist summary
- Site banners
- Site settings
- Dashboard
- Existing Cloudinary upload flow
- Existing app update / APK updater UI

## Backend setup
The ZIP contains:
- `backend/api/admin.php` — Admin API endpoint
- `backend/sql/admin_users.sql` — admin account/session tables
- `backend/tools/create_admin.php` — first admin account creator

Place `admin.php` under the existing PHP backend's `/api/` directory. Run `admin_users.sql` once in the same `foll_bazar` database, then create an admin account with the PHP CLI script.

The script expects the existing backend `config/config.php`. If your backend is `C:\Foll-Bazar\backend`, keep the same database config already used by the website API.

## Local Android emulator
Default debug API:

`http://10.0.2.2:8080/api`

`10.0.2.2` points the Android emulator to the host PC. Start the local PHP backend first:

```text
cd C:\Foll-Bazar\backend
C:\xampp\php\php.exe -S 127.0.0.1:8080 -t .
```

For a physical phone, set `ADMIN_API_BASE_URL` in `local.properties` to the PC LAN address. For cPanel release, set it to the real HTTPS API URL.

## Admin login
The Admin app no longer uses Supabase Auth. The PHP API creates an opaque admin session token after verifying the dedicated `admin_users` password hash.

Do not put the MySQL password, PHP secret, or Cloudinary API secret in the APK.

## Cloudinary
Cloudinary is intentionally **not** migrated in this update. The existing unsigned upload preset remains active so the Admin app and current website continue to use the same image URLs. The Cloudinary-to-cPanel storage migration can be done later as a separate synchronized update.

## Important migration rule
Do not delete or disable the old Supabase project yet. It remains a backup/reference until the complete website + Admin migration is verified.

## Build configuration
`local.properties.example` contains:

- `ADMIN_API_BASE_URL`
- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_UPLOAD_PRESET`

The old Supabase URL/key are no longer needed by the Android Admin runtime.
