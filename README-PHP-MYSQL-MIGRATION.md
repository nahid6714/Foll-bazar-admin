

### Kotlin build fix

The Android PHP/MySQL build must not compile the legacy `SupabaseClient.kt`. The app build script now removes that stale file automatically during `preBuild` if it exists in an older checkout. If your Git branch still contains that file, it can also be deleted manually from `app/src/main/java/com/folbazar/admin/data/SupabaseClient.kt`.
