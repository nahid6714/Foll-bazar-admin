# Fol Bazar Admin Android v2.2

## Backend cleanup
- Removed legacy Cloudinary client.
- Removed legacy Supabase client.
- Admin data operations use `PhpAdminClient` -> PHP/Laravel-compatible Admin API.
- Image uploads use `ServerStorageClient` -> server local storage.
- GitHub Actions checks Kotlin source for stale `CLOUDINARY` / `SUPABASE` references before compilation.

## Production API
Default API base URL:
`https://lakebazar.com/api`

Do not commit production database credentials or `.env` secrets.
