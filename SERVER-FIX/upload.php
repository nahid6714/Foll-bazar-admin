<?php
require __DIR__ . '/bootstrap.php';
try {
    admin_user();
    $max = isset($config['max_upload_bytes']) ? (int)$config['max_upload_bytes'] : 8*1024*1024;
    if (!isset($_FILES['file'])) fail('File required');
    $f = $_FILES['file'];
    if (($f['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) fail('PHP upload error code: ' . (string)($f['error'] ?? -1));
    if ((int)$f['size'] <= 0) fail('Uploaded file is empty');
    if ((int)$f['size'] > $max) fail('File too large. Limit: ' . $max . ' bytes');
    if (!is_uploaded_file($f['tmp_name'])) fail('PHP did not mark the file as a valid uploaded file');
    if (!class_exists('finfo')) fail('PHP Fileinfo extension is not enabled');
    $mime = (new finfo(FILEINFO_MIME_TYPE))->file($f['tmp_name']);
    $allowed = ['image/jpeg'=>'jpg','image/png'=>'png','image/webp'=>'webp','image/gif'=>'gif'];
    if (!isset($allowed[$mime])) fail('Only JPG, PNG, WEBP and GIF images are allowed. Detected: ' . $mime);
    $sub = preg_replace('/[^a-z0-9_-]/i','', (string)($_POST['folder'] ?? 'products')) ?: 'products';
    if (!in_array($sub,['products','banners','categories'],true)) $sub='products';
    $dir = '/home/lakebazar/public_html/backend/uploads/' . $sub;
    $urlBase = 'https://lakebazar.com/backend/uploads/' . $sub;
    if (!is_dir($dir) && !mkdir($dir,0755,true)) fail('Could not create upload directory: ' . $dir,500);
    if (!is_writable($dir)) fail('Upload directory is not writable: ' . $dir,500);
    $name = bin2hex(random_bytes(12)) . '.' . $allowed[$mime];
    $path = $dir . '/' . $name;
    if (!move_uploaded_file($f['tmp_name'],$path)) fail('move_uploaded_file failed. Check folder ownership/permissions.',500);
    if (!is_file($path) || filesize($path) <= 0) fail('File was not created on server',500);
    ok(['url'=>$urlBase.'/'.$name,'path'=>$path,'size'=>(int)filesize($path),'mime'=>$mime]);
} catch (Throwable $e) {
    error_log('FOL BAZAR UPLOAD ERROR: '.$e->getMessage().' | FILE: '.$e->getFile().' | LINE: '.$e->getLine());
    fail('Server upload error: '.$e->getMessage(),500);
}
