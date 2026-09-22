<?php
namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Api\BaseApiController;
use App\Models\{Banner,Category,Coupon,Complaint,Order,OrderItem,Product,ProductVariant,Profile,SiteSetting,User,Wishlist};
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

/**
 * Compatibility data API for the Fol Bazar Admin Android app.
 * The app talks to this Laravel endpoint only; no separate PHP/MySQL API is required.
 */
class AdminDataController extends BaseApiController
{
    public function handle(Request $r)
    {
        $resource = (string) $r->query('resource', '');
        $action = (string) $r->query('action', $r->isMethod('get') ? 'list' : 'save');
        $id = (string) $r->query('id', $r->input('id', ''));

        return match ($resource) {
            'categories' => $this->categories($r, $action, $id),
            'products' => $this->products($r, $action, $id),
            'product_variants' => $this->variants($r, $action, $id),
            'orders' => $this->orders($r, $action, $id),
            'order_items' => $this->orderItems($r),
            'profiles' => $this->profiles($r, $action, $id),
            'complaints' => $this->complaints($r, $action, $id),
            'wishlists' => $this->wishlists(),
            'coupons' => $this->coupons($r, $action, $id),
            'site_banners' => $this->banners($r, $action, $id),
            'site_settings' => $this->settings($r, $action),
            default => $this->fail('Unknown admin resource', 404),
        };
    }

    private function categories(Request $r, string $action, string $id)
    {
        if ($r->isMethod('get')) return $this->ok(Category::orderBy('sort_order')->orderBy('name')->get());
        if ($action === 'delete') { Category::findOrFail($id)->delete(); return $this->ok(); }
        $data = $r->validate([
            'name'=>'required|string|max:190','slug'=>'required|string|max:190','image_url'=>'nullable|string',
            'is_active'=>'boolean','sort_order'=>'integer',
        ]);
        $row = $id ? tap(Category::findOrFail($id), fn($x) => $x->update($data)) : Category::create($data);
        return $this->ok([$row], $id ? 200 : 201);
    }

    private function products(Request $r, string $action, string $id)
    {
        if ($r->isMethod('get')) {
            $q = Product::with('category')->orderBy('sort_order')->latest();
            if ($id) $q->whereKey($id);
            $rows = $q->get()->map(fn(Product $p) => $this->productRow($p));
            return $this->ok($rows->values());
        }
        if ($action === 'delete') { Product::findOrFail($id)->delete(); return $this->ok(); }
        $data = $r->validate([
            'name'=>'required|string|max:255','slug'=>'required|string|max:255','description'=>'nullable|string',
            'image_url'=>'nullable|string','gallery_urls'=>'nullable|array','old_price'=>'nullable|numeric',
            'price'=>'required|numeric|min:0','stock_quantity'=>'required|integer|min:0','sold_quantity'=>'nullable|integer|min:0',
            'discount_percent'=>'nullable|numeric','is_featured'=>'boolean','is_flash_sale'=>'boolean','is_hot_deal'=>'boolean',
            'is_active'=>'boolean','sort_order'=>'integer','category_id'=>'nullable|string','variants'=>'nullable|array',
        ]);
        $variants = $data['variants'] ?? null;
        unset($data['variants']);
        $row = DB::transaction(function () use ($id, $data, $variants) {
            $p = $id ? Product::findOrFail($id) : Product::create($data);
            if ($id) $p->update($data);
            if (is_array($variants)) {
                $p->variants()->delete();
                foreach ($variants as $i => $v) {
                    $p->variants()->create([
                        'label'=>(string)($v['label'] ?? ''),'weight_grams'=>$v['weight_grams'] ?? null,
                        'price'=>$v['price'] ?? 0,'old_price'=>$v['old_price'] ?? null,
                        'stock_quantity'=>$v['stock_quantity'] ?? 0,'is_active'=>$v['is_active'] ?? true,
                        'sort_order'=>$v['sort_order'] ?? $i,
                    ]);
                }
            }
            return $p->load('category');
        });
        return $this->ok([$this->productRow($row)], $id ? 200 : 201);
    }

    private function variants(Request $r, string $action, string $id)
    {
        if ($r->isMethod('get')) {
            $q = ProductVariant::query()->orderBy('sort_order');
            if ($id) $q->whereKey($id);
            if ($r->filled('product_id')) $q->where('product_id', $r->query('product_id'));
            return $this->ok($q->get());
        }
        if ($action === 'delete') { ProductVariant::findOrFail($id)->delete(); return $this->ok(); }
        $data = $r->validate([
            'product_id'=>'required|string','label'=>'required|string','weight_grams'=>'nullable|integer',
            'price'=>'numeric','old_price'=>'nullable|numeric','stock_quantity'=>'integer','is_active'=>'boolean','sort_order'=>'integer',
        ]);
        $row = $id ? tap(ProductVariant::findOrFail($id), fn($x) => $x->update($data)) : ProductVariant::create($data);
        return $this->ok([$row], $id ? 200 : 201);
    }

    private function orders(Request $r, string $action, string $id)
    {
        if ($r->isMethod('get')) {
            $q = Order::query()->latest(); if ($id) $q->whereKey($id);
            return $this->ok($q->get());
        }
        if (!$id) return $this->fail('Order id is required', 400);
        $data = $r->validate(['status'=>'sometimes|string|max:50','payment_status'=>'sometimes|string|max:50']);
        $o = Order::findOrFail($id); $o->update($data); return $this->ok([$o]);
    }

