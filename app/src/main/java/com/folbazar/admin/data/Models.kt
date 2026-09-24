package com.folbazar.admin.data

data class Category(
    val id: String,
    val name: String,
    val slug: String,
    val imageUrl: String? = null,
    val description: String? = null,
    val active: Boolean = true,
    val sortOrder: Int = 0
)

data class Product(
    val id: String,
    val name: String,
    val slug: String,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val description: String? = null,
    val price: Double,
    val oldPrice: Double? = null,
    val stock: Int = 0,
    val soldQuantity: Int = 0,
    val discountPercent: Double? = null,
    val imageUrl: String? = null,
    val galleryUrls: List<String> = emptyList(),
    val active: Boolean = true,
    val featured: Boolean = false,
    val flashSale: Boolean = false,
    val hotDeal: Boolean = false,
    val sortOrder: Int = 0
)

data class ProductVariant(
    val id: String,
    val productId: String,
    val label: String,
    val weightGrams: Int,
    val price: Double,
    val oldPrice: Double? = null,
    val stock: Int = 0,
    val active: Boolean = true,
    val sortOrder: Int = 0
)

data class Order(
    val id: String,
    val orderNumber: String,
    val userId: String? = null,
    val customer: String,
    val phone: String,
    val email: String? = null,
    val division: String? = null,
    val district: String? = null,
    val upazila: String? = null,
    val address: String,
    val deliveryNote: String? = null,
    val orderNote: String? = null,
    val subtotal: Double,
    val deliveryCharge: Double,
    val discount: Double,
    val total: Double,
    val paymentMethod: String,
    val paymentTitle: String? = null,
    val senderPhone: String? = null,
    val trxId: String? = null,
    val couponCode: String? = null,
    val shippingMethod: String? = null,
    val deliveryArea: String? = null,
    val paymentStatus: String,
    val status: String,
    val createdAt: String? = null
)

data class Customer(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val role: String = "customer",
    val avatarUrl: String? = null,
    val address: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class Complaint(
    val id: String,
    val number: String,
    val customerName: String,
    val phone: String,
    val subject: String? = null,
    val description: String,
    val status: String,
    val adminNote: String? = null,
    val createdAt: String? = null
)

data class WishlistSummary(
    val productId: String,
    val count: Int
)

data class Coupon(
    val id: String,
    val code: String,
    val title: String? = null,
    val discountType: String,
    val discountValue: Double,
    val minOrder: Double = 0.0,
    val maxDiscount: Double? = null,
    val usageLimit: Int? = null,
    val usedCount: Int = 0,
    val active: Boolean = true,
    val startsAt: String? = null,
    val expiresAt: String? = null
)


data class OrderItem(
    val id: String,
    val orderId: String,
    val productId: String? = null,
    val productName: String = "পণ্য",
    val variantLabel: String? = null,
    val weightGrams: Int? = null,
    val unitPrice: Double = 0.0,
    val quantity: Int = 0,
    val lineTotal: Double = 0.0,
    val imageUrl: String? = null
)

data class SiteBanner(
    val id: String,
    val bannerType: String = "hero",
    val title: String? = null,
    val altText: String = "ফল বাজার ব্যানার",
    val imageUrl: String,
    val linkUrl: String? = null,
    val sortOrder: Int = 0,
    val active: Boolean = true,
    val widthPercent: Int = 100,
    val heightPx: Int = 250,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

