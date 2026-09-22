# Laravel Admin API Patch

Copy these files into the **same Fol-Bazar-Laravel project** that runs the public website.

Then run:

```bash
php artisan migrate
php artisan optimize:clear
```

Create an admin securely:

```bash
php artisan folbazar:create-admin admin@yourdomain.com
```

Set the Android app's production API URL to:

```properties
ADMIN_API_BASE_URL=https://yourdomain.com/api
```

The patch does not create a separate database or separate PHP backend. It uses the website's existing Laravel database and Sanctum authentication.
