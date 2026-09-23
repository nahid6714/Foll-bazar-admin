<?php
declare(strict_types=1);

/*
 * Fol Bazar Admin API
 * Shared backend contract for the Android Admin app and the Laravel website.
 * This file is served from public/api/admin.php and reads the Laravel root .env.
 */

function loadDotEnv(string $file): array {
    if (!is_file($file)) return [];
    $out = [];
    foreach (file($file, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES) ?: [] as $line) {
        $line = trim($line);
        if ($line === '' || str_starts_with($line, '#')) continue;
        if (!str_contains($line, '=')) continue;
        [$key, $value] = explode('=', $line, 2);
        $key = trim($key);
        $value = trim($value);
        if ($value !== '' && (($value[0] === '"' && substr($value, -1) === '"') || ($value[0] === "'" && substr($value, -1) === "'"))) {
            $value = substr($value, 1, -1);
        }
        $out[$key] = $value;
    }
    return $out;
}

$env = loadDotEnv(dirname(__DIR__, 2) . '/.env');
$config = [
    'db_host' => $env['DB_HOST'] ?? '127.0.0.1',
    'db_port' => $env['DB_PORT'] ?? '3306',
    'db_name' => $env['DB_DATABASE'] ?? '',
    'db_user' => $env['DB_USERNAME'] ?? '',
    'db_pass' => $env['DB_PASSWORD'] ?? '',
    'app_url' => rtrim($env['APP_URL'] ?? '', '/'),
];

if ($config['db_name'] === '' || $config['db_user'] === '') {
    respond(['ok' => false, 'message' => 'Database configuration missing from Laravel .env'], 500);
}

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

$origin = $_SERVER['HTTP_ORIGIN'] ?? '';
$allowedOrigin = (string)($config['app_url'] ?? '');
if ($origin !== '') {
    $allowed = [$allowedOrigin, preg_replace('#^https://#', 'https://www.', $allowedOrigin)];
    if (in_array($origin, array_filter($allowed), true)) {
        header('Access-Control-Allow-Origin: ' . $origin);
        header('Vary: Origin');
    }
}
header('Access-Control-Allow-Headers: Content-Type, Authorization');
header('Access-Control-Allow-Methods: GET, POST, PATCH, DELETE, OPTIONS');
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

try {
    $pdo = new PDO(
        'mysql:host=' . ($config['db_host'] ?? '127.0.0.1') .
        ';port=' . ($config['db_port'] ?? '3306') .
        ';dbname=' . ($config['db_name'] ?? '') .
        ';charset=' . ($config['db_charset'] ?? 'utf8mb4'),
        (string)($config['db_user'] ?? 'root'),
        (string)($config['db_pass'] ?? ''),
        [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC, PDO::ATTR_EMULATE_PREPARES => false]
    );
} catch (Throwable $e) {
    respond(['ok' => false, 'message' => 'Database connection failed'], 500);
}

function uuidv4(): string {
    $data = random_bytes(16);
    $data[6] = chr((ord($data[6]) & 0x0f) | 0x40);
    $data[8] = chr((ord($data[8]) & 0x3f) | 0x80);
    return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($data), 4));
}

