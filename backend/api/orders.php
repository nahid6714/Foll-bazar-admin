<?php require __DIR__.'/bootstrap.php';
try {
 $action=$_GET['action']??''; $pdo=db();
 if($action==='track'){
   $num=trim((string)($_GET['order_number']??'')); $phone=normalize_phone((string)($_GET['phone']??''));
   if($num){$s=$pdo->prepare('SELECT order_number,status,total_amount,created_at FROM orders WHERE UPPER(order_number)=UPPER(?) ORDER BY created_at DESC LIMIT 1');$s->execute([$num]);}
   else {$s=$pdo->prepare("SELECT order_number,status,total_amount,created_at FROM orders WHERE REPLACE(REPLACE(REPLACE(customer_phone,' ',''),'-',''),'+','') LIKE ? ORDER BY created_at DESC LIMIT 1");$s->execute(['%'.$phone]);}
   $r=$s->fetch(); ok($r?:null);
 }
 if($action==='create'){
   $p=json_input(); $name=trim((string)($p['customer_name']??'')); $phone=trim((string)($p['customer_phone']??'')); $address=trim((string)($p['address']??'')); $shipping=strtolower((string)($p['shipping_method']??'dhaka')); $payment=strtolower((string)($p['payment_method']??'cod')); $items=$p['items']??[]; if($payment==='cash')$payment='cod';
   if(strlen($name)<2 || strlen(normalize_phone($phone))<11 || strlen($address)<5 || !in_array($shipping,['dhaka','outside'],true) || !in_array($payment,['cod','bkash','nagad','rocket','bank','shurjopay','card'],true) || !is_array($items) || !$items) fail('Invalid order data');
   $pdo->beginTransaction(); try {
     $user=auth_user(false); $subtotal=0; $server=[];
     foreach($items as $it){ $pid=(string)($it['product_id']??''); $label=isset($it['variant_label'])?trim((string)$it['variant_label']):null; $qty=(int)($it['quantity']??0); if($qty<1||$qty>50) fail('Invalid product quantity');
       $s=$pdo->prepare('SELECT * FROM products WHERE is_active=1 AND (id=? OR legacy_id=?) LIMIT 1 FOR UPDATE');$s->execute([$pid,$pid]);$prod=$s->fetch();if(!$prod) fail('Product not found: '.$pid,404);
       $variant=null; $price=(float)$prod['price']; $stock=(int)$prod['stock_quantity'];
       if($label){$s=$pdo->prepare('SELECT * FROM product_variants WHERE product_id=? AND label=? AND is_active=1 LIMIT 1 FOR UPDATE');$s->execute([$prod['id'],$label]);$variant=$s->fetch(); if($variant){$price=(float)$variant['price'];$stock=(int)$variant['stock_quantity'];} else {if($label==='৫০০ গ্রাম'){$price=round($price*.5,2);}elseif($label==='২ কেজি'){$price=round($price*2,2);}elseif($label==='১ কেজি'){}else fail('Unknown product variant');}}
       if($stock<$qty) fail('Insufficient stock'); $line=round($price*$qty,2);$subtotal+= $line; $server[]=['product'=>$prod,'variant'=>$variant,'label'=>$label,'price'=>$price,'qty'=>$qty,'line'=>$line];
       if($variant){$pdo->prepare('UPDATE product_variants SET stock_quantity=stock_quantity-? WHERE id=?')->execute([$qty,$variant['id']]);}else{$pdo->prepare('UPDATE products SET stock_quantity=stock_quantity-? WHERE id=?')->execute([$qty,$prod['id']]);} $pdo->prepare('UPDATE products SET sold_quantity=sold_quantity+? WHERE id=?')->execute([$qty,$prod['id']]);
     }
     $discount=0;$couponCode=trim((string)($p['coupon_code']??''));
     if($couponCode){$s=$pdo->prepare('SELECT * FROM coupons WHERE UPPER(code)=UPPER(?) LIMIT 1 FOR UPDATE');$s->execute([$couponCode]);$c=$s->fetch();if(!$c)fail('কুপন কোডটি পাওয়া যায়নি');if(!$c['is_active']||($c['starts_at']&&strtotime($c['starts_at'])>time())||($c['expires_at']&&strtotime($c['expires_at'])<time())||($c['usage_limit']!==null&&$c['used_count']>=$c['usage_limit']))fail('কুপনটি ব্যবহার করা যাবে না');if($subtotal<(float)$c['min_order'])fail('Minimum order required');if($c['discount_type']==='percent'){$discount=round($subtotal*(float)$c['discount_value']/100,2);if($c['max_discount']!==null)$discount=min($discount,(float)$c['max_discount']);}else{$discount=min((float)$c['discount_value'],$subtotal);} }
     $delivery=$shipping==='dhaka'?60:120;$discount=max(0,min($discount,$subtotal));$total=max(0,$subtotal-$discount+$delivery);$orderNumber=trim((string)($p['order_number']??'')) ?: 'FB-'.strtoupper(substr(bin2hex(random_bytes(5)),0,10));$oid=uuid();
     $uid=$user['id']??null; $st=$pdo->prepare('INSERT INTO orders(id,order_number,user_id,customer_name,customer_phone,customer_email,address,order_note,shipping_method,delivery_area,subtotal,delivery_charge,discount_amount,total_amount,payment_method,payment_status,payment_title,sender_phone,trx_id,coupon_code,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)');$st->execute([$oid,$orderNumber,$uid,$name,$phone,$p['customer_email']??null,$address,$p['order_note']??null,$shipping,$shipping==='dhaka'?'ঢাকার ভিতরে':'ঢাকার বাইরে',$subtotal,$delivery,$discount,$total,$payment,'pending',$p['payment_title']??null,$p['sender_phone']??null,$p['trx_id']??null,$couponCode?:null,'pending']);
     foreach($server as $x){$v=$x['variant'];$st=$pdo->prepare('INSERT INTO order_items(id,order_id,product_id,variant_id,product_name,variant_label,weight_grams,unit_price,quantity,line_total) VALUES(?,?,?,?,?,?,?,?,?,?)');$st->execute([uuid(),$oid,$x['product']['id'],$v['id']??null,$x['product']['name'],$x['label'],$v['weight_grams']??null,$x['price'],$x['qty'],$x['line']]);}
     if($couponCode){$st=$pdo->prepare('SELECT id FROM coupons WHERE UPPER(code)=UPPER(?)');$st->execute([$couponCode]);$cid=$st->fetchColumn();$pdo->prepare('INSERT INTO coupon_usages(id,coupon_id,user_id,order_id) VALUES(?,?,?,?)')->execute([uuid(),$cid,$uid,$oid]);$pdo->prepare('UPDATE coupons SET used_count=used_count+1 WHERE id=?')->execute([$cid]);}
     $pdo->commit();ok(['order_id'=>$oid,'order_number'=>$orderNumber,'subtotal'=>$subtotal,'discount_amount'=>$discount,'delivery_charge'=>$delivery,'total_amount'=>$total]);
   } catch(Throwable $e){if($pdo->inTransaction())$pdo->rollBack();throw $e;}
 }
 if($action==='admin-list'){admin_user();$st=$pdo->query('SELECT * FROM orders ORDER BY created_at DESC');ok($st->fetchAll());}
 if($action==='admin-get'){admin_user();$id=trim((string)($_GET['id']??''));$st=$pdo->prepare('SELECT * FROM orders WHERE id=? LIMIT 1');$st->execute([$id]);$order=$st->fetch();if(!$order)fail('Order not found',404);$it=$pdo->prepare('SELECT oi.*,p.image_url FROM order_items oi LEFT JOIN products p ON p.id=oi.product_id WHERE oi.order_id=? ORDER BY oi.id');$it->execute([$id]);$order['items']=$it->fetchAll();ok($order);}
 if($action==='admin-update'){admin_user();$p=json_input();$st=$pdo->prepare('UPDATE orders SET status=?,payment_status=? WHERE id=?');$st->execute([$p['status']??'pending',$p['payment_status']??'pending',$p['id']]);ok();}
 fail('Unknown order action',404);
} catch(Throwable $e){$msg=$e instanceof RuntimeException?$e->getMessage():'Server error';fail($msg,500);}
