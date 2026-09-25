DIRECT IN-APP UPDATE SETUP

1. Create: /public_html/backend/updates/
2. Upload update.php to: /public_html/backend/api/update.php
3. Upload update.json to: /public_html/backend/updates/update.json
4. Upload the signed APK to: /public_html/backend/updates/
5. The app now checks:
   https://lakebazar.com/backend/api/update.php?action=manifest

Important: the APK must be signed with the same signing certificate as the installed app.
If the APK is unsigned or signed with another key, Android will not install it as an update.
