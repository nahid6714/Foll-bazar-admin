<?php
// Put this file at: public_html/backend/api/update.php
// Put update.json + APK files at: public_html/backend/updates/
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');

$manifest = __DIR__ . '/../updates/update.json';

if (!is_file($manifest)) {
    http_response_code(404);
    echo json_encode([
        'ok' => false,
        'error' => 'Update manifest not found. Upload backend/updates/update.json.'
    ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

$raw = file_get_contents($manifest);
$data = json_decode($raw, true);
if (!is_array($data)) {
    http_response_code(500);
    echo json_encode([
        'ok' => false,
        'error' => 'Update manifest JSON is invalid.'
    ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

$versionCode = (int)($data['versionCode'] ?? 0);
$versionName = (string)($data['versionName'] ?? '');
$releaseName = (string)($data['releaseName'] ?? ('Fol Bazar Admin ' . $versionName));
$download = trim((string)($data['downloadUrl'] ?? ''));
$releaseUrl = trim((string)($data['releaseUrl'] ?? ''));

if ($versionCode <= 0 || $download === '') {
    http_response_code(500);
    echo json_encode([
        'ok' => false,
        'error' => 'Update manifest requires versionCode and downloadUrl.'
    ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

if (!preg_match('#^https?://#i', $download)) {
    $download = 'https://lakebazar.com/backend/updates/' . ltrim($download, '/');
}

if ($releaseUrl !== '' && !preg_match('#^https?://#i', $releaseUrl)) {
    $releaseUrl = 'https://lakebazar.com/backend/updates/' . ltrim($releaseUrl, '/');
}

echo json_encode([
    'versionCode' => $versionCode,
    'versionName' => $versionName,
    'releaseName' => $releaseName,
    'downloadUrl' => $download,
    'releaseUrl' => $releaseUrl,
], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
