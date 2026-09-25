# Fol Bazar Admin v15

- Product image picker now shows the selected local image immediately while upload is running.
- Successful uploads replace the local preview with the server URL.
- Failed uploads keep the local preview and show the detailed server/client error.
- PHP upload diagnostics now distinguish PHP upload limits, temp-file errors, MIME rejection, directory permissions, and failed file moves.
- Added a server-side file existence check after upload.
- In-app updater now tries a dedicated PHP manifest endpoint first, then the legacy cPanel update.json.
- HTML returned instead of JSON is reported with the exact manifest URL and likely .htaccess/missing-file cause.
- Added SERVER-UPDATE/update.php and update.json.example for direct in-app updates without the root website SPA rewrite.
- Added setup instructions for /public_html/backend/updates/.
