-- Fol Bazar Admin v19: banner -> product category mapping
-- Run once in lakebazar_foll_bazar_new if site_banners does not already have category_id.
ALTER TABLE site_banners
    ADD COLUMN category_id CHAR(36) NULL AFTER link_url,
    ADD INDEX idx_site_banners_category_id (category_id);
