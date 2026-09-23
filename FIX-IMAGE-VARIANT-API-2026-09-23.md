# Image + Variant API compatibility fix — 2026-09-23

## What was fixed

- Product variant GET/POST/update/delete now tries the Laravel `admin-data` endpoint first, then the PHP/MySQL `admin.php?resource=...` endpoint, and finally the older `manage.php` endpoint.
- The previous `Unknown admin action` 404 from the legacy `manage.php` endpoint therefore no longer becomes the first/only compatibility path.
- Legacy PATCH fallback now sends the record `id` in the JSON body as well as the query string, so the PHP API updates the existing row instead of attempting to insert a new row.
- Image upload accepts both `{ok:true,url:...}` and `{data:{url:...}}` response shapes and retries the legacy PHP upload endpoint for common compatibility failures.
- PHP upload URL generation now falls back to the current request host if `APP_URL` is missing, preventing an empty image URL.

## Important

The Android client can only use an API endpoint that is actually deployed on the server. If `https://lakebazar.com/api/admin-data` is still missing, deploy the matching `public/api/admin.php` / Laravel API changes from this project as well as the APK.
