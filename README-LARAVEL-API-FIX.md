# Fol Bazar Admin — Laravel API Fix

This update switches the Android Admin App from the old `admin.php?resource=...` PHP API format to the live Laravel API under `/api`.

## API used
- Login: `POST /api/auth/login`
- Products: `/api/admin/products`
- Categories: `/api/admin/categories`
- Orders: `/api/admin/orders`
- Customers: `/api/admin/customers`
- Coupons: `/api/admin/coupons`
- Banners: `/api/admin/banners`
- Settings: `/api/admin/settings`
- Complaints: `/api/complaints.php?action=admin-list` (legacy Laravel route, still admin-authenticated)

## Important
The app expects Laravel responses in the form `{ "ok": true, "data": ... }` and now unwraps them automatically for the existing Repository layer.

No Cloudinary or Supabase dependency was added.

## Build
Push the updated project to `main`. GitHub Actions will build the release APK using:
`ADMIN_API_BASE_URL=https://lakebazar.com/api`

## cPanel image upload fix

The Android app now uploads images to `POST https://lakebazar.com/api/upload` with the admin Sanctum Bearer token. It sends a multipart `file` plus `folder` (`products`, `categories`, `banners`, or `settings`) and reads the Laravel `{ok:true,data:{url,path}}` response.

The Laravel backend should use the `uploads` filesystem disk, which stores files directly under `public/storage` on cPanel. This avoids a required `php artisan storage:link` command. Make sure `public/storage` is writable by the web server/PHP user.
