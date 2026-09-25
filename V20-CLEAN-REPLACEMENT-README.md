# Fol Bazar Admin v20 — Clean Receipt Source

This package is a complete clean project replacement for the previous source.

## Important
Do NOT merge this folder into the old Android project.
Replace the GitHub repository's project files with this `FolBazar-Admin` project.

## Receipt compile cleanup
The legacy files below must NOT exist:
- `app/src/main/java/com/folbazar/admin/data/ReceiptPaperSize.kt`
- `app/src/main/java/com/folbazar/admin/data/ReceiptHtmlBuilder.kt`

`ReceiptPaperSize` is defined only once, inside:
- `app/src/main/java/com/folbazar/admin/data/OrderReceiptExporter.kt`

## Before pushing to GitHub
Search the repository for:
- `enum class ReceiptPaperSize`
- `ReceiptHtmlBuilder`

There should be exactly one `enum class ReceiptPaperSize` and no `ReceiptHtmlBuilder.kt`.
