<?php
require __DIR__ . '/bootstrap.php';

try {
    admin_user();
    global $config;

    if (!isset($_FILES['file'])) {
        $post = $_SERVER['CONTENT_LENGTH'] ?? 'unknown';
        fail('File required. PHP did not receive an uploaded file. Content-Length: ' . $post, 400);
    }

    $f = $_FILES['file'];
    $error = (int)($f['error'] ?? UPLOAD_ERR_NO_FILE);

    if ($error !== UPLOAD_ERR_OK) {
        $messages = [
            UPLOAD_ERR_INI_SIZE => 'File is larger than PHP upload_max_filesize.',
            UPLOAD_ERR_FORM_SIZE => 'File is larger than the form upload limit.',
            UPLOAD_ERR_PARTIAL => 'File upload was only partially completed.',
            UPLOAD_ERR_NO_FILE => 'No file was received by PHP.',
            UPLOAD_ERR_NO_TMP_DIR => 'PHP temporary upload directory is missing.',
            UPLOAD_ERR_CANT_WRITE => 'PHP could not write the temporary upload file.',
            UPLOAD_ERR_EXTENSION => 'A PHP extension stopped the upload.',
        ];
        fail($messages[$error] ?? ('Upload failed. PHP error code: ' . $error), 400);
    }

    $size = (int)($f['size'] ?? 0);
    $max = (int)($config['max_upload_bytes'] ?? (8 * 1024 * 1024));
    if ($size <= 0) fail('Uploaded file is empty.', 400);
    if ($size > $max) fail('File too large. Maximum allowed: ' . $max . ' bytes.', 413);

    $tmp = (string)($f['tmp_name'] ?? '');
    if ($tmp === '' || !is_uploaded_file($tmp)) {
        fail('PHP received an invalid upload temporary file.', 400);
    }

    $mime = (new finfo(FILEINFO_MIME_TYPE))->file($tmp);
    $allowed = [
        'image/jpeg' => 'jpg',
        'image/png'  => 'png',
        'image/webp' => 'webp',
        'image/gif'  => 'gif',
    ];
    if (!isset($allowed[$mime])) {
        fail('Only JPG, PNG, WEBP and GIF images are allowed. Detected MIME: ' . ($mime ?: 'unknown'), 415);
    }

    $sub = preg_replace('/[^a-z0-9_-]/i', '', $_POST['folder'] ?? 'products') ?: 'products';
    if (!in_array($sub, ['products', 'banners', 'categories'], true)) $sub = 'products';

    $dir = rtrim((string)$config['upload_dir'], '/') . '/' . $sub;
    if (!is_dir($dir) && !mkdir($dir, 0755, true)) {
        fail('Could not create upload directory: ' . $dir, 500);
    }
    if (!is_writable($dir)) {
        fail('Upload directory is not writable by PHP: ' . $dir . '. Check cPanel permissions/ownership.', 500);
    }

    $name = bin2hex(random_bytes(12)) . '.' . $allowed[$mime];
    $path = $dir . '/' . $name;

    if (!move_uploaded_file($tmp, $path)) {
        fail('PHP received the image but could not move it into: ' . $dir, 500);
    }

    @chmod($path, 0644);
    if (!is_file($path) || filesize($path) <= 0) {
        fail('Upload move reported success but the saved file could not be verified: ' . $path, 500);
    }

    $base = rtrim((string)$config['upload_url'], '/') . '/' . $sub . '/' . $name;
    ok([
        'url' => $base,
        'path' => $path,
        'filename' => $name,
        'mime' => $mime,
        'size' => $size,
    ]);
} catch (Throwable $e) {
    error_log('FOLBAZAR UPLOAD ERROR: ' . $e->getMessage() . ' | FILE: ' . $e->getFile() . ' | LINE: ' . $e->getLine());
    fail('Server error: ' . $e->getMessage(), 500);
}
