-- Run once on lakebazar_foll_bazar_new
ALTER TABLE site_banners ADD COLUMN category_id CHAR(36) NULL AFTER link_url;
CREATE INDEX idx_site_banners_category_id ON site_banners(category_id);