function respond(array $payload, int $status = 200): never {
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}
function body(): array {
    $raw = file_get_contents('php://input') ?: '{}';
    $v = json_decode($raw, true);
    return is_array($v) ? $v : [];
}
function token(): string {
    $h = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (preg_match('/Bearer\s+(.+)/i', $h, $m)) return trim($m[1]);
    return '';
}
function qid(): ?string {
    $v = $_GET['id'] ?? null;
    return is_string($v) && $v !== '' ? $v : null;
}
function requireAdmin(PDO $pdo): array {
    $raw = token();
    if ($raw === '') respond(['ok' => false, 'message' => 'Authentication required'], 401);
    $hash = hash('sha256', $raw);
    $stmt = $pdo->prepare('SELECT u.id,u.email,u.name,u.role,s.expires_at FROM admin_sessions s JOIN admin_users u ON u.id=s.admin_user_id WHERE s.token_hash=? AND u.is_active=1 LIMIT 1');
    $stmt->execute([$hash]);
    $row = $stmt->fetch();
    if (!$row || strtotime((string)$row['expires_at']) < time()) respond(['ok' => false, 'message' => 'Admin session expired'], 401);
    return $row;
}
function tableExists(PDO $pdo, string $table): bool {
    $s = $pdo->prepare('SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?');
    $s->execute([$table]); return (int)$s->fetchColumn() > 0;
}
function decodeJsonValue($value) {
    if (is_string($value)) {
        $decoded = json_decode($value, true);
        if (json_last_error() === JSON_ERROR_NONE) return $decoded;
    }
    return $value;
}
function encodeDbValue(string $column, $value) {
    if ($column === 'gallery_urls' || $column === 'value') {
        return json_encode($value, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    }
    return $value;
}
function normalizeRow(array $row): array {
    foreach (['gallery_urls','value'] as $key) {
        if (array_key_exists($key, $row)) $row[$key] = decodeJsonValue($row[$key]);
    }
    return $row;
}
function allowedFields(string $resource, array $input): array {
    $maps = [
        'categories' => ['name','slug','image_url','description','is_active','sort_order'],
        'products' => ['name','slug','description','price','old_price','stock_quantity','category_id','image_url','gallery_urls','is_active','is_featured','is_flash_sale','is_hot_deal','sort_order'],
        'product_variants' => ['product_id','label','weight_grams','price','old_price','stock_quantity','sku','is_active','sort_order'],
        'orders' => ['status','payment_status'],
        'profiles' => ['role'],
        'complaints' => ['status','admin_note'],
        'coupons' => ['code','title','discount_type','discount_value','min_order','max_discount','usage_limit','is_active','starts_at','expires_at'],
        'site_banners' => ['banner_type','title','alt_text','image_url','link_url','sort_order','is_active','width_percent','height_px'],
        'site_settings' => ['key','value'],
    ];
    $allowed = $maps[$resource] ?? [];
    $out = [];
    foreach ($allowed as $key) if (array_key_exists($key, $input)) $out[$key] = encodeDbValue($key, $input[$key]);
    return $out;
}
function insertRow(PDO $pdo, string $table, array $fields): array {
    if (!$fields) respond(['ok'=>false,'message'=>'No writable fields'],400);
    if (in_array($table, ['categories','products','product_variants','coupons','site_banners'], true) && array_key_exists('id', $fields) && $fields['id'] === null) $fields['id'] = uuidv4();
    $cols = array_keys($fields);
    if (in_array('id', $cols, true) && $fields['id'] === null) $fields['id'] = uuidv4();
    $cols = array_keys($fields);
    $quoted = array_map(fn($x) => '`'.str_replace('`','``',$x).'`', $cols);
    $marks = implode(',', array_fill(0,count($cols),'?'));
    $stmt=$pdo->prepare('INSERT INTO `'.$table.'` ('.implode(',',$quoted).') VALUES ('.$marks.')');
    $stmt->execute(array_values($fields));
    $id = $pdo->lastInsertId();
    if ($id === '' || $id === '0') $id = $fields['id'] ?? null;
    if ($id !== null) {
        $s=$pdo->prepare('SELECT * FROM `'.$table.'` WHERE id=? LIMIT 1'); $s->execute([$id]); $r=$s->fetch();
        if ($r) return normalizeRow($r);
    }
    return normalizeRow($fields);
}
function updateRow(PDO $pdo, string $table, string $id, array $fields): array {
    if (!$fields) respond(['ok'=>false,'message'=>'No writable fields'],400);
    $parts=[];$vals=[];
    foreach($fields as $k=>$v){$parts[]='`'.str_replace('`','``',$k).'`=?';$vals[]=$v;}
    $vals[]=$id;
    $stmt=$pdo->prepare('UPDATE `'.$table.'` SET '.implode(',',$parts).' WHERE id=?');
    $stmt->execute($vals);
    $s=$pdo->prepare('SELECT * FROM `'.$table.'` WHERE id=? LIMIT 1');$s->execute([$id]);$r=$s->fetch();
    if(!$r) respond(['ok'=>false,'message'=>'Record not found'],404);
    return normalizeRow($r);
}
function deleteRow(PDO $pdo,string $table,string $id): void {
    $s=$pdo->prepare('DELETE FROM `'.$table.'` WHERE id=?');$s->execute([$id]);
}

$action = (string)($_GET['action'] ?? '');
if ($action === 'login') {
    $in = body(); $email = trim((string)($in['email'] ?? '')); $password=(string)($in['password'] ?? '');
    if ($email === '' || $password === '') respond(['ok'=>false,'message'=>'Email and password are required'],422);
    $s=$pdo->prepare('SELECT id,email,name,role,password_hash,is_active FROM admin_users WHERE email=? LIMIT 1');$s->execute([$email]);$u=$s->fetch();
    if(!$u || !(int)$u['is_active'] || !password_verify($password,(string)$u['password_hash']) || $u['role'] !== 'admin') respond(['ok'=>false,'message'=>'Admin login failed'],401);
    $raw=bin2hex(random_bytes(32));$hash=hash('sha256',$raw);$expires=date('Y-m-d H:i:s',time()+60*60*24*30);
    $s=$pdo->prepare('INSERT INTO admin_sessions (id,admin_user_id,token_hash,expires_at) VALUES (UUID(),?,?,?)');$s->execute([$u['id'],$hash,$expires]);
    respond(['ok'=>true,'access_token'=>$raw,'user_id'=>(string)$u['id'],'email'=>(string)$u['email'],'name'=>(string)$u['name']]);
}
if ($action === 'logout') {
    $raw=token(); if($raw!==''){ $s=$pdo->prepare('DELETE FROM admin_sessions WHERE token_hash=?');$s->execute([hash('sha256',$raw)]); }
    respond(['ok'=>true]);
}

requireAdmin($pdo);

if ($action === 'upload') {
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') respond(['ok'=>false,'message'=>'POST required'],405);
    if (!isset($_FILES['file']) || !is_array($_FILES['file'])) respond(['ok'=>false,'message'=>'Image file is required'],422);

    $file = $_FILES['file'];
    if (($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
        respond(['ok'=>false,'message'=>'Image upload failed'],422);
    }
    $maxBytes = 10 * 1024 * 1024;
    if ((int)($file['size'] ?? 0) > $maxBytes) {
        respond(['ok'=>false,'message'=>'Image is larger than 10 MB'],422);
    }

    $tmp = (string)($file['tmp_name'] ?? '');
    $finfo = new finfo(FILEINFO_MIME_TYPE);
    $mime = $finfo->file($tmp) ?: '';
    $allowed = [
        'image/jpeg' => 'jpg',
        'image/png' => 'png',
        'image/webp' => 'webp',
        'image/gif' => 'gif',
    ];
    if (!isset($allowed[$mime])) respond(['ok'=>false,'message'=>'Only JPG, PNG, WEBP or GIF images are allowed'],422);

    $uploadDir = dirname(__DIR__) . '/uploads/admin';
    if (!is_dir($uploadDir) && !mkdir($uploadDir, 0755, true) && !is_dir($uploadDir)) {
        respond(['ok'=>false,'message'=>'Upload directory could not be created'],500);
    }

    $filename = bin2hex(random_bytes(16)) . '.' . $allowed[$mime];
    $destination = $uploadDir . '/' . $filename;
    if (!move_uploaded_file($tmp, $destination)) {
        respond(['ok'=>false,'message'=>'Could not save uploaded image'],500);
    }

    $base = rtrim((string)($config['app_url'] ?? ''), '/');
    if ($base === '') {
        $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
        $host = (string)($_SERVER['HTTP_HOST'] ?? '');
        if ($host !== '') $base = $scheme . '://' . $host;
    }
    $url = $base . '/uploads/admin/' . rawurlencode($filename);
    respond(['ok'=>true,'url'=>$url],201);
}
$resource = (string)($_GET['resource'] ?? '');
$method = $_SERVER['REQUEST_METHOD'];
$allowedTables=['categories','products','product_variants','orders','order_items','profiles','complaints','wishlists','coupons','coupon_usages','site_banners','site_settings'];
if(!in_array($resource,$allowedTables,true)) respond(['ok'=>false,'message'=>'Unknown resource'],404);
if(!tableExists($pdo,$resource)) respond(['ok'=>false,'message'=>'Table not found'],404);

if ($method === 'GET') {
    $id=qid();
    if($resource==='products'){
        $sql='SELECT p.*, c.name AS category_name FROM products p LEFT JOIN categories c ON c.id=p.category_id';$where=[];$vals=[];
        if($id){$where[]='p.id=?';$vals[]=$id;}
        if(isset($_GET['active'])){$where[]='p.is_active=?';$vals[]=(int)$_GET['active'];}
        $sql.= $where?' WHERE '.implode(' AND ',$where):''; $sql.=' ORDER BY p.created_at DESC';
    } elseif($resource==='order_items'){
        $sql='SELECT oi.*, p.image_url FROM order_items oi LEFT JOIN products p ON p.id=oi.product_id';$where=[];$vals=[];
        if(isset($_GET['order_id'])){$where[]='oi.order_id=?';$vals[]=$_GET['order_id'];}
        if($id){$where[]='oi.id=?';$vals[]=$id;}
        $sql.=$where?' WHERE '.implode(' AND ',$where):''; $sql.=' ORDER BY oi.created_at ASC';
    } else {
        $sql='SELECT * FROM `'.$resource.'`';$where=[];$vals=[];
        if($id){$where[]='id=?';$vals[]=$id;}
        foreach(['product_id','order_id','key'] as $f) if(isset($_GET[$f])){$where[]='`'.$f.'`=?';$vals[]=$_GET[$f];}
        $sql.=$where?' WHERE '.implode(' AND ',$where):'';
        if($resource==='categories')$sql.=' ORDER BY sort_order ASC,name ASC';
        elseif($resource==='orders')$sql.=' ORDER BY created_at DESC';
        elseif($resource==='profiles')$sql.=' ORDER BY created_at DESC';
        elseif($resource==='complaints')$sql.=' ORDER BY created_at DESC';
        elseif($resource==='coupons')$sql.=' ORDER BY created_at DESC';
        elseif($resource==='site_banners')$sql.=' ORDER BY banner_type ASC,sort_order ASC,created_at DESC';
        elseif($resource==='site_settings')$sql.=' ORDER BY `key` ASC';
    }
    $s=$pdo->prepare($sql);$s->execute($vals);$rows=array_map('normalizeRow',$s->fetchAll());
    respond($rows);
}

if ($method === 'POST') {
    $in=body();
    if($resource==='products' && empty($in['slug'])) $in['slug']=trim(preg_replace('/[^a-z0-9\-]+/i','-',strtolower((string)($in['name']??'item'))),'-').'-'.substr((string)time(),-6);
    if($resource==='categories' && empty($in['slug'])) $in['slug']=trim(preg_replace('/[^a-z0-9\-]+/i','-',strtolower((string)($in['name']??'category'))),'-').'-'.substr((string)time(),-6);
    $fields=allowedFields($resource,$in);
    if(in_array($resource,['products','categories','product_variants','site_banners','coupons'],true) && tableHasId($pdo,$resource)) $fields['id']=$in['id']??null;
    $row=insertRow($pdo,$resource,$fields);
    respond([$row],201);
}
if ($method === 'PATCH') {
    $id=qid(); if(!$id) respond(['ok'=>false,'message'=>'id is required'],400);
    $row=updateRow($pdo,$resource,$id,allowedFields($resource,body()));respond([$row]);
}
if ($method === 'DELETE') {
    $id=qid(); if(!$id) respond(['ok'=>false,'message'=>'id is required'],400);deleteRow($pdo,$resource,$id);respond(['ok'=>true]);
}
respond(['ok'=>false,'message'=>'Method not allowed'],405);

function tableHasId(PDO $pdo,string $table): bool {
    $s=$pdo->prepare('SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=\'id\'');$s->execute([$table]);return (bool)$s->fetchColumn();
}
