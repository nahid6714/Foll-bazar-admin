<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli') { fwrite(STDERR, "CLI only\n"); exit(1); }
$configFile = dirname(__DIR__, 2) . '/config/config.php';
if (!is_file($configFile)) { fwrite(STDERR, "Copy this script beside your backend and adjust config path if needed.\n"); exit(1); }
$config=require $configFile;
$pdo=new PDO('mysql:host='.($config['db_host']??'127.0.0.1').';dbname='.($config['db_name']??'foll_bazar').';charset='.($config['db_charset']??'utf8mb4'),$config['db_user']??'root',$config['db_pass']??'',[
 PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]);
$email=trim((string)($argv[1]??''));$password=(string)($argv[2]??'');$name=trim((string)($argv[3]??'Fol Bazar Admin'));
if($email===''||$password===''){fwrite(STDERR,"Usage: php create_admin.php email password [name]\n");exit(1);}
$hash=password_hash($password,PASSWORD_DEFAULT);
$stmt=$pdo->prepare('INSERT INTO admin_users(id,email,name,password_hash,role,is_active) VALUES(UUID(),?,?,\'admin\',1) ON DUPLICATE KEY UPDATE name=VALUES(name),password_hash=VALUES(password_hash),role=\'admin\',is_active=1');
$stmt->execute([$email,$name,$hash]);
echo "Admin account ready: {$email}\n";
