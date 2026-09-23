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
