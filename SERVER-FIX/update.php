<?php
// Public update manifest endpoint. No admin login is required here.
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Access-Control-Allow-Origin: *');
$manifest = '/home/lakebazar/public_html/backend/updates/update.json';
if (!is_file($manifest)) {
    http_response_code(404);
    echo json_encode(['ok'=>false,'error'=>'Update manifest not found','path'=>$manifest], JSON_UNESCAPED_SLASHES|JSON_UNESCAPED_UNICODE);
    exit;
}
$raw = file_get_contents($manifest);
$data = json_decode($raw, true);
if (!is_array($data)) {
    http_response_code(500);
    echo json_encode(['ok'=>false,'error'=>'Update manifest JSON is invalid'], JSON_UNESCAPED_SLASHES|JSON_UNESCAPED_UNICODE);
    exit;
}
$required = ['versionCode','versionName','downloadUrl'];
foreach ($required as $key) if (!isset($data[$key]) || $data[$key] === '') { http_response_code(500); echo json_encode(['ok'=>false,'error'=>'Missing field: '.$key]); exit; }
if (!preg_match('~^https?://~i', (string)$data['downloadUrl'])) {
    $data['downloadUrl'] = 'https://lakebazar.com/backend/updates/' . ltrim((string)$data['downloadUrl'],'/');
}
echo json_encode($data, JSON_UNESCAPED_SLASHES|JSON_UNESCAPED_UNICODE);
