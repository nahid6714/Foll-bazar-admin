# Fol Bazar Admin — Banner Image Editor

- Width/Height editing controls have been removed from the Banner Editor.
- Admin keeps the normal/default banner frame for preview.
- Preview uses crop behavior matching the existing website frame.
- Admin can change the banner image and save it without changing stored banner dimensions.
- Existing PHP/MySQL `width_percent` and `height_px` values are preserved.
- New banners rely on the database's normal/default dimension values.
- Website source files are not changed by this Admin-only update.


### V41 UI cleanup
- Image preview frames use small 4–6dp corners instead of large rounded shapes.
- Image previews use `ContentScale.Fit` so the full uploaded image remains visible.
- Image upload/delete actions use full-width rows on narrow screens to avoid awkward text wrapping.
- Banner width/height controls remain removed; the website uses its responsive content width.
- Recommended banner upload sizes: Hero 1600×600 px, Promo 1600×350 px.
