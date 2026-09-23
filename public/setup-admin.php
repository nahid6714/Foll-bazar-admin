<?php
declare(strict_types=1);

/*
 * One-time Fol Bazar Admin setup.
 * Open with ?key=-EpUbaXMOpwsK-SDL6YWyUPmhB_wXi-4, create the first admin, then DELETE this file.
 */

$SETUP_KEY = '-EpUbaXMOpwsK-SDL6YWyUPmhB_wXi-4';
if (!hash_equals($SETUP_KEY, (string)($_GET['key'] ?? ''))) {
    http_response_code(404);
    exit('Not found');
}

function loadDotEnv(string $file): array {
    if (!is_file($file)) return [];
    $out = [];
    foreach (file($file, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES) ?: [] as $line) {
        $line = trim($line);
        if ($line === '' || str_starts_with($line, '#') || !str_contains($line, '=')) continue;
        [$k,$v] = explode('=', $line, 2);
        $v = trim($v);
        if ($v !== '' && (($v[0] === '"' && substr($v,-1) === '"') || ($v[0] === "'" && substr($v,-1) === "'"))) $v = substr($v,1,-1);
        $out[trim($k)] = $v;
    }
    return $out;
}
$env = loadDotEnv(dirname(__DIR__) . '/.env');
try {
    $pdo = new PDO(
        'mysql:host='.($env['DB_HOST'] ?? '127.0.0.1').';port='.($env['DB_PORT'] ?? '3306').';dbname='.($env['DB_DATABASE'] ?? '').';charset=utf8mb4',
        $env['DB_USERNAME'] ?? '', $env['DB_PASSWORD'] ?? '',
        [PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]
    );
    $pdo->exec("CREATE TABLE IF NOT EXISTS admin_users (
        id CHAR(36) NOT NULL PRIMARY KEY,
        email VARCHAR(190) NOT NULL UNIQUE,
        name VARCHAR(190) NOT NULL,
        password_hash VARCHAR(255) NOT NULL,
        role VARCHAR(30) NOT NULL DEFAULT 'admin',
        is_active TINYINT(1) NOT NULL DEFAULT 1,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    $pdo->exec("CREATE TABLE IF NOT EXISTS admin_sessions (
        id CHAR(36) NOT NULL PRIMARY KEY,
        admin_user_id CHAR(36) NOT NULL,
        token_hash CHAR(64) NOT NULL UNIQUE,
        expires_at DATETIME NOT NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_admin_sessions_user (admin_user_id),
        INDEX idx_admin_sessions_expiry (expires_at),
        CONSTRAINT fk_admin_sessions_user FOREIGN KEY (admin_user_id) REFERENCES admin_users(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    $count = (int)$pdo->query('SELECT COUNT(*) FROM admin_users')->fetchColumn();
    $message = '';
    if ($_SERVER['REQUEST_METHOD'] === 'POST') {
        if ($count > 0) throw new RuntimeException('An admin account already exists. Use the existing account.');
        $email = trim((string)($_POST['email'] ?? ''));
        $name = trim((string)($_POST['name'] ?? 'Fol Bazar Admin'));
        $password = (string)($_POST['password'] ?? '');
        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) throw new RuntimeException('Valid email required.');
        if (mb_strlen($password) < 10) throw new RuntimeException('Password must be at least 10 characters.');
        if ($name === '') $name = 'Fol Bazar Admin';
        $stmt = $pdo->prepare('INSERT INTO admin_users (id,email,name,password_hash,role,is_active) VALUES (UUID(),?,?,?,?,1)');
        $stmt->execute([$email,$name,password_hash($password,PASSWORD_DEFAULT),'admin']);
        $message = 'Admin created successfully. Delete setup-admin.php now.';
        $count = 1;
    }
} catch (Throwable $e) {
    $error = $e->getMessage();
}
?>
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Fol Bazar Admin Setup</title><style>body{font-family:Arial,sans-serif;max-width:520px;margin:50px auto;padding:20px}input{width:100%;padding:12px;margin:6px 0 14px;box-sizing:border-box}button{padding:12px 18px}.ok{background:#e7f7e7;padding:12px}.err{background:#ffe7e7;padding:12px}</style></head>
<body><h2>Fol Bazar Admin Setup</h2>
<?php if (!empty($message)): ?><div class="ok"><?=htmlspecialchars($message)?></div><?php elseif (!empty($error)): ?><div class="err"><?=htmlspecialchars($error)?></div>
<?php elseif ($count > 0): ?><div class="err">An admin account already exists. Delete this file.</div>
<?php else: ?><form method="post"><label>Name</label><input name="name" value="Fol Bazar Admin" required><label>Email</label><input type="email" name="email" required><label>Password</label><input type="password" name="password" minlength="10" required><button>Create Admin</button></form><?php endif; ?>
</body></html>
