# Fol Bazar Admin — PHP/MySQL Backend Migration

The Android Admin UI is kept intact. The data layer now talks to the same PHP/MySQL backend used by the current Fol Bazar website instead of Supabase.

## Backend

Place `backend/api/admin.php` on the same PHP API host as the website backend, and run `backend/sql/admin_users.sql` once. Create the first admin with `backend/tools/create_admin.php` from PHP CLI.

## Local Android emulator

The default debug API URL is:

`http://10.0.2.2:8080/api`

This maps the Android emulator to the host machine's `127.0.0.1:8080` PHP server.

For a physical phone, replace `ADMIN_API_BASE_URL` in `local.properties` with the PC's LAN address, for example:

`http://192.168.1.10:8080/api`

For cPanel release builds, set `ADMIN_API_BASE_URL` to the real HTTPS API URL, for example:

`https://your-domain.com/api`

## Images

Cloudinary remains enabled for now. Image upload was intentionally not migrated in this version so the website and Admin can stay synchronized while the storage migration is done later.

## Important

- Supabase is no longer required by the Android Admin runtime.
- Do not delete the old Supabase project yet; keep it as a backup/reference.
- Do not put MySQL passwords, PHP secrets, or Cloudinary API secrets in the APK.
