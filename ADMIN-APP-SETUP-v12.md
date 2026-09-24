# Fol Bazar Admin v12 — Laravel API build

## API
The Android app is now Laravel-only. Default production API:

`https://lakebazar.com/api`

Optional `local.properties` values:

```properties
ADMIN_API_BASE_URL=https://lakebazar.com/api
ADMIN_UPDATE_BASE_URL=https://lakebazar.com/admin-app
APP_VERSION_CODE=1
APP_VERSION_NAME=1.1.1
```

## Image uploads
The app sends images to:

`POST /api/upload`

with an authenticated admin Bearer token and one of:

- `folder=products`
- `folder=categories`
- `folder=banners`

The app reads the Laravel `{ok:true,data:{url,path}}` response and stores the returned public URL in the product/category/banner record.

## Important fixes
- Admin login uses `/api/auth/login` instead of the obsolete legacy login path.
- CRUD uses the current `/api/admin/*` Laravel routes.
- Product gallery URLs are supported in the Android model.
- Order details read items from `/api/admin/orders/{id}`.
- Site settings use `setting_key` / `setting_value`.
- Wishlist analytics uses `/api/admin/wishlist-summary`.
- Complaint admin notes are supported.
- Category descriptions are supported.
- Legacy cloud/database runtime dependencies were removed from the Android module.

## App updates from cPanel
Place an `update.json` and the signed APK under the configured cPanel update directory, by default:

`public_html/admin-app/`

Example `update.json`:

```json
{
  "versionCode": 2,
  "versionName": "1.1.2",
  "releaseName": "Fol Bazar Admin 1.1.2",
  "downloadUrl": "FolBazar-Admin-1.1.2.apk",
  "releaseUrl": ""
}
```

The APK must be signed with the same signing certificate as the installed app for in-app update installation to pass validation.
