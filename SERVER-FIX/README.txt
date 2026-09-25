SERVER SETUP
1. Replace /public_html/backend/api/upload.php with SERVER-FIX/upload.php.
2. Put update.php at /public_html/backend/api/update.php.
3. Create /public_html/backend/updates/.
4. Put update.json and the release APK in /public_html/backend/updates/.
5. update.json downloadUrl can be just the APK filename.
6. APK must be signed with the same key as the installed app.
7. Ensure backend/uploads/products, banners, categories are writable by PHP.
