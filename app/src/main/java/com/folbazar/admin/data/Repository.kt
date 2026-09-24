package com.folbazar.admin.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class Repository(private val api: PhpAdminClient = PhpAdminClient()) {
    private fun s(o: JsonObject, vararg keys: String): String? = keys.asSequence().mapNotNull { o[it]?.jsonPrimitive?.contentOrNull }.firstOrNull()
    private fun d(o: JsonObject, vararg keys: String): Double? = keys.asSequence().mapNotNull { o[it]?.jsonPrimitive?.doubleOrNull }.firstOrNull()
    private fun i(o: JsonObject, vararg keys: String): Int? = keys.asSequence().mapNotNull { o[it]?.jsonPrimitive?.intOrNull }.firstOrNull()
    private fun b(o: JsonObject, vararg keys: String): Boolean? = keys.asSequence().mapNotNull { o[it]?.jsonPrimitive?.booleanOrNull }.firstOrNull()
    private fun sa(o: JsonObject, vararg keys: String): List<String> = keys.asSequence().mapNotNull { o[it] as? JsonArray }.firstOrNull()?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
    private fun array(text: String): JsonArray = Json.parseToJsonElement(text).let { el -> if (el is JsonArray) el else JsonArray(emptyList()) }
    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun parseCategory(o: JsonObject) = Category(s(o,"id")?:"",s(o,"name")?:"",s(o,"slug")?:"",s(o,"image_url"),s(o,"description"),b(o,"is_active")?:true,i(o,"sort_order")?:0)
    private fun parseProduct(o: JsonObject) = Product(
        id=s(o,"id")?:"", name=s(o,"name","title")?:"", slug=s(o,"slug")?:"", categoryId=s(o,"category_id"), categoryName=s(o,"category_name") ?: (o["category"] as? JsonObject)?.let{ s(it,"name") },
        description=s(o,"description"), price=d(o,"price")?:0.0, oldPrice=d(o,"old_price"), stock=i(o,"stock_quantity","stock")?:0, soldQuantity=i(o,"sold_quantity")?:0, discountPercent=d(o,"discount_percent"),
        imageUrl=s(o,"image_url","image"), galleryUrls=sa(o,"gallery_urls"), active=b(o,"is_active","active")?:true, featured=b(o,"is_featured")?:false, flashSale=b(o,"is_flash_sale")?:false, hotDeal=b(o,"is_hot_deal")?:false, sortOrder=i(o,"sort_order")?:0
    )
    private fun parseVariant(o: JsonObject) = ProductVariant(s(o,"id")?:"",s(o,"product_id")?:"",s(o,"label")?:"",i(o,"weight_grams")?:0,d(o,"price")?:0.0,d(o,"old_price"),i(o,"stock_quantity")?:0,b(o,"is_active")?:true,i(o,"sort_order")?:0)
    private fun parseOrder(o: JsonObject) = Order(s(o,"id")?:"",s(o,"order_number")?:s(o,"id")?.take(8).orEmpty(),s(o,"user_id"),s(o,"customer_name")?:"Customer",s(o,"customer_phone")?:"",s(o,"customer_email"),s(o,"division"),s(o,"district"),s(o,"upazila"),s(o,"address")?:"",s(o,"delivery_note"),s(o,"order_note"),d(o,"subtotal")?:0.0,d(o,"delivery_charge")?:0.0,d(o,"discount_amount")?:0.0,d(o,"total_amount","total")?:0.0,s(o,"payment_method")?:"cod",s(o,"payment_title"),s(o,"sender_phone"),s(o,"trx_id"),s(o,"coupon_code"),s(o,"shipping_method"),s(o,"delivery_area"),s(o,"payment_status")?:"pending",s(o,"status")?:"pending",s(o,"created_at"))
    private fun parseOrderItem(o: JsonObject) = OrderItem(s(o,"id")?:"",s(o,"order_id")?:"",s(o,"product_id"),s(o,"product_name") ?: s(o,"name") ?: "পণ্য",s(o,"variant_label"),i(o,"weight_grams"),d(o,"unit_price")?:0.0,i(o,"quantity")?:0,d(o,"line_total")?:0.0,s(o,"image_url") ?: (o["product"] as? JsonObject)?.let{ s(it,"image_url") } ?: (o["products"] as? JsonObject)?.let{ s(it,"image_url") })
    private fun parseCustomer(o: JsonObject): Customer {
        val profile = o["profile"] as? JsonObject
        return Customer(
            id = s(o,"id") ?: "",
            name = s(profile ?: JsonObject(emptyMap()),"full_name","name") ?: s(o,"full_name","name","email") ?: "Customer",
            email = s(profile ?: JsonObject(emptyMap()),"email") ?: s(o,"email"),
            phone = s(profile ?: JsonObject(emptyMap()),"phone") ?: s(o,"phone"),
            role = s(o,"role") ?: s(profile ?: JsonObject(emptyMap()),"role") ?: "customer",
            avatarUrl = s(profile ?: JsonObject(emptyMap()),"avatar_url") ?: s(o,"avatar_url"),
            address = s(profile ?: JsonObject(emptyMap()),"address") ?: s(o,"address"),
            createdAt = s(o,"created_at"),
            updatedAt = s(o,"updated_at")
        )
    }
    private fun parseComplaint(o: JsonObject) = Complaint(s(o,"id")?:"",s(o,"complaint_number")?:s(o,"id")?.take(8).orEmpty(),s(o,"customer_name")?:"Customer",s(o,"customer_phone")?:"",s(o,"subject"),s(o,"message")?:s(o,"description")?:"",s(o,"status")?:"open",s(o,"admin_note"),s(o,"created_at"))
    private fun parseBanner(o: JsonObject) = SiteBanner(s(o,"id")?:"",s(o,"banner_type")?:"hero",s(o,"title"),s(o,"alt_text")?:"ফল বাজার ব্যানার",s(o,"image_url")?:"",s(o,"link_url"),i(o,"sort_order")?:0,b(o,"is_active")?:true,i(o,"width_percent")?:100,i(o,"height_px")?:180,s(o,"created_at"),s(o,"updated_at"))
    private fun parseCoupon(o: JsonObject) = Coupon(s(o,"id")?:"",s(o,"code")?:"",s(o,"title"),s(o,"discount_type")?:"percent",d(o,"discount_value")?:0.0,d(o,"min_order")?:0.0,d(o,"max_discount"),i(o,"usage_limit"),i(o,"used_count")?:0,b(o,"is_active")?:true,s(o,"starts_at"),s(o,"expires_at"))

    suspend fun categories(): Result<List<Category>> = runCatching { withContext(Dispatchers.IO) { array(api.get("categories")).map{parseCategory(it.jsonObject)} } }
    suspend fun products(): Result<List<Product>> = runCatching { withContext(Dispatchers.IO) { array(api.get("products")).map{parseProduct(it.jsonObject)} } }
    suspend fun product(id:String): Result<Product?> = runCatching { withContext(Dispatchers.IO) { array(api.get("products","?id=eq.$id")).firstOrNull()?.let{parseProduct(it.jsonObject)} } }
    suspend fun variants(productId:String): Result<List<ProductVariant>> = runCatching { withContext(Dispatchers.IO) { array(api.get("product_variants","?product_id=eq.$productId")).map{parseVariant(it.jsonObject)} } }
    suspend fun orders(): Result<List<Order>> = runCatching { withContext(Dispatchers.IO) { array(api.get("orders")).map{parseOrder(it.jsonObject)} } }
    suspend fun orderItems(orderId:String): Result<List<OrderItem>> = runCatching { withContext(Dispatchers.IO) {
        val root=obj(api.order(orderId)); val rawItems=root["items"] as? JsonArray ?: JsonArray(emptyList()); rawItems.map{parseOrderItem(it.jsonObject)}
    } }
    suspend fun customers(): Result<List<Customer>> = runCatching { withContext(Dispatchers.IO) { array(api.get("profiles")).map{parseCustomer(it.jsonObject)} } }
    suspend fun complaints(): Result<List<Complaint>> = runCatching { withContext(Dispatchers.IO) { array(api.get("complaints")).map{parseComplaint(it.jsonObject)} } }
    suspend fun wishlistSummary(): Result<List<WishlistSummary>> = runCatching { withContext(Dispatchers.IO) { array(api.wishlistSummary()).map{WishlistSummary(s(it.jsonObject,"product_id")?:"",i(it.jsonObject,"count")?:0)}.filter{it.productId.isNotBlank()}.sortedByDescending{it.count} } }
    suspend fun coupons(): Result<List<Coupon>> = runCatching { withContext(Dispatchers.IO) { array(api.get("coupons")).map{parseCoupon(it.jsonObject)} } }

    suspend fun setting(key:String): Result<JsonElement?> = runCatching { withContext(Dispatchers.IO) {
        array(api.get("site_settings")).firstOrNull{ s(it.jsonObject,"setting_key","key")==key }?.jsonObject?.let{ s(it,"setting_value","value")?.let{v-> runCatching{Json.parseToJsonElement(v)}.getOrElse{JsonPrimitive(v)} } }
    } }
    suspend fun saveSetting(key:String,value:JsonElement): Result<Unit> = runCatching { withContext(Dispatchers.IO) { api.saveSetting(key,value); Unit } }

    suspend fun banners(): Result<List<SiteBanner>> = runCatching { withContext(Dispatchers.IO) { array(api.get("site_banners")).map{parseBanner(it.jsonObject)} } }
    suspend fun addBanner(bannerType:String,title:String?,altText:String,imageUrl:String,linkUrl:String?,sortOrder:Int,widthPercent:Int,heightPx:Int):Result<SiteBanner> = runCatching{withContext(Dispatchers.IO){parseBanner(array(api.post("site_banners",buildJsonObject{put("banner_type",bannerType);put("title",title);put("alt_text",altText.ifBlank{"ফল বাজার ব্যানার"});put("image_url",imageUrl);put("link_url",linkUrl);put("sort_order",sortOrder);put("is_active",true)}.toString())).first().jsonObject)}}
    suspend fun updateBanner(b:SiteBanner):Result<SiteBanner> = runCatching{withContext(Dispatchers.IO){parseBanner(array(api.patch("site_banners","id=eq.${b.id}",buildJsonObject{put("banner_type",b.bannerType);put("title",b.title);put("alt_text",b.altText);put("image_url",b.imageUrl);put("link_url",b.linkUrl);put("sort_order",b.sortOrder);put("is_active",b.active)}.toString())).first().jsonObject)}}
    suspend fun deleteBanner(id:String):Result<Unit> = runCatching{withContext(Dispatchers.IO){api.delete("site_banners","id=eq.$id");Unit}}

    suspend fun addCategory(name:String,description:String?,imageUrl:String?,sortOrder:Int):Result<Category> = runCatching{withContext(Dispatchers.IO){val slug=slug(name)+"-"+System.currentTimeMillis().toString().takeLast(6);parseCategory(array(api.post("categories",buildJsonObject{put("name",name);put("slug",slug);put("image_url",imageUrl);put("is_active",true);put("sort_order",sortOrder)}.toString())).first().jsonObject)}}
    suspend fun updateCategory(c:Category):Result<Category> = runCatching{withContext(Dispatchers.IO){parseCategory(array(api.patch("categories","id=eq.${c.id}",buildJsonObject{put("name",c.name);put("slug",c.slug);put("image_url",c.imageUrl);put("is_active",c.active);put("sort_order",c.sortOrder)}.toString())).first().jsonObject)}}
    suspend fun deleteCategory(id:String):Result<Unit> = runCatching{withContext(Dispatchers.IO){api.delete("categories","id=eq.$id");Unit}}

    suspend fun addProduct(name:String,description:String?,price:Double,oldPrice:Double?,stock:Int,categoryId:String?,imageUrl:String?,galleryUrls:List<String>,featured:Boolean,flash:Boolean,hot:Boolean):Result<Product> = runCatching{withContext(Dispatchers.IO){val slug=slug(name)+"-"+System.currentTimeMillis().toString().takeLast(6);parseProduct(array(api.post("products",buildJsonObject{put("name",name);put("slug",slug);put("description",description);put("price",price);put("old_price",oldPrice);put("stock_quantity",stock);put("category_id",categoryId);put("image_url",imageUrl);putJsonArray("gallery_urls"){galleryUrls.forEach{add(it)}};put("is_active",true);put("is_featured",featured);put("is_flash_sale",flash);put("is_hot_deal",hot)}.toString())).first().jsonObject)}}
    suspend fun updateProduct(p:Product):Result<Product> = runCatching{withContext(Dispatchers.IO){parseProduct(array(api.patch("products","id=eq.${p.id}",buildJsonObject{put("name",p.name);put("description",p.description);put("price",p.price);put("old_price",p.oldPrice);put("stock_quantity",p.stock);put("category_id",p.categoryId);put("image_url",p.imageUrl);putJsonArray("gallery_urls"){p.galleryUrls.forEach{add(it)}};put("is_active",p.active);put("is_featured",p.featured);put("is_flash_sale",p.flashSale);put("is_hot_deal",p.hotDeal);put("sort_order",p.sortOrder)}.toString())).first().jsonObject)}}
    suspend fun deleteProduct(id:String):Result<Unit> = runCatching{withContext(Dispatchers.IO){api.delete("products","id=eq.$id");Unit}}

    suspend fun addVariant(productId:String,label:String,grams:Int,price:Double,oldPrice:Double?,stock:Int,sortOrder:Int=0):Result<ProductVariant> = runCatching{withContext(Dispatchers.IO){parseVariant(array(api.post("product_variants",buildJsonObject{put("product_id",productId);put("label",label);put("weight_grams",grams);put("price",price);put("old_price",oldPrice);put("stock_quantity",stock);put("is_active",true);put("sort_order",sortOrder)}.toString())).first().jsonObject)}}
    suspend fun updateVariant(v:ProductVariant):Result<ProductVariant> = runCatching{withContext(Dispatchers.IO){parseVariant(array(api.patch("product_variants","id=eq.${v.id}",buildJsonObject{put("id",v.id);put("product_id",v.productId);put("label",v.label);put("weight_grams",v.weightGrams);put("price",v.price);put("old_price",v.oldPrice);put("stock_quantity",v.stock);put("is_active",v.active);put("sort_order",v.sortOrder)}.toString())).first().jsonObject)}}
    suspend fun deleteVariant(id:String):Result<Unit> = runCatching{withContext(Dispatchers.IO){api.delete("product_variants","id=eq.$id");Unit}}

    suspend fun updateOrderStatus(id:String,status:String):Result<Order> = runCatching{withContext(Dispatchers.IO){parseOrder(array(api.patch("orders","id=eq.$id",buildJsonObject{put("status",status)}.toString())).first().jsonObject)}}
    suspend fun updatePaymentStatus(id:String,status:String):Result<Order> = runCatching{withContext(Dispatchers.IO){parseOrder(array(api.patch("orders","id=eq.$id",buildJsonObject{put("payment_status",status)}.toString())).first().jsonObject)}}
    suspend fun updateCustomerRole(id:String,role:String):Result<Customer> = runCatching{withContext(Dispatchers.IO){parseCustomer(array(api.patch("profiles","id=eq.$id",buildJsonObject{put("role",role)}.toString())).first().jsonObject)}}
    suspend fun updateComplaint(id:String,status:String,note:String?):Result<Complaint> = runCatching{withContext(Dispatchers.IO){parseComplaint(array(api.patch("complaints","id=eq.$id",buildJsonObject{put("status",status);put("admin_note",note)}.toString())).first().jsonObject)}}
    suspend fun addCoupon(code:String,title:String?,type:String,value:Double,minOrder:Double,maxDiscount:Double?,limit:Int?,startsAt:String?,expiresAt:String?):Result<Coupon> = runCatching{withContext(Dispatchers.IO){parseCoupon(array(api.post("coupons",buildJsonObject{put("code",code.trim().uppercase());put("title",title);put("discount_type",if(type=="percentage")"percent" else type);put("discount_value",value);put("min_order",minOrder);put("max_discount",maxDiscount);put("usage_limit",limit);put("starts_at",startsAt);put("expires_at",expiresAt);put("is_active",true)}.toString())).first().jsonObject)}}
    suspend fun updateCoupon(c:Coupon):Result<Coupon> = runCatching{withContext(Dispatchers.IO){parseCoupon(array(api.patch("coupons","id=eq.${c.id}",buildJsonObject{put("code",c.code.trim().uppercase());put("title",c.title);put("discount_type",if(c.discountType=="percentage")"percent" else c.discountType);put("discount_value",c.discountValue);put("min_order",c.minOrder);put("max_discount",c.maxDiscount);put("usage_limit",c.usageLimit);put("is_active",c.active);put("starts_at",c.startsAt);put("expires_at",c.expiresAt)}.toString())).first().jsonObject)}}
    suspend fun deleteCoupon(id:String):Result<Unit> = runCatching{withContext(Dispatchers.IO){api.delete("coupons","id=eq.$id");Unit}}

    data class AdminSession(val accessToken:String,val refreshToken:String?,val userId:String,val email:String)
    suspend fun signInAdmin(email:String,password:String):Result<AdminSession> = runCatching{withContext(Dispatchers.IO){
        val res=obj(api.signIn(email,password)); val token=s(res,"access_token") ?: throw IllegalStateException("লগইন ব্যর্থ হয়েছে")
        val user=res["user"]?.jsonObject ?: JsonObject(emptyMap()); val role=s(user,"role") ?: "customer"
        if(role!="admin") throw IllegalStateException("এই অ্যাকাউন্টের Admin access নেই")
        AdminSession(token,null,s(user,"id")?:throw IllegalStateException("Admin user ID পাওয়া যায়নি"),s(user,"email")?:email)
    }}
    private fun slug(value:String):String=value.lowercase().trim().replace(Regex("[^a-z0-9\u0980-\u09FF]+"),"-").trim('-').ifBlank{"item"}
}