    private function orderItems(Request $r)
    {
        $q = OrderItem::with('product')->latest('created_at');
        if ($r->filled('order_id')) $q->where('order_id', $r->query('order_id'));
        return $this->ok($q->get()->map(function (OrderItem $i) {
            $row = $i->toArray();
            $row['image_url'] = $i->product?->image_url;
            return $row;
        })->values());
    }

    private function profiles(Request $r, string $action, string $id)
    {
        $q = User::with('profile')->latest(); if ($id) $q->whereKey($id);
        if ($r->isMethod('get')) return $this->ok($q->get()->map(fn(User $u) => $this->profileRow($u))->values());
        if (!$id) return $this->fail('Customer id is required', 400);
        $data = $r->validate(['role'=>'sometimes|in:customer,admin']);
        $u = User::findOrFail($id); $u->update($data);
        if (isset($data['role'])) $u->profile()->update(['role'=>$data['role']]);
        return $this->ok([$this->profileRow($u->load('profile'))]);
    }

    private function complaints(Request $r, string $action, string $id)
    {
        if ($r->isMethod('get')) {
            return $this->ok(Complaint::latest()->get()->map(fn(Complaint $c) => $this->complaintRow($c))->values());
        }
        if (!$id) return $this->fail('Complaint id is required', 400);
        $data = $r->validate(['status'=>'sometimes|in:open,in_progress,resolved,closed','admin_note'=>'nullable|string']);
        $c = Complaint::findOrFail($id);
        if (array_key_exists('admin_note',$data)) {
            // admin_note is supported by the migration included with this package.
        }
        $c->update($data); return $this->ok([$this->complaintRow($c)]);
    }

    private function wishlists()
    {
        return $this->ok(Wishlist::query()->select('product_id')->get());
    }

    private function coupons(Request $r, string $action, string $id)
    {
        if ($r->isMethod('get')) return $this->ok(Coupon::latest()->get());
        if ($action === 'delete') { Coupon::findOrFail($id)->delete(); return $this->ok(); }
        $data = $r->validate([
            'code'=>'required|string|max:100','title'=>'nullable|string|max:255','discount_type'=>'required|in:percent,fixed',
            'discount_value'=>'required|numeric|min:0','min_order'=>'numeric|min:0','max_discount'=>'nullable|numeric|min:0',
            'usage_limit'=>'nullable|integer|min:1','used_count'=>'integer|min:0','is_active'=>'boolean','starts_at'=>'nullable|date','expires_at'=>'nullable|date',
        ]);
        $row = $id ? tap(Coupon::findOrFail($id), fn($x) => $x->update($data)) : Coupon::create($data);
        return $this->ok([$row], $id ? 200 : 201);
    }

    private function banners(Request $r, string $action, string $id)
    {
        if ($r->isMethod('get')) return $this->ok(Banner::orderBy('banner_type')->orderBy('sort_order')->get()->map(fn(Banner $b) => $this->bannerRow($b))->values());
        if ($action === 'delete') { Banner::findOrFail($id)->delete(); return $this->ok(); }
        $data = $r->validate([
            'banner_type'=>'required|in:hero,promo','title'=>'nullable|string|max:255','alt_text'=>'nullable|string|max:255',
            'image_url'=>'required|string','link_url'=>'nullable|string','sort_order'=>'integer','is_active'=>'boolean',
        ]);
        $row = $id ? tap(Banner::findOrFail($id), fn($x) => $x->update($data)) : Banner::create($data);
        return $this->ok([$this->bannerRow($row)], $id ? 200 : 201);
    }

    private function settings(Request $r, string $action)
    {
        if ($r->isMethod('get')) {
            $q = SiteSetting::query()->orderBy('setting_key');
            if ($r->filled('key')) $q->where('setting_key', $r->query('key'));
            return $this->ok($q->get()->map(fn(SiteSetting $s) => ['key'=>$s->setting_key,'value'=>$s->setting_value])->values());
        }
        $data = $r->validate(['key'=>'nullable|string|max:190','value'=>'nullable|string']);
        $data['key'] = $data['key'] ?? $r->query('key');
        if (!$data['key']) return $this->fail('Setting key is required', 422);
        $s = SiteSetting::updateOrCreate(['setting_key'=>$data['key']], ['setting_value'=>$data['value'] ?? null]);
        return $this->ok([['key'=>$s->setting_key,'value'=>$s->setting_value]]);
    }

    private function productRow(Product $p): array
    {
        $row = $p->toArray(); $row['category_name'] = $p->category?->name; return $row;
    }
    private function profileRow(User $u): array
    {
        $p = $u->profile; return ['id'=>$u->id,'full_name'=>$p?->full_name,'phone'=>$p?->phone,'email'=>$u->email,'role'=>$u->role,'avatar_url'=>null,'address'=>null,'created_at'=>$u->created_at,'updated_at'=>$u->updated_at];
    }
    private function complaintRow(Complaint $c): array
    {
        return ['id'=>$c->id,'complaint_number'=>$c->id,'customer_name'=>$c->customer_name,'customer_phone'=>$c->customer_phone,'subject'=>null,'description'=>$c->message,'message'=>$c->message,'status'=>$c->status,'admin_note'=>$c->admin_note ?? null,'created_at'=>$c->created_at,'image_url'=>$c->image_url];
    }
    private function bannerRow(Banner $b): array
    {
        $row = $b->toArray(); $row['width_percent']=100; $row['height_px']=180; return $row;
    }
}

