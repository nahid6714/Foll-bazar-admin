# Build fixes — 2026-09-23

Fixed the Kotlin compilation errors shown by GitHub Actions in the Android `app` module:

1. Added missing `buildJsonObject` and `JsonPrimitive` imports to `PhpAdminClient.kt`.
2. Made the `JsonElement` `when` in `Repository.kt` exhaustive with `else -> Unit`.
3. Removed experimental loop-control usage from `ServerStorageClient.kt` while preserving upload endpoint fallback behavior.

The GitHub Actions workflow builds `:app`, so these fixes are applied to the root `app/src/main/...` source used by the workflow. The duplicate `admin_src` copy was also kept compile-clean where applicable.

The image upload fallback remains:
- `https://lakebazar.com/api/upload`
- `https://lakebazar.com/api/admin.php?action=upload`

Product images are still saved through the existing `image_url` and `gallery_urls` update flow.
