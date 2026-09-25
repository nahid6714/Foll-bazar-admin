# Fol Bazar Admin v14.2 — Upload Thread Fix

## Fixed
- Image upload network/file work now runs inside `Dispatchers.IO`.
- Fixes Android `NetworkOnMainThreadException` shown in the product editor.
- Existing detailed upload error handling is preserved.
- HTTP status, endpoint, Content-Type, server JSON error, timeout, DNS, connection, file-size, MIME and authentication errors remain visible in the app.

## Why
The previous upload client was a `suspend` function but used blocking OkHttp `execute()` directly. Calling it from a Compose coroutine inherited the Main dispatcher, so Android blocked the network call and returned `NetworkOnMainThreadException`.
