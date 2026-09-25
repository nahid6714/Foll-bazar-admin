# Fol Bazar Admin v19

Implemented in the supplied v18 source:

- Removed the separate Analytics bottom-navigation tab.
- Kept Delivered Sales on Dashboard and made it open a dedicated Delivered Sales page.
- Added Today's Orders to Dashboard and made it open Orders filtered to today.
- Kept the Dashboard + Product shortcut; it now opens the New Product editor directly.
- Removed the duplicate Order shortcut beside the Product shortcut.
- Added Orders filters: All, Today, Yesterday, This Week, This Month.
- Added product-wise Delivered Sales ranking with sold quantity and sales amount.
- Added receipt export implementation using an approved FileProvider cache path.
- Updated FileProvider paths so receipt image/PDF sharing no longer fails with a configured-root error.
- Restored functional Light / Dark / Device theme preference persistence.
- Added Product Category selection to Banner create/edit UI and model/API payload.
- Preserved the existing working image upload client.

Server-side banner category note:
- The supplied Android ZIP did not contain the production banners.php backend or the live database schema migration.
- Therefore SERVER-UPDATE/site_banners_category_migration.sql and BANNERS-CATEGORY-API-NOTE.txt are included for the live backend.
- The app-side category selector is ready, but the live backend must persist category_id before the website can place a banner before a selected category.
