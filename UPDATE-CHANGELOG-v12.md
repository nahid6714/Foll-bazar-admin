# Fol Bazar Admin v12 — audit and fixes

## Confirmed source problems fixed
1. Image upload was calling the legacy `admin.php?action=upload` endpoint and expected `url` at the JSON root. Laravel returns the admin response inside `data`, so successful uploads were treated as failures. The client now uses `/api/upload` and unwraps `{ok,data:{url,path}}`.
2. Product uploads did not send a folder. The app now sends `products`, while categories and banners send their own folder names.
3. Admin login was pointing at a legacy action that the current Laravel `LegacyController` does not implement. Login now uses `/api/auth/login` and verifies `role=admin`.
4. CRUD was calling `admin.php?resource=...`, while Laravel exposes the current admin CRUD under `/api/admin/*`. The client now uses those routes.
5. `Product` was missing `galleryUrls` even though the repository/UI already used it.
6. `OrderItem` was referenced but missing from the Android model source. It is now defined and order details read items from the admin order endpoint.
7. Site settings used obsolete `key/value` field names. The client now uses `setting_key/setting_value`.
8. Customer responses contain a nested `profile`; the parser now reads that structure.
9. Wishlist analytics needed an admin endpoint; Laravel now provides `/api/admin/wishlist-summary`.
10. Complaint admin notes were exposed by the UI but not stored by the Laravel backend. A migration/model/controller update adds `admin_note`.
11. Category descriptions were shown in the UI but absent from the database/model/controller. A migration/model/controller update adds `description`.
12. The banner UI offered `event`, but Laravel accepts only `hero` and `promo`. The invalid option was removed.
13. Unused Supabase/Cloudinary Android clients referenced BuildConfig fields that were not defined. They were removed from the Android module.
14. The app update checker now reads a cPanel-hosted `update.json`.
15. The Android release build now has a production API default and configurable version/update properties.
16. Cleartext HTTP is no longer enabled in the Android manifest.
17. Laravel runtime `.keep` files preserve `storage/framework/sessions`, `cache/data`, `views`, logs and `bootstrap/cache` when the project is transferred as a ZIP.
