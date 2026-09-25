# Fol Bazar Admin — Upload Error Handling Update

## What changed

- Reworked `ServerStorageClient.kt` upload handling.
- Upload now uses an 8 MB client-side limit to match the PHP backend configuration.
- Added explicit handling for missing login token, missing file access, empty files, invalid folder, timeout, DNS, connection, HTTP 401/403/404/413/5xx, invalid JSON, empty response, and missing returned image URL.
- Upload requests include the original selected filename and an `X-FolBazar-Client: android-admin` diagnostic header.
- Server response body and HTTP status are surfaced in the app when an upload fails.
- Added an in-app detailed upload error dialog with a Copy button.
- Product image upload keeps the detailed error in the existing product form error area.
- Banner image upload and Settings logo upload now show the same detailed error dialog.
- No database schema changes are required.
- No backend endpoint URL changes are required.

## Current production API

`https://lakebazar.com/backend/api`

## Upload endpoint

`https://lakebazar.com/backend/api/upload.php`
