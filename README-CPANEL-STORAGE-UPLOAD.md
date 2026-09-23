# cPanel Laravel image upload

## Android Admin app
- Upload endpoint: `POST https://lakebazar.com/api/upload`
- Authentication: `Authorization: Bearer <admin-token>`
- Multipart fields: `file`, `folder`
- Folders: `products`, `categories`, `banners`, `settings`
- Response expected: `{ "ok": true, "data": { "url": "...", "path": "..." } }`

## cPanel/Laravel
The Laravel upload disk writes directly to `public/storage`, so the server does not require `php artisan storage:link` for these admin uploads.

Make sure this directory exists and is writable by the PHP/web-server user:

`public/storage`

Uploaded files will appear under:

- `public/storage/products/`
- `public/storage/categories/`
- `public/storage/banners/`
- `public/storage/settings/`

The public URL format is:

`https://lakebazar.com/storage/<folder>/<filename>`

Do not put `.env` or database passwords into GitHub.
