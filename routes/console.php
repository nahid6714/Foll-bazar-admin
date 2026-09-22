<?php
use Illuminate\Support\Facades\Artisan; use Illuminate\Support\Facades\Schema;
Artisan::command('folbazar:health',function(){ $this->info('Fol Bazar Laravel backend is ready.'); $this->info('Database tables: '.count(Schema::getTables())); });


Artisan::command('folbazar:schema-verify', function () {
    $expected = [
        'users' => ['id','email','password_hash','role','email_verified_at','remember_token','created_at','updated_at'],
        'profiles' => ['id','full_name','phone','email','role','created_at','updated_at'],
        'categories' => ['id','name','slug','image_url','is_active','sort_order','created_at','updated_at'],
        'products' => ['id','legacy_id','name','slug','description','image_url','gallery_urls','old_price','price','stock_quantity','sold_quantity','discount_percent','is_featured','is_flash_sale','is_hot_deal','is_active','sort_order','category_id','created_at','updated_at'],
        'product_variants' => ['id','product_id','label','weight_grams','price','old_price','stock_quantity','is_active','sort_order','created_at','updated_at'],
        'orders' => ['id','order_number','user_id','customer_name','customer_phone','customer_email','address','order_note','shipping_method','delivery_area','subtotal','delivery_charge','discount_amount','total_amount','payment_method','payment_status','payment_title','sender_phone','trx_id','coupon_code','status','created_at','updated_at'],
        'order_items' => ['id','order_id','product_id','variant_id','product_name','variant_label','weight_grams','unit_price','quantity','line_total','created_at'],
        'coupons' => ['id','code','discount_type','discount_value','min_order','max_discount','usage_limit','used_count','is_active','starts_at','expires_at','created_at','updated_at'],
        'coupon_usages' => ['id','coupon_id','user_id','order_id','created_at'],
        'wishlists' => ['id','user_id','product_id','created_at'],
        'complaints' => ['id','user_id','order_number','customer_name','customer_phone','message','image_url','status','created_at','updated_at'],
        'site_banners' => ['id','banner_type','title','alt_text','image_url','link_url','sort_order','is_active','created_at','updated_at'],
        'site_settings' => ['setting_key','setting_value','updated_at'],
        'personal_access_tokens' => ['id','tokenable_type','tokenable_id','name','token','abilities','last_used_at','expires_at','created_at','updated_at'],
    ];

    $errors = 0;
    foreach ($expected as $table => $columns) {
        if (!Schema::hasTable($table)) {
            $this->error("MISSING TABLE: {$table}");
            $errors++;
            continue;
        }
        $actual = Schema::getColumnListing($table);
        $missing = array_values(array_diff($columns, $actual));
        if ($missing) {
            $this->error("{$table}: missing -> ".implode(', ', $missing));
            $errors++;
        } else {
            $this->line("OK  {$table}");
        }
    }

    if ($errors) {
        $this->newLine();
        $this->error("Schema verification failed: {$errors} table(s) need attention. No data was changed.");
        return 1;
    }

    $this->newLine();
    $this->info('Schema verification passed. All required Laravel columns are present. No data was changed.');
    return 0;
});

Artisan::command('folbazar:create-admin {email}', function (string $email) {
    $email = strtolower(trim($email));
    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        $this->error('A valid email address is required.');
        return 1;
    }
    $name = trim((string) $this->ask('Admin name'));
    if ($name === '') $name = 'Fol Bazar Admin';
    $password = (string) $this->secret('Admin password');
    if (strlen($password) < 8) {
        $this->error('Password must be at least 8 characters.');
        return 1;
    }
    $user = \App\Models\User::updateOrCreate(
        ['email' => $email],
        ['password_hash' => \Illuminate\Support\Facades\Hash::make($password), 'role' => 'admin']
    );
    \App\Models\Profile::updateOrCreate(
        ['id' => $user->id],
        ['full_name' => $name, 'phone' => '', 'email' => $email, 'role' => 'admin']
    );
    $this->info("Admin account ready: {$email}");
    return 0;
});
