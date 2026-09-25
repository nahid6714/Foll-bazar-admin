@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.folbazar.admin.ui

import android.net.Uri
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.folbazar.admin.BuildConfig
import com.folbazar.admin.data.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

private data class NavItem(val route: String, val label: String, val icon: ImageVector)
private val ORDER_STATUSES = listOf("pending","confirmed","processing","packed","shipped","out_for_delivery","delivered","cancelled","returned")
private val PAYMENT_STATUSES = listOf("pending","paid","failed","refunded")
private val CUSTOMER_ROLES = listOf("customer","reseller","seller","admin")
private val COMPLAINT_STATUSES = listOf("open","in_review","resolved","closed","rejected")
private val VARIANT_PRESETS = listOf(
    250 to "২৫০ গ্রাম",
    500 to "৫০০ গ্রাম",
    1000 to "১ কেজি",
    2000 to "২ কেজি",
    3000 to "৩ কেজি",
    5000 to "৫ কেজি"
)

@Composable
fun FolBazarAdminApp() {
    var loggedIn by remember { mutableStateOf(Session.isLoggedIn) }
    if (!loggedIn) { LoginScreen(onLoggedIn = { loggedIn = true }); return }
    val context = LocalContext.current
    val nav = rememberNavController()
    val items = listOf(
        NavItem("dashboard","ড্যাশবোর্ড",Icons.Default.Dashboard),
        NavItem("products","পণ্য",Icons.Default.Inventory2),
        NavItem("orders","অর্ডার",Icons.Default.ShoppingCart),
        NavItem("analytics","অ্যানালিটিক্স",Icons.Default.Analytics),
        NavItem("more","আরও",Icons.Default.MoreHoriz)
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ফল বাজার Admin", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val current = nav.currentBackStackEntryAsState().value?.destination?.route
                items.forEach { item -> NavigationBarItem(selected = current == item.route || (item.route == "more" && current in listOf("more","categories","customers","complaints","coupons","wishlist","banners","settings","analytics")), onClick = { nav.navigate(item.route) { launchSingleTop = true } }, icon = { Icon(item.icon,null) }, label = { Text(item.label) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primaryContainer)) }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = "dashboard", modifier = Modifier.padding(padding)) {
            composable("dashboard") { Dashboard(nav) }
            composable("products") { Products() }
            composable("orders") { Orders() }
            composable("analytics") { Analytics() }
            composable("more") { More(nav) }
            composable("categories") { Categories() }
            composable("customers") { Customers() }
            composable("complaints") { Complaints() }
            composable("coupons") { Coupons() }
            composable("wishlist") { Wishlist() }
            composable("banners") { Banners() }
            composable("settings") { SettingsScreen(onLogout = { Session.clear(context); loggedIn = false }) }
        }
    }
}

@Composable private fun Dashboard(nav: NavHostController) {
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var complaints by remember { mutableStateOf<List<Complaint>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(refresh) {
        val r = Repository(); val p=r.products(); val o=r.orders(); val c=r.customers(); val x=r.complaints()
        products=p.getOrNull().orEmpty(); orders=o.getOrNull().orEmpty(); customers=c.getOrNull().orEmpty(); complaints=x.getOrNull().orEmpty()
        error = p.exceptionOrNull()?.message ?: o.exceptionOrNull()?.message ?: c.exceptionOrNull()?.message ?: x.exceptionOrNull()?.message
    }
    val pending = orders.count { it.status in setOf("pending","confirmed","processing","packed") }
    val revenue = orders.filter { it.status == "delivered" }.sumOf { it.total }
    RefreshableList(refresh, { refresh++ }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
        item { Text("স্বাগতম 👋", style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold); Text("ফল বাজারের সম্পূর্ণ নিয়ন্ত্রণ কেন্দ্র") }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Stat("পণ্য",products.size.toString(),Icons.Default.Inventory2,Modifier.weight(1f)) { nav.navigate("products") { launchSingleTop = true } }
            Stat("অর্ডার",orders.size.toString(),Icons.Default.ShoppingBag,Modifier.weight(1f)) { nav.navigate("orders") { launchSingleTop = true } }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Stat("কাস্টমার",customers.size.toString(),Icons.Default.People,Modifier.weight(1f)) { nav.navigate("customers") { launchSingleTop = true } }
            Stat("Pending",pending.toString(),Icons.Default.Pending,Modifier.weight(1f)) { nav.navigate("orders") { launchSingleTop = true } }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Stat("Delivered Sales","৳ ${money(revenue)}",Icons.Default.Payments,Modifier.weight(1f)) { nav.navigate("orders") { launchSingleTop = true } }
            Stat("অভিযোগ",complaints.count{it.status=="open"}.toString(),Icons.Default.ReportProblem,Modifier.weight(1f)) { nav.navigate("complaints") { launchSingleTop = true } }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) { FilledTonalButton({ nav.navigate("products") },Modifier.weight(1f)){Icon(Icons.Default.Add,null);Spacer(Modifier.width(4.dp));Text("পণ্য")}; FilledTonalButton({ nav.navigate("orders") },Modifier.weight(1f)){Icon(Icons.Default.ShoppingCart,null);Spacer(Modifier.width(4.dp));Text("অর্ডার")} } }
        item {
            Text("সব সেকশন", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardShortcut("ক্যাটাগরি", Icons.Default.Category, Modifier.weight(1f)) { nav.navigate("categories") { launchSingleTop = true } }
            DashboardShortcut("কাস্টমার", Icons.Default.People, Modifier.weight(1f)) { nav.navigate("customers") { launchSingleTop = true } }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardShortcut("অভিযোগ", Icons.Default.ReportProblem, Modifier.weight(1f)) { nav.navigate("complaints") { launchSingleTop = true } }
            DashboardShortcut("কুপন", Icons.Default.LocalOffer, Modifier.weight(1f)) { nav.navigate("coupons") { launchSingleTop = true } }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardShortcut("Wishlist", Icons.Default.Favorite, Modifier.weight(1f)) { nav.navigate("wishlist") { launchSingleTop = true } }
            DashboardShortcut("সেটিংস", Icons.Default.Settings, Modifier.weight(1f)) { nav.navigate("settings") { launchSingleTop = true } }
        } }
        item { DashboardShortcut("ব্যানার", Icons.Default.Image, Modifier.fillMaxWidth()) { nav.navigate("banners") { launchSingleTop = true } } }
        error?.let { item { Text("API: $it", color=MaterialTheme.colorScheme.error) } }
    }
}

@Composable private fun Stat(title:String,value:String,icon:ImageVector,modifier:Modifier,onClick:()->Unit){
    Card(modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon,null,tint=MaterialTheme.colorScheme.primary)
            Text(title)
            Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        }
    }
}


@Composable
private fun DashboardShortcut(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable private fun Analytics() {
    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var items by remember { mutableStateOf<List<OrderItem>>(emptyList()) }
    var goal by remember { mutableStateOf("100000") }
    var goalInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refresh) {
        val r = Repository()
        val os = r.orders(); val ps = r.products()
        orders = os.getOrNull().orEmpty(); products = ps.getOrNull().orEmpty()
        val all = mutableListOf<OrderItem>()
        orders.take(100).forEach { o -> r.orderItems(o.id).getOrNull()?.let { all += it } }
        items = all
        r.setting("sales_goal").getOrNull()?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.let { goal = it.toLong().toString() }
        error = os.exceptionOrNull()?.message ?: ps.exceptionOrNull()?.message
    }
    val delivered = orders.filter { it.status == "delivered" }
    val revenue = delivered.sumOf { it.total }
    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    val todayOrders = orders.count { it.createdAt?.take(10) == today }
    val top = items.groupBy { it.productName }.mapValues { (_, xs) -> xs.sumOf { it.quantity } }.entries.sortedByDescending { it.value }.take(5)
    val goalValue = goal.toDoubleOrNull() ?: 0.0
    val progress = if (goalValue > 0) (revenue / goalValue).coerceIn(0.0, 1.0) else 0.0
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("সেলস অ্যানালিটিক্স", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("আগের Admin Panel-এর dashboard analytics ও sales goal এখন Fol Bazar-এ") }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) { Stat("Delivered Sales", "৳ ${money(revenue)}", Icons.Default.Payments, Modifier.weight(1f)) {}; Stat("আজকের অর্ডার", todayOrders.toString(), Icons.Default.Today, Modifier.weight(1f)) {} } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) { Text("মাসিক / সেলস Goal", fontWeight=FontWeight.Bold); Text("৳ ${money(revenue)} / ৳ ${money(goalValue)}"); LinearProgressIndicator(progress=progress.toFloat(), modifier=Modifier.fillMaxWidth()); Row(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically) { OutlinedTextField(goalInput, {goalInput=it}, label={Text("Goal (৳)")}, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal), modifier=Modifier.weight(1f), singleLine=true); Button(onClick={ val v=goalInput.toDoubleOrNull(); if(v!=null){ goal=v.toString(); scope.launch { Repository().saveSetting("sales_goal", JsonPrimitive(v)) } } }){Text("সেভ")} } } } }
        item { Text("Top Selling Products", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold) }
        items(top.size) { index -> val e = top[index]; Card(Modifier.fillMaxWidth()) { Row(modifier=Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement=Arrangement.SpaceBetween) { Text(e.key, modifier=Modifier.weight(1f)); Text("${e.value} pcs", fontWeight=FontWeight.Bold) } } }
        error?.let { item { Text("ডাটা লোড সমস্যা: $it", color=MaterialTheme.colorScheme.error) } }
        item { OutlinedButton(onClick={refresh++}, modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(6.dp));Text("রিফ্রেশ")} }
    }
}

@Composable private fun More(nav:NavHostController){
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("অ্যাডমিন ম্যানেজমেন্ট",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("ওয়েবসাইটের বাকি সব নিয়ন্ত্রণ এখান থেকে")}
        item{AdminAction("ক্যাটাগরি","ক্যাটাগরি যোগ, এডিট, active/off, delete",Icons.Default.Category){nav.navigate("categories")}}
        item{AdminAction("কাস্টমার","প্রোফাইল ও customer/reseller/seller/admin role",Icons.Default.People){nav.navigate("customers")}}
        item{AdminAction("অভিযোগ","অভিযোগ দেখা, নোট ও status পরিবর্তন",Icons.Default.ReportProblem){nav.navigate("complaints")}}
        item{AdminAction("কুপন / ডিসকাউন্ট","coupon code, percent/fixed discount, limit",Icons.Default.LocalOffer){nav.navigate("coupons")}}
        item{AdminAction("Wishlist","কোন পণ্য কতবার wishlist হয়েছে",Icons.Default.Favorite){nav.navigate("wishlist")}}
        item{AdminAction("ওয়েবসাইট ব্যানার","Hero, Promo ও Event banner যোগ, edit, active/off, delete ও server image",Icons.Default.Image){nav.navigate("banners")}}
        item{AdminAction("সেলস অ্যানালিটিক্স","Sales goal, আজকের অর্ডার, delivered revenue ও top products",Icons.Default.Analytics){nav.navigate("analytics")}}
        item{AdminAction("সেটিংস / App Update","অ্যাপ আপডেট চেক, ডাউনলোড ও ইনস্টল",Icons.Default.Settings){nav.navigate("settings")}}
        item{Text("নিরাপত্তা: Laravel admin middleware চূড়ান্ত permission; app শুধু authenticated admin token দিয়ে কাজ করে.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
@Composable private fun AdminAction(title:String,desc:String,icon:ImageVector,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold);Text(desc,style=MaterialTheme.typography.bodySmall)};Icon(Icons.Default.ChevronRight,null)}}}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Products() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var cats by remember { mutableStateOf<List<Category>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var add by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Product?>(null) }
    var del by remember { mutableStateOf<Product?>(null) }

    LaunchedEffect(refresh) {
        loading = true
        val r = Repository()
        val p = r.products()
        val c = r.categories()
        products = p.getOrNull().orEmpty()
        cats = c.getOrNull().orEmpty()
        error = p.exceptionOrNull()?.message ?: c.exceptionOrNull()?.message
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("পণ্য", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("পণ্য, দাম, স্টক, সাইজ ও server-এর ছবি")
            }
            IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "রিফ্রেশ") }
            FilledTonalButton(onClick = { add = true }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("নতুন")
            }
        }
        Spacer(Modifier.height(10.dp))
        error?.let { Text("ডাটা লোড হয়নি: $it", color = MaterialTheme.colorScheme.error) }

        RefreshableList(refresh, { refresh++ }) {
            if (loading && products.isEmpty()) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else if (!loading && products.isEmpty()) {
                item { EmptyState(if (error != null) "পণ্যের ডাটা লোড হয়নি" else "কোনো পণ্য নেই") }
            }

            items(products, key = { it.id }) { p ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        p.imageUrl?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = p.name,
                                modifier = Modifier.size(68.dp)
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { copyImageLink(context, it) }
                                    )
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text("৳ ${money(p.price)}", style = MaterialTheme.typography.titleMedium)
                            Text("স্টক ${p.stock} • ${p.categoryName ?: "ক্যাটাগরি নেই"}")
                            Text(
                                if (p.active) "Active" else "Off",
                                color = if (p.active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(onClick = { edit = p }) {
                            Icon(Icons.Default.Edit, "এডিট")
                        }
                        IconButton(onClick = { del = p }) {
                            Icon(Icons.Default.Delete, "ডিলিট")
                        }
                    }
                }
            }
        }
    }

    if (add) {
        ProductDialog(
            initial = null,
            cats = cats,
            onDismiss = { add = false }
        ) { n, d, pr, op, st, ci, img, gal, f, fl, h ->
            scope.launch {
                Repository().addProduct(n, d, pr, op, st, ci, img, gal, f, fl, h).fold(
                    { add = false; refresh++ },
                    { error = it.message }
                )
            }
        }
    }

    edit?.let { p ->
        ProductDialog(
            initial = p,
            cats = cats,
            onDismiss = { edit = null }
        ) { n, d, pr, op, st, ci, img, gal, f, fl, h ->
            scope.launch {
                Repository().updateProduct(
                    p.copy(
                        name = n, description = d, price = pr, oldPrice = op,
                        stock = st, categoryId = ci, imageUrl = img, galleryUrls = gal,
                        featured = f, flashSale = fl, hotDeal = h
                    )
                ).fold(
                    { edit = null; refresh++ },
                    { error = it.message }
                )
            }
        }
    }

    del?.let { p ->
        Confirm(
            "পণ্য ডিলিট করবেন?",
            "${p.name} স্থায়ীভাবে মুছে যাবে.",
            {
                scope.launch {
                    Repository().deleteProduct(p.id).fold(
                        { del = null; refresh++ },
                        { error = it.message; del = null }
                    )
                }
            },
            { del = null }
        )
    }
}

private fun copyImageLink(context: android.content.Context, url: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("Image URL", url))
    Toast.makeText(context, "ছবির লিংক কপি হয়েছে", Toast.LENGTH_SHORT).show()
}

@Composable
private fun VariantManagerDialog(product: Product, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var variants by remember { mutableStateOf<List<ProductVariant>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var add by remember { mutableStateOf(false) }
    var quickAdd by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<ProductVariant?>(null) }
    var del by remember { mutableStateOf<ProductVariant?>(null) }

    LaunchedEffect(product.id, refresh) {
        loading = true
        Repository().variants(product.id).fold(
            { variants = it; error = null },
            { error = it.message }
        )
        loading = false
    }

    FullScreenEditorPage(
        onDismiss = onDismiss,
        title = "সাইজ / ভ্যারিয়েন্ট — ${product.name}",
        confirmText = "বন্ধ",
        onConfirm = onDismiss
    ) {
                Text("ওয়েবসাইটে যে ৫০০ গ্রাম, ১ কেজি ইত্যাদি দেখাবে—এখান থেকেই যোগ/এডিট/ডিলিট করুন.", style=MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={add=true}, modifier=Modifier.weight(1f)) { Icon(Icons.Default.Add,null); Spacer(Modifier.width(5.dp)); Text("একটি সাইজ") }
                    FilledTonalButton(onClick={quickAdd=true}, modifier=Modifier.weight(1f)) { Icon(Icons.Default.PlaylistAdd,null); Spacer(Modifier.width(5.dp)); Text("একসাথে কয়েকটি") }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text("ডাটা লোড হয়নি: $it", color=MaterialTheme.colorScheme.error) }
                if (!loading && variants.isEmpty()) Text("এখনও কোনো সাইজ/ভ্যারিয়েন্ট যোগ করা হয়নি.", color=MaterialTheme.colorScheme.onSurfaceVariant)
                variants.forEach { v ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(v.label, fontWeight=FontWeight.SemiBold)
                                Text("${v.weightGrams}g • ৳ ${money(v.price)} • স্টক ${v.stock}")
                                Text(if(v.active) "Active" else "Off", style=MaterialTheme.typography.bodySmall, color=if(v.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                            IconButton(onClick={edit=v}) { Icon(Icons.Default.Edit,"এডিট") }
                            IconButton(onClick={del=v}) { Icon(Icons.Default.Delete,"ডিলিট") }
                        }
                    }
                }
    }

    if (add) VariantEditorDialog(null, onDismiss={add=false}, onSave={label,grams,price,oldPrice,stock,active,sortOrder ->
        scope.launch { Repository().addVariant(product.id,label,grams,price,oldPrice,stock,sortOrder).fold({add=false;refresh++},{error=it.message}) }
    })
    if (quickAdd) QuickVariantDialog(
        productName = product.name,
        existingWeights = variants.map { it.weightGrams }.toSet(),
        onDismiss = { quickAdd = false }
    ) { stock, items, prices ->
        scope.launch {
            var ok = true
            var lastError: String? = null
            items.forEach { (grams, label) ->
                if (ok) {
                    Repository().addVariant(product.id, label, grams, prices[grams] ?: 0.0, null, stock, grams).fold(
                        { },
                        { ok = false; lastError = it.message }
                    )
                }
            }
            quickAdd = false
            refresh++
            if (!ok) error = lastError
        }
    }
    edit?.let { v -> VariantEditorDialog(v, onDismiss={edit=null}, onSave={label,grams,price,oldPrice,stock,active,sortOrder ->
        scope.launch { Repository().updateVariant(v.copy(label=label,weightGrams=grams,price=price,oldPrice=oldPrice,stock=stock,active=active,sortOrder=sortOrder)).fold({edit=null;refresh++},{error=it.message}) }
    }) }
    del?.let { v -> Confirm("ভ্যারিয়েন্ট ডিলিট করবেন?", "${v.label} (${v.weightGrams}g) স্থায়ীভাবে মুছে যাবে.", {
        scope.launch { Repository().deleteVariant(v.id).fold({del=null;refresh++},{error=it.message;del=null}) }
    }, { del=null }) }
}

@Composable
private fun QuickVariantDialog(
    productName: String,
    existingWeights: Set<Int>,
    onDismiss: () -> Unit,
    onConfirm: (stock: Int, items: List<Pair<Int, String>>, prices: Map<Int, Double>) -> Unit
) {
    var perKg by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("0") }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var priceOverrides by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    val perKgValue = perKg.toDoubleOrNull()

    fun autoPrice(grams: Int): Double = (perKgValue ?: 0.0) * grams / 1000.0

    FullScreenEditorPage(
        onDismiss = onDismiss,
        title = "একসাথে কয়েকটি সাইজ — $productName",
        confirmText = "সিলেক্ট করা সাইজ যোগ করুন",
        confirmEnabled = selected.isNotEmpty(),
        onConfirm = {
            val items = selected.sorted().map { g -> g to (VARIANT_PRESETS.firstOrNull { it.first == g }?.second ?: "${g}g") }
            val prices = selected.associateWith { g -> priceOverrides[g]?.toDoubleOrNull() ?: autoPrice(g) }
            onConfirm(stock.toIntOrNull() ?: 0, items, prices)
        }
    ) {
                Text(
                    "একবার প্রতি কেজি দাম দিন, নিচে থেকে যে সাইজগুলো লাগবে টিক দিন—দাম নিজে থেকেই হিসাব হয়ে যাবে। প্রয়োজনে যেকোনো সাইজের দাম আলাদা করেও বদলাতে পারবেন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Field(perKg, { perKg = it }, "প্রতি কেজি দাম (৳)", KeyboardType.Decimal)
                Field(stock, { stock = it }, "প্রতিটি সাইজে স্টক", KeyboardType.Number)

                VARIANT_PRESETS.forEach { (grams, label) ->
                    val already = existingWeights.contains(grams)
                    val isSelected = selected.contains(grams)
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !already) {
                                        selected = if (isSelected) selected - grams else selected + grams
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isSelected, enabled = !already, onCheckedChange = {
                                    selected = if (it) selected + grams else selected - grams
                                })
                                Column(Modifier.weight(1f)) {
                                    Text(label, fontWeight = FontWeight.SemiBold)
                                    if (already) Text(
                                        "ইতিমধ্যে যোগ করা আছে",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) Text("৳ ${money(autoPrice(grams))}", fontWeight = FontWeight.Bold)
                            }
                            if (isSelected) {
                                Spacer(Modifier.height(6.dp))
                                Field(
                                    priceOverrides[grams] ?: (if (perKgValue != null) money(autoPrice(grams)) else ""),
                                    { v -> priceOverrides = priceOverrides + (grams to v) },
                                    "এই সাইজের দাম (৳) — চাইলে বদলান",
                                    KeyboardType.Decimal
                                )
                            }
                        }
                    }
                }
    }
}

@Composable
private fun VariantEditorDialog(initial: ProductVariant?, onDismiss: () -> Unit, onSave: (String,Int,Double,Double?,Int,Boolean,Int)->Unit) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var grams by remember { mutableStateOf(initial?.weightGrams?.toString() ?: "") }
    var price by remember { mutableStateOf(initial?.price?.toString() ?: "") }
    var oldPrice by remember { mutableStateOf(initial?.oldPrice?.toString() ?: "") }
    var stock by remember { mutableStateOf(initial?.stock?.toString() ?: "0") }
    var sortOrder by remember { mutableStateOf(initial?.sortOrder?.toString() ?: "0") }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    val parsedPrice = price.toDoubleOrNull()
    val parsedGrams = grams.toIntOrNull()
    FullScreenEditorPage(
        onDismiss=onDismiss,
        title=if(initial==null) "নতুন সাইজ / ভ্যারিয়েন্ট" else "সাইজ / ভ্যারিয়েন্ট এডিট",
        confirmEnabled=label.isNotBlank() && parsedGrams!=null && parsedGrams>0 && parsedPrice!=null && parsedPrice>=0,
        onConfirm={onSave(label.trim(),parsedGrams?:0,parsedPrice?:0.0,oldPrice.toDoubleOrNull(),stock.toIntOrNull()?:0,active,sortOrder.toIntOrNull()?:0)}
    ) {
            Field(label,{label=it},"লেবেল (যেমন ৫০০ গ্রাম / ১ কেজি)")
            Field(grams,{grams=it},"ওজন (গ্রাম)",KeyboardType.Number)
            Field(price,{price=it},"দাম (৳)",KeyboardType.Decimal)
            Field(oldPrice,{oldPrice=it},"পুরনো দাম (৳)",KeyboardType.Decimal)
            Field(stock,{stock=it},"স্টক",KeyboardType.Number)
            Field(sortOrder,{sortOrder=it},"সাজানোর ক্রম",KeyboardType.Number)
            SwitchRow("Active",active){active=it}
    }
}

@Composable
private fun RefreshableList(
    refreshKey: Int,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit
) {
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) {
            delay(1200)
            refreshing = false
        }
    }

    PullToRefreshBox(
        state = rememberPullToRefreshState(),
        isRefreshing = refreshing,
        onRefresh = {
            if (!refreshing) {
                refreshing = true
                onRefresh()
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = contentPadding,
            content = content
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Inbox,
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductDialog(
    initial: Product?,
    cats: List<Category>,
    onDismiss: () -> Unit,
    onSave: (String, String?, Double, Double?, Int, String?, String?, List<String>, Boolean, Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var price by remember { mutableStateOf(initial?.price?.toString() ?: "") }
    var old by remember { mutableStateOf(initial?.oldPrice?.toString() ?: "") }
    var stock by remember { mutableStateOf(initial?.stock?.toString() ?: "0") }
    var cat by remember { mutableStateOf(initial?.categoryId) }
    var image by remember { mutableStateOf(initial?.imageUrl) }
    var galleryUrls by remember { mutableStateOf(
        (initial?.galleryUrls?.takeIf { it.isNotEmpty() } ?: listOfNotNull(initial?.imageUrl)).distinct()
    ) }
    var featured by remember { mutableStateOf(initial?.featured ?: false) }
    var flash by remember { mutableStateOf(initial?.flashSale ?: false) }
    var hot by remember { mutableStateOf(initial?.hotDeal ?: false) }
    var uploading by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }

    var variants by remember { mutableStateOf<List<ProductVariant>>(emptyList()) }
    var variantLoading by remember { mutableStateOf(false) }
    var variantError by remember { mutableStateOf<String?>(null) }
    var variantRefresh by remember { mutableStateOf(0) }
    var addVariant by remember { mutableStateOf(false) }
    var editVariant by remember { mutableStateOf<ProductVariant?>(null) }
    var deleteVariant by remember { mutableStateOf<ProductVariant?>(null) }

    val multiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uploading = true
            formError = null
            scope.launch {
                val uploaded = mutableListOf<String>()
                var failed: String? = null
                uris.forEach { uri ->
                    ServerStorageClient(context).uploadImage(uri, "products").fold(
                        { uploaded += it },
                        { failed = it.message ?: "ছবি আপলোড ব্যর্থ" }
                    )
                }
                if (uploaded.isNotEmpty()) {
                    galleryUrls = (galleryUrls + uploaded).distinct()
                    if (image.isNullOrBlank()) image = uploaded.first()
                }
                if (failed != null) formError = failed
                uploading = false
            }
        }
    }

    LaunchedEffect(initial?.id, variantRefresh) {
        if (initial != null) {
            variantLoading = true
            Repository().variants(initial.id).fold(
                { variants = it; variantError = null },
                { variantError = it.message }
            )
            variantLoading = false
        }
    }

    val parsedPrice = price.toDoubleOrNull()
    FullScreenEditorPage(
        onDismiss = onDismiss,
        title = if (initial == null) "নতুন পণ্য" else "পণ্য এডিট",
        confirmEnabled = !uploading && name.isNotBlank() && parsedPrice != null,
        onConfirm = {
            onSave(
                name.trim(),
                desc.trim().ifBlank { null },
                parsedPrice ?: 0.0,
                old.toDoubleOrNull(),
                stock.toIntOrNull() ?: 0,
                cat,
                image ?: galleryUrls.firstOrNull(),
                galleryUrls,
                featured,
                flash,
                hot
            )
        }
    ) {
                Text(
                    if (initial == null) "পণ্যের তথ্য ও ছবি"
                    else "এখান থেকেই সাইজ / ভ্যারিয়েন্টও নিয়ন্ত্রণ করুন",
                    style = MaterialTheme.typography.bodySmall
                )
                Field(name, { name = it }, "পণ্যের নাম")
                Field(price, { price = it }, "মূল দাম (৳)", KeyboardType.Decimal)
                Field(old, { old = it }, "পুরনো দাম (৳)", KeyboardType.Decimal)
                Field(stock, { stock = it }, "স্টক", KeyboardType.Number)
                Field(desc, { desc = it }, "বিবরণ", single = false)

                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(cats.firstOrNull { it.id == cat }?.name ?: "ক্যাটাগরি নির্বাচন")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        cats.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = { cat = category.id; menu = false }
                            )
                        }
                    }
                }

                var urlInput by remember { mutableStateOf("") }
                Text(
                    "পণ্যের ছবি",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "একটার বেশি ছবি একসাথে যোগ করা যাবে। প্রথম/main ছবি নির্বাচন করতে ছবির উপর ট্যাপ করুন, মুছতে ✕ চাপুন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (galleryUrls.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(galleryUrls, key = { it }) { url ->
                            Box(Modifier.size(92.dp)) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = "Product image",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .border(
                                            if (url == image) 3.dp else 1.dp,
                                            if (url == image) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            MaterialTheme.shapes.small
                                        )
                                        .combinedClickable(
                                            onClick = { image = url },
                                            onLongClick = { copyImageLink(context, url) }
                                        ),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = {
                                        galleryUrls = galleryUrls.filterNot { it == url }
                                        if (image == url) image = galleryUrls.firstOrNull()
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).size(30.dp)
                                ) {
                                    Icon(Icons.Default.Close, "ছবি মুছুন")
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center
                    ) { Text("এখনও কোনো ছবি যোগ করা হয়নি", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Image URL") },
                        singleLine = true
                    )
                    IconButton(onClick = {
                        val u = urlInput.trim()
                        if (u.isNotBlank() && u !in galleryUrls) {
                            galleryUrls = galleryUrls + u
                            if (image.isNullOrBlank()) image = u
                        }
                        urlInput = ""
                    }, enabled = urlInput.isNotBlank()) { Icon(Icons.Default.Add, "যোগ") }
                }

                OutlinedButton(
                    onClick = { multiPicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uploading
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (uploading) "ছবি আপলোড হচ্ছে…" else "ছবি নির্বাচন / আপলোড")
                }

                if (initial != null) {
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "সাইজ / ভ্যারিয়েন্ট",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "৫০০ গ্রাম, ১ কেজি ইত্যাদি এখান থেকেই যোগ বা এডিট করুন।",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        FilledTonalButton(onClick = { addVariant = true }) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(4.dp))
                            Text("যোগ")
                        }
                    }

                    variantError?.let {
                        Text("ভ্যারিয়েন্ট লোড হয়নি: $it", color = MaterialTheme.colorScheme.error)
                    }
                    if (variantLoading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else if (variants.isEmpty()) {
                        Text(
                            "এখনও কোনো সাইজ যোগ করা হয়নি।",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        variants.forEach { v ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(v.label, fontWeight = FontWeight.Bold)
                                        Text("৳ ${money(v.price)} • স্টক ${v.stock} • ${v.weightGrams}g")
                                        Text(
                                            if (v.active) "Active" else "Off",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (v.active) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    IconButton(onClick = { editVariant = v }) {
                                        Icon(Icons.Default.Edit, "সাইজ এডিট")
                                    }
                                    IconButton(onClick = { deleteVariant = v }) {
                                        Icon(Icons.Default.Delete, "সাইজ ডিলিট")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "পণ্যটি প্রথমে সেভ করুন। এরপর একই এডিট অপশনে ঢুকেই ৫০০ গ্রাম / ১ কেজি ইত্যাদি সাইজ যোগ করতে পারবেন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SwitchRow("Featured", featured) { featured = it }
                SwitchRow("Flash sale", flash) { flash = it }
                SwitchRow("Hot deal", hot) { hot = it }
                formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    if (addVariant && initial != null) {
        VariantEditorDialog(
            null,
            onDismiss = { addVariant = false },
            onSave = { label, grams, vPrice, oldPrice, vStock, active, sortOrder ->
                scope.launch {
                    Repository().addVariant(
                        initial.id, label, grams, vPrice, oldPrice, vStock, sortOrder
                    ).fold(
                        { addVariant = false; variantRefresh++ },
                        { variantError = it.message }
                    )
                }
            }
        )
    }

    editVariant?.let { v ->
        VariantEditorDialog(
            v,
            onDismiss = { editVariant = null },
            onSave = { label, grams, vPrice, oldPrice, vStock, active, sortOrder ->
                scope.launch {
                    Repository().updateVariant(
                        v.copy(
                            label = label,
                            weightGrams = grams,
                            price = vPrice,
                            oldPrice = oldPrice,
                            stock = vStock,
                            active = active,
                            sortOrder = sortOrder
                        )
                    ).fold(
                        { editVariant = null; variantRefresh++ },
                        { variantError = it.message }
                    )
                }
            }
        )
    }

    deleteVariant?.let { v ->
        Confirm(
            "সাইজ ডিলিট করবেন?",
            "${v.label} (${v.weightGrams}g) স্থায়ীভাবে মুছে যাবে.",
            {
                scope.launch {
                    Repository().deleteVariant(v.id).fold(
                        { deleteVariant = null; variantRefresh++ },
                        { variantError = it.message; deleteVariant = null }
                    )
                }
            },
            { deleteVariant = null }
        )
    }
}


@Composable
private fun AdminImageControl(
    imageUrl: String,
    onImageUrlChange: (String) -> Unit,
    label: String = "ছবি",
    height: Dp = 190.dp,
    onUpload: suspend (Uri) -> Result<String>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uploading = true
            uploadError = null
            scope.launch {
                onUpload(uri).fold(
                    { onImageUrlChange(it) },
                    { uploadError = it.message ?: "ছবি আপলোড ব্যর্থ" }
                )
                uploading = false
            }
        }
    }

    uploadError?.let { error ->
        UploadErrorDialog(
            message = error,
            onDismiss = { uploadError = null }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = imageUrl,
            onValueChange = onImageUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Image URL") },
            singleLine = true
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl.isNotBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Image, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("এখানেই ছবির Live Preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { picker.launch("image/*") },
                modifier = Modifier.weight(1f),
                enabled = !uploading
            ) {
                Icon(Icons.Default.AddPhotoAlternate, null)
                Spacer(Modifier.width(5.dp))
                Text(if (uploading) "আপলোড হচ্ছে…" else "ছবি নির্বাচন / আপলোড")
            }
            OutlinedButton(
                onClick = { onImageUrlChange("") },
                modifier = Modifier.weight(1f),
                enabled = imageUrl.isNotBlank() && !uploading,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, null)
                Spacer(Modifier.width(5.dp))
                Text("ছবি মুছুন")
            }
        }
        Text(
            "URL লিখলে বা ছবি আপলোড করলে একই Frame-এর ভিতরেই Live Preview দেখা যাবে।",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


@Composable
private fun UploadErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "ছবি আপলোড ব্যর্থ",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "সমস্যার বিস্তারিত কারণ:",
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("ঠিক আছে")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(
                        android.content.Context.CLIPBOARD_SERVICE
                    ) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "Fol Bazar upload error",
                            message
                        )
                    )
                    Toast.makeText(
                        context,
                        "Error details copied",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            ) {
                Text("কপি")
            }
        }
    )
}

@Composable
private fun FullScreenEditorPage(
    onDismiss: () -> Unit,
    title: String,
    confirmText: String = "সেভ",
    confirmEnabled: Boolean = true,
    dismissText: String = "বাতিল",
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "ফিরে যান") }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider()
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(dismissText) }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = confirmEnabled, onClick = onConfirm) { Text(confirmText) }
                }
            }
        }
    }
}

@Composable
private fun CategoryEditorDialog(
    initial: Category?,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, Int) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var imageUrl by remember { mutableStateOf(initial?.imageUrl ?: "") }
    var sortOrder by remember { mutableStateOf(initial?.sortOrder?.toString() ?: "0") }

    FullScreenEditorPage(
        onDismiss = onDismiss,
        title = if (initial == null) "নতুন ক্যাটাগরি" else "ক্যাটাগরি এডিট",
        confirmEnabled = name.isNotBlank(),
        onConfirm = {
            onSave(
                name.trim(),
                description.trim().ifBlank { null },
                imageUrl.trim().ifBlank { null },
                sortOrder.toIntOrNull() ?: 0
            )
        }
    ) {
        Field(name, { name = it }, "ক্যাটাগরির নাম")
        Field(description, { description = it }, "বিবরণ", single = false)
        AdminImageControl(
            imageUrl = imageUrl,
            onImageUrlChange = { imageUrl = it },
            label = "ক্যাটাগরির ছবি",
            height = 150.dp,
            onUpload = { uri -> ServerStorageClient(context).uploadImage(uri, "categories") }
        )
        Field(sortOrder, { sortOrder = it }, "Sort order", KeyboardType.Number)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                if (initial?.active == false) "বর্তমান অবস্থা: Off" else "বর্তমান অবস্থা: Active",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}


@Composable
private fun Categories() {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Category>>(emptyList()) }
    var refresh by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var add by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Category?>(null) }
    var del by remember { mutableStateOf<Category?>(null) }

    LaunchedEffect(refresh) {
        loading = true
        Repository().categories().fold(
            { list = it; error = null },
            { error = it.message }
        )
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ক্যাটাগরি", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { add = true }) {
                Icon(Icons.Default.Add, null)
                Text("নতুন")
            }
        }
        Spacer(Modifier.height(10.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        RefreshableList(refresh, { refresh++ }) {
            if (loading && list.isEmpty()) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else if (!loading && list.isEmpty()) {
                item { EmptyState(if (error != null) "ডাটা লোড হয়নি" else "এখানে কোনো ক্যাটাগরি নেই") }
            }
            items(list, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        c.imageUrl?.let { AsyncImage(it, null, Modifier.size(48.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.SemiBold)
                            Text(if (c.active) "Active" else "Off")
                        }
                        IconButton(onClick = { edit = c }) { Icon(Icons.Default.Edit, null) }
                        IconButton(onClick = { del = c }) { Icon(Icons.Default.Delete, null) }
                    }
                }
            }
        }
    }

    if (add) {
        CategoryEditorDialog(null, { add = false }) { n: String, d: String?, img: String?, o: Int ->
            scope.launch {
                Repository().addCategory(n, d, img, o).fold(
                    { add = false; refresh++ },
                    { error = it.message }
                )
            }
        }
    }
    edit?.let { c ->
        CategoryEditorDialog(c, { edit = null }) { n: String, d: String?, img: String?, o: Int ->
            scope.launch {
                Repository().updateCategory(c.copy(name = n, description = d, imageUrl = img, sortOrder = o)).fold(
                    { edit = null; refresh++ },
                    { error = it.message }
                )
            }
        }
    }
    del?.let { c ->
        Confirm(
            "ক্যাটাগরি ডিলিট?",
            "${c.name} মুছে যাবে.",
            {
                scope.launch {
                    Repository().deleteCategory(c.id).fold(
                        { del = null; refresh++ },
                        { error = it.message; del = null }
                    )
                }
            },
            { del = null }
        )
    }
}

@Composable
private fun OrderDetailsDialog(
    o: Order,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(o.status) }
    var pay by remember { mutableStateOf(o.paymentStatus) }
    var items by remember { mutableStateOf<List<OrderItem>>(emptyList()) }
    var itemsLoading by remember { mutableStateOf(true) }
    var cats by remember { mutableStateOf<List<Category>>(emptyList()) }
    var viewProduct by remember { mutableStateOf<Product?>(null) }
    var productLoadingId by remember { mutableStateOf<String?>(null) }
    var productError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(o.id) {
        itemsLoading = true
        val loaded = Repository().orderItems(o.id).getOrDefault(emptyList())
        // পুরনো/টেস্ট অর্ডারে product_id সেভ না থাকলে বা ছবি না এলে, নাম মিলিয়ে বর্তমান পণ্য তালিকা থেকে লিংক করার চেষ্টা
        items = if (loaded.any { it.productId == null || it.imageUrl == null }) {
            val allProducts = Repository().products().getOrDefault(emptyList())
            loaded.map { item ->
                if (item.productId != null && item.imageUrl != null) return@map item
                val match = if (item.productId != null) allProducts.firstOrNull { p -> p.id == item.productId }
                else allProducts.firstOrNull { p -> p.name.equals(item.productName, ignoreCase = true) }
                if (match != null) item.copy(
                    productId = item.productId ?: match.id,
                    imageUrl = item.imageUrl ?: match.imageUrl
                ) else item
            }
        } else loaded
        itemsLoading = false
    }
    LaunchedEffect(Unit) {
        cats = Repository().categories().getOrDefault(emptyList())
    }

    fun openProduct(productId: String?) {
        if (productId == null) {
            Toast.makeText(context, "এই আইটেমের সাথে কোনো পণ্য যুক্ত পাওয়া যায়নি (হয়তো পণ্যটি মুছে ফেলা হয়েছে)", Toast.LENGTH_SHORT).show()
            return
        }
        productError = null
        productLoadingId = productId
        scope.launch {
            Repository().product(productId).fold(
                { p ->
                    if (p != null) viewProduct = p
                    else productError = "পণ্যটি আর পাওয়া যাচ্ছে না (মুছে ফেলা হয়েছে)"
                },
                { productError = it.message ?: "পণ্য লোড করা যায়নি" }
            )
            productLoadingId = null
        }
    }

    FullScreenEditorPage(
        onDismiss = onDismiss,
        title = "অর্ডার #${o.orderNumber}",
        confirmText = "আপডেট",
        onConfirm = { onSave(status, pay) }
    ) {
                Text(
                    "${o.customer} • ${o.phone}",
                    style = MaterialTheme.typography.bodySmall
                )
                OrderSection("দ্রুত অ্যাকশন") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { dialNumber(context, o.phone) },
                            modifier = Modifier.weight(1f),
                            enabled = o.phone.isNotBlank()
                        ) {
                            Icon(Icons.Default.Call, null)
                            Spacer(Modifier.width(5.dp))
                            Text("কল")
                        }
                        OutlinedButton(
                            onClick = { copyText(context, o.phone, "ফোন নম্বর কপি হয়েছে") },
                            modifier = Modifier.weight(1f),
                            enabled = o.phone.isNotBlank()
                        ) {
                            Icon(Icons.Default.ContentCopy, null)
                            Spacer(Modifier.width(5.dp))
                            Text("ফোন কপি")
                        }
                    }
                }
                OrderSection("কাস্টমার ও ঠিকানা") {
                    InfoLine("নাম", o.customer)
                    InfoLine("ফোন", o.phone)
                    InfoLine("ইমেইল", o.email)
                    InfoLine("ঠিকানা", o.address)
                    InfoLine("বিভাগ", o.division)
                    InfoLine("জেলা", o.district)
                    InfoLine("উপজেলা", o.upazila)
                    InfoLine("Delivery area", o.deliveryArea)
                    InfoLine("Shipping", o.shippingMethod)
                }

                OrderSection("পণ্য তালিকা") {
                    when {
                        itemsLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        items.isEmpty() -> Text("পণ্যের বিস্তারিত পাওয়া যায়নি", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> items.forEach { it2 ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { openProduct(it2.productId) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(52.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (it2.imageUrl != null) {
                                        AsyncImage(
                                            model = it2.imageUrl,
                                            contentDescription = it2.productName,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (productLoadingId == it2.productId) {
                                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(it2.productName + (it2.variantLabel?.takeIf { v -> v.isNotBlank() }?.let { v -> " ($v)" } ?: ""), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    Text("${it2.quantity} × ৳${money(it2.unitPrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("৳ ${money(it2.lineTotal)}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    productError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                OrderSection("মূল্য") {
                    InfoLine("Subtotal", "৳ ${money(o.subtotal)}")
                    InfoLine("Delivery", "৳ ${money(o.deliveryCharge)}")
                    InfoLine("Discount", "৳ ${money(o.discount)}")
                    InfoLine("Total", "৳ ${money(o.total)}", bold = true)
                }

                OrderSection("রশিদ ডাউনলোড") {
                    Text("সাইজ বেছে নিয়ে অর্ডার রশিদ ছবি বা PDF আকারে সেভ/শেয়ার করুন", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    var receiptSize by remember { mutableStateOf(ReceiptPaperSize.A4) }
                    var receiptBusy by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReceiptPaperSize.values().forEach { s ->
                            FilterChip(
                                selected = receiptSize == s,
                                onClick = { receiptSize = s },
                                label = { Text(s.label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val activity = OrderReceiptExporter.findActivity(context)
                                if (activity == null) {
                                    Toast.makeText(context, "রশিদ এক্সপোর্ট করা যায়নি", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                receiptBusy = true
                                scope.launch {
                                    try {
                                        val file = OrderReceiptExporter.exportImage(activity, o, items, receiptSize)
                                        OrderReceiptExporter.shareFile(context, file, "image/png")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "ছবি তৈরি ব্যর্থ: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        receiptBusy = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !itemsLoading && !receiptBusy
                        ) {
                            Icon(Icons.Default.Image, null)
                            Spacer(Modifier.width(5.dp))
                            Text("ছবি ডাউনলোড")
                        }
                        OutlinedButton(
                            onClick = {
                                val activity = OrderReceiptExporter.findActivity(context)
                                if (activity == null) {
                                    Toast.makeText(context, "রশিদ এক্সপোর্ট করা যায়নি", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                receiptBusy = true
                                scope.launch {
                                    try {
                                        val file = OrderReceiptExporter.exportPdf(activity, o, items, receiptSize)
                                        OrderReceiptExporter.shareFile(context, file, "application/pdf")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "PDF তৈরি ব্যর্থ: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        receiptBusy = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !itemsLoading && !receiptBusy
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null)
                            Spacer(Modifier.width(5.dp))
                            Text("PDF ডাউনলোড")
                        }
                    }
                    if (receiptBusy) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }

                OrderSection("পেমেন্ট") {
                    InfoLine("Method", paymentLabel(o.paymentMethod))
                    InfoLine("Payment title", o.paymentTitle)
                    InfoLine("Sender number", o.senderPhone)
                    InfoLine("TrxID", o.trxId)
                    InfoLine("Coupon", o.couponCode)
                }

                OrderSection("নোট") {
                    InfoLine("Delivery note", o.deliveryNote)
                    InfoLine("Order note", o.orderNote)
                }

                OrderSection("স্ট্যাটাস") {
                    Text("Order status", fontWeight = FontWeight.SemiBold)
                    SimpleChoice(status, ORDER_STATUSES) { status = it }
                    Text("Payment status", fontWeight = FontWeight.SemiBold)
                    SimpleChoice(pay, PAYMENT_STATUSES) { pay = it }
                }
    }

    viewProduct?.let { p ->
        ProductDialog(
            initial = p,
            cats = cats,
            onDismiss = { viewProduct = null }
        ) { n, d, pr, op, st, ci, img, gal, f, fl, h ->
            scope.launch {
                Repository().updateProduct(
                    p.copy(
                        name = n, description = d, price = pr, oldPrice = op,
                        stock = st, categoryId = ci, imageUrl = img, galleryUrls = gal,
                        featured = f, flashSale = fl, hotDeal = h
                    )
                ).fold(
                    { viewProduct = null },
                    { productError = it.message }
                )
            }
        }
    }
}


@Composable
private fun Orders() {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Order>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Order?>(null) }

    LaunchedEffect(refresh) {
        loading = true
        Repository().orders().fold(
            { list = it; error = null },
            { error = it.message }
        )
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("অর্ডার", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("অর্ডার, কাস্টমার, পেমেন্ট ও ডেলিভারি")
            }
            IconButton(onClick = { refresh++ }) {
                Icon(Icons.Default.Refresh, "রিফ্রেশ")
            }
        }
        Spacer(Modifier.height(10.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        RefreshableList(refresh, { refresh++ }) {
            if (loading && list.isEmpty()) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else if (!loading && list.isEmpty()) {
                item { EmptyState(if (error != null) "অর্ডারের ডাটা লোড হয়নি" else "এখনও কোনো অর্ডার আসেনি") }
            }

            items(list, key = { it.id }) { o ->
                Card(
                    Modifier.fillMaxWidth().clickable { selected = o },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("#${o.orderNumber}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(o.customer, fontWeight = FontWeight.SemiBold)
                                Text(o.phone, style = MaterialTheme.typography.bodySmall)
                            }
                            AssistChip(
                                onClick = { selected = o },
                                label = { Text(statusLabel(o.status)) }
                            )
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("মোট", style = MaterialTheme.typography.labelMedium)
                                Text("৳ ${money(o.total)}", fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("পেমেন্ট", style = MaterialTheme.typography.labelMedium)
                                Text(paymentLabel(o.paymentMethod), fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("পেমেন্ট স্ট্যাটাস", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    statusLabel(o.paymentStatus),
                                    color = if (o.paymentStatus == "paid")
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { o ->
        OrderDetailsDialog(
            o,
            onDismiss = { selected = null }
        ) { status: String, pay: String ->
            scope.launch {
                Repository().updateOrderStatus(o.id, status)
                Repository().updatePaymentStatus(o.id, pay)
                Repository().orders().fold(
                    { list = it; selected = null; error = null },
                    { error = it.message }
                )
            }
        }
    }
}



private fun statusLabel(value: String): String = when (value) {
    "pending" -> "Pending"
    "confirmed" -> "Confirmed"
    "processing" -> "Processing"
    "packed" -> "Packed"
    "shipped" -> "Shipped"
    "out_for_delivery" -> "Out for delivery"
    "delivered" -> "Delivered"
    "cancelled" -> "Cancelled"
    "returned" -> "Returned"
    "paid" -> "Paid"
    "failed" -> "Failed"
    "refunded" -> "Refunded"
    else -> value
}

private fun paymentLabel(value: String): String = when (value) {
    "cod" -> "Cash on delivery"
    "bkash" -> "bKash"
    "nagad" -> "Nagad"
    "rocket" -> "Rocket"
    "bank" -> "Bank"
    "shurjopay" -> "ShurjoPay"
    "card" -> "Card"
    else -> value
}


@Composable
private fun OrderSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String?, bold: Boolean = false) {
    if (!value.isNullOrBlank()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                "$label: ",
                modifier = Modifier.widthIn(min = 92.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun Customers() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var list by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Customer?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("সব") }

    LaunchedEffect(refresh) {
        loading = true
        Repository().customers().fold({ list = it; error = null }, { error = it.message })
        loading = false
    }

    // The chips are real field filters. With an empty search box they still
    // filter users by whether that field exists; when text is entered they
    // additionally perform a case-insensitive contains search in that field.
    val filtered = remember(list, query, filterMode) {
        val q = query.trim()
        list.filter { c ->
            val fieldMatches = when (filterMode) {
                "নাম" -> c.name.isNotBlank() && (q.isBlank() || c.name.contains(q, ignoreCase = true))
                "নম্বর" -> !c.phone.isNullOrBlank() && (q.isBlank() || c.phone.orEmpty().contains(q, ignoreCase = true))
                "Gmail" -> !c.email.isNullOrBlank() && (q.isBlank() || c.email.orEmpty().contains(q, ignoreCase = true))
                else -> q.isBlank() ||
                    c.name.contains(q, ignoreCase = true) ||
                    (c.phone ?: "").contains(q, ignoreCase = true) ||
                    (c.email ?: "").contains(q, ignoreCase = true)
            }
            fieldMatches
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("সকল ইউজার", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${filtered.size} জন • নাম, নম্বর বা Gmail দিয়ে খুঁজুন")
            }
            IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "রিফ্রেশ") }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = if (query.isNotBlank()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "মুছুন") } }) else null,
            label = { Text("${when (filterMode) { "নাম" -> "নাম"; "নম্বর" -> "ফোন নম্বর"; "Gmail" -> "Gmail"; else -> "ইউজার" }} খুঁজুন") },
            placeholder = { Text(if (filterMode == "সব") "নাম / ফোন / Gmail" else when (filterMode) { "নাম" -> "নাম লিখুন"; "নম্বর" -> "নম্বর লিখুন"; else -> "Gmail লিখুন" }) }
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("সব", "নাম", "নম্বর", "Gmail").forEach { mode ->
                FilterChip(selected = filterMode == mode, onClick = { filterMode = mode }, label = { Text(mode) })
            }
        }
        Spacer(Modifier.height(8.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        RefreshableList(refresh, { refresh++ }) {
            if (loading && list.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (!loading && list.isEmpty()) item { EmptyState(if (error != null) "ডাটা লোড হয়নি" else "এখানে কোনো ইউজার নেই") }
            else if (!loading && filtered.isEmpty()) item { EmptyState("এই ফিল্টারে কোনো ইউজার পাওয়া যায়নি") }

            items(filtered, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth().clickable { selected = c }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(c.email ?: "Gmail দেওয়া নেই", style = MaterialTheme.typography.bodySmall)
                                Text(c.phone ?: "নম্বর দেওয়া নেই", style = MaterialTheme.typography.bodySmall)
                            }
                            AssistChip(onClick = { selected = c }, label = { Text(c.role) })
                        }
                        if (!c.phone.isNullOrBlank()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { dialNumber(context, c.phone) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Call, null); Spacer(Modifier.width(5.dp)); Text("কল")
                                }
                                OutlinedButton(onClick = { copyText(context, c.phone, "নম্বর কপি হয়েছে") }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(5.dp)); Text("কপি")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { c ->
        UserDetailsDialog(c, onDismiss = { selected = null }) { role ->
            scope.launch {
                Repository().updateCustomerRole(c.id, role).fold(
                    { selected = null; refresh++ },
                    { error = it.message }
                )
            }
        }
    }
}

@Composable
private fun UserDetailsDialog(c: Customer, onDismiss: () -> Unit, onRoleSave: (String) -> Unit) {
    val context = LocalContext.current
    var role by remember(c.id) { mutableStateOf(c.role) }
    FullScreenEditorPage(
        onDismiss = onDismiss,
        title = "ইউজার তথ্য",
        onConfirm = { onRoleSave(role) }
    ) {
                UserInfoCard("ব্যক্তিগত তথ্য") {
                    CopyInfoLine("নাম", c.name, context)
                    CopyInfoLine("ফোন", c.phone, context)
                    CopyInfoLine("Gmail", c.email, context)
                    CopyInfoLine("ঠিকানা", c.address, context)
                    CopyInfoLine("User ID", c.id, context)
                    CopyInfoLine("যোগদানের সময়", c.createdAt, context)
                }
                UserInfoCard("দ্রুত অ্যাকশন") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!c.phone.isNullOrBlank()) FilledTonalButton({ dialNumber(context, c.phone) }, Modifier.weight(1f)) { Icon(Icons.Default.Call, null); Spacer(Modifier.width(5.dp)); Text("কল") }
                        if (!c.email.isNullOrBlank()) OutlinedButton({ copyText(context, c.email, "Gmail কপি হয়েছে") }, Modifier.weight(1f)) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(5.dp)); Text("Gmail কপি") }
                    }
                }
                Text("Role", fontWeight = FontWeight.SemiBold)
                SimpleChoice(role, CUSTOMER_ROLES) { role = it }
    }
}

@Composable
private fun UserInfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title, fontWeight = FontWeight.Bold); content() } }
}

@Composable
private fun CopyInfoLine(label: String, value: String?, context: android.content.Context) {
    if (!value.isNullOrBlank()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.labelSmall); Text(value) }
        IconButton(onClick = { copyText(context, value, "$label কপি হয়েছে") }) { Icon(Icons.Default.ContentCopy, "কপি") }
    }
}

private fun copyText(context: android.content.Context, value: String?, message: String) {
    if (value.isNullOrBlank()) return
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Fol Bazar", value))
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun dialNumber(context: android.content.Context, phone: String?) {
    if (phone.isNullOrBlank()) return
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")))
}

@Composable
private fun Complaints() {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Complaint>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Complaint?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(refresh) {
        loading = true
        Repository().complaints().fold(
            { list = it; error = null },
            { error = it.message }
        )
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("অভিযোগ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        RefreshableList(refresh, { refresh++ }) {
            if (loading && list.isEmpty()) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else if (!loading && list.isEmpty()) {
                item { EmptyState(if (error != null) "ডাটা লোড হয়নি" else "এখনও কোনো অভিযোগ আসেনি") }
            }
            items(list, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth().clickable { selected = c }) {
                    Column(Modifier.padding(12.dp)) {
                        Text("#${c.number}", fontWeight = FontWeight.Bold)
                        Text(c.subject ?: "অভিযোগ")
                        Text("${c.customerName} • ${c.phone}")
                        Text(c.description, maxLines = 2)
                        AssistChip(onClick = { selected = c }, label = { Text(c.status) })
                    }
                }
            }
        }
    }

    selected?.let { c ->
        ComplaintDialog(c, { selected = null }) { status, note ->
            scope.launch {
                Repository().updateComplaint(c.id, status, note).fold(
                    { selected = null; refresh++ },
                    { error = it.message; selected = null }
                )
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Banners() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var list by remember { mutableStateOf<List<SiteBanner>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<SiteBanner?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(refresh) {
        loading = true
        Repository().banners().fold({ list = it; error = null }, { error = it.message })
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ওয়েবসাইট ব্যানার", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Hero ও Promo banner সরাসরি Fol Bazar API থেকে নিয়ন্ত্রণ করুন", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { selected = null; showEditor = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("যোগ") }
        }
        Spacer(Modifier.height(10.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        RefreshableList(refresh, { refresh++ }) {
            if (loading && list.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (!loading && list.isEmpty()) item { EmptyState("এখনও কোনো ব্যানার নেই") }
            items(list, key = { it.id }) { b ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AsyncImage(model = b.imageUrl, contentDescription = b.altText, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 190.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.title?.ifBlank { null } ?: "ব্যানার", fontWeight = FontWeight.Bold)
                                Text("${b.bannerType.uppercase()} • ${if (b.active) "Active" else "Off"} • Sort ${b.sortOrder}", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { selected = b; showEditor = true }) { Text("এডিট") }
                            TextButton(onClick = {
                                scope.launch { Repository().deleteBanner(b.id).fold({ refresh++ }, { error = it.message }) }
                            }) { Text("ডিলিট", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        BannerEditorDialog(initial = selected, onDismiss = { showEditor = false }) { bannerType, title, altText, imageUrl, linkUrl, sortOrder, active, widthPercent, heightPx ->
            scope.launch {
                val repo = Repository()
                val result = if (selected == null) {
                    repo.addBanner(bannerType, title, altText, imageUrl, linkUrl, sortOrder, widthPercent, heightPx)
                } else {
                    repo.updateBanner(selected!!.copy(bannerType = bannerType, title = title, altText = altText, imageUrl = imageUrl, linkUrl = linkUrl, sortOrder = sortOrder, active = active, widthPercent = widthPercent, heightPx = heightPx))
                }
                result.fold({ showEditor = false; refresh++ }, { error = it.message })
            }
        }
    }
}

@Composable
private fun BannerEditorDialog(
    initial: SiteBanner?,
    onDismiss: () -> Unit,
    onSave: (String, String?, String, String, String?, Int, Boolean, Int, Int) -> Unit
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(initial?.bannerType ?: "hero") }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var alt by remember { mutableStateOf(initial?.altText ?: "ফল বাজার ব্যানার") }
    var imageUrl by remember { mutableStateOf(initial?.imageUrl ?: "") }
    var link by remember { mutableStateOf(initial?.linkUrl ?: "") }
    var sort by remember { mutableStateOf(initial?.sortOrder?.toString() ?: "0") }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    // Website ব্যানার সবসময় ফ্রেমের পুরো জায়গা নেয় (responsive), তাই ম্যানুয়াল width/height
    // নিয়ন্ত্রণের কোনো প্রয়োজন নেই — একটা স্থির ডিফল্ট মান পাঠানো হয় ব্যাকওয়ার্ড কম্প্যাটিবিলিটির জন্য।
    val widthPercent = 100
    val heightPx = initial?.heightPx ?: 180
    var uploading by remember { mutableStateOf(false) }
    val uploadScope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uploading = true
            uploadScope.launch {
                ServerStorageClient(context).uploadImage(uri, "banners").fold(
                    { imageUrl = it },
                    { uploadError = it.message ?: "ছবি আপলোড ব্যর্থ" }
                )
                uploading = false
            }
        }
    }

    uploadError?.let { error ->
        UploadErrorDialog(
            message = error,
            onDismiss = { uploadError = null }
        )
    }

    FullScreenEditorPage(
        onDismiss = onDismiss,
        title = if (initial == null) "নতুন ব্যানার" else "ব্যানার এডিট",
        confirmEnabled = imageUrl.isNotBlank() && sort.toIntOrNull() != null && !uploading,
        onConfirm = {
            onSave(type, title.trim().ifBlank { null }, alt.trim().ifBlank { "ফল বাজার ব্যানার" }, imageUrl.trim(), link.trim().ifBlank { null }, sort.toIntOrNull() ?: 0, active, widthPercent.toInt(), heightPx.toInt())
        }
    ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == "hero", { type = "hero" }, label = { Text("Hero") })
                    FilterChip(type == "promo", { type = "promo" }, label = { Text("Promo") })
                }
                Field(title, { title = it }, "Title")
                Field(alt, { alt = it }, "Alt text")

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                        .padding(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ব্যানার প্রিভিউ", fontWeight = FontWeight.Bold)
                        Text("Website-এ ব্যানার সবসময় নির্ধারিত ফ্রেমের পুরো জায়গা জুড়ে responsive-ভাবে বসে; size আলাদাভাবে ঠিক করার প্রয়োজন নেই।", style = MaterialTheme.typography.bodySmall)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = 190.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = alt,
                                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text("ছবির Live Preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        OutlinedTextField(
                            value = imageUrl,
                            onValueChange = { imageUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Image URL") },
                            singleLine = true
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.weight(1f), enabled = !uploading) {
                                Icon(Icons.Default.AddPhotoAlternate, null)
                                Spacer(Modifier.width(4.dp))
                                Text(if (uploading) "আপলোড…" else "ছবি নির্বাচন")
                            }
                            OutlinedButton(onClick = { imageUrl = "" }, modifier = Modifier.weight(1f), enabled = imageUrl.isNotBlank() && !uploading, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                Icon(Icons.Default.Delete, null)
                                Spacer(Modifier.width(4.dp))
                                Text("ছবি মুছুন")
                            }
                        }
                    }
                }
                Field(link, { link = it }, "Link URL (optional)")
                Field(sort, { sort = it }, "Sort order", KeyboardType.Number)
                SwitchRow("Active", active) { active = it }
    }
}

@Composable private fun Wishlist(){
    var list by remember{mutableStateOf<List<WishlistSummary>>(emptyList())}; var error by remember{mutableStateOf<String?>(null)}; var refresh by remember{mutableStateOf(0)}; var loading by remember{mutableStateOf(true)}
    LaunchedEffect(refresh){loading=true;Repository().wishlistSummary().fold({list=it;error=null},{error=it.message});loading=false}
    Column(Modifier.fillMaxSize().padding(16.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Wishlist",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("সবচেয়ে বেশি wishlist হওয়া পণ্য")};IconButton({refresh++}){Icon(Icons.Default.Refresh,null)}}
        Spacer(Modifier.height(10.dp));error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        RefreshableList(refresh,{refresh++}) {
            if (loading && list.isEmpty()) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else if (!loading && list.isEmpty()) {
                item { EmptyState(if (error != null) "Wishlist ডাটা লোড হয়নি" else "এখনও কোনো Wishlist ডাটা নেই") }
            }
            items(list,key={it.productId}){w->Card(Modifier.fillMaxWidth()){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text("#${w.productId.take(8)}",Modifier.weight(1f),fontWeight=FontWeight.SemiBold);AssistChip({},{Text("${w.count} wishlist")})}}}}
    }
}

private data class CouponForm(val code:String,val title:String?,val type:String,val value:Double,val min:Double,val max:Double?,val limit:Int?,val start:String?,val end:String?)

@Composable private fun CouponDialog(initial:Coupon?,onDismiss:()->Unit,onSave:(CouponForm)->Unit){var code by remember{mutableStateOf(initial?.code?:"")};var title by remember{mutableStateOf(initial?.title?:"")};var type by remember{mutableStateOf(initial?.discountType ?: "percent")};var value by remember{mutableStateOf(initial?.discountValue?.toString()?:"")};var min by remember{mutableStateOf(initial?.minOrder?.toString()?: "0")};var max by remember{mutableStateOf(initial?.maxDiscount?.toString()?: "")};var limit by remember{mutableStateOf(initial?.usageLimit?.toString()?: "")};FullScreenEditorPage(onDismiss=onDismiss,title=if(initial==null)"নতুন কুপন" else "কুপন এডিট",confirmEnabled=code.isNotBlank()&&value.toDoubleOrNull()!=null,onConfirm={onSave(CouponForm(code.trim(),title.trim().ifBlank{null},type,value.toDoubleOrNull()?:0.0,min.toDoubleOrNull()?:0.0,max.toDoubleOrNull(),limit.toIntOrNull(),initial?.startsAt,initial?.expiresAt))}){Field(code,{code=it},"কুপন কোড");Field(title,{title=it},"শিরোনাম");Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(type=="percent",{type="percent"},label={Text("Percent")});FilterChip(type=="fixed",{type="fixed"},label={Text("Fixed")})};Field(value,{value=it},"Discount value",KeyboardType.Decimal);Field(min,{min=it},"Minimum order",KeyboardType.Decimal);Field(max,{max=it},"Maximum discount",KeyboardType.Decimal);Field(limit,{limit=it},"Usage limit",KeyboardType.Number)}}


@Composable private fun RoleDialog(c:Customer,onDismiss:()->Unit,onSave:(String)->Unit){var role by remember{mutableStateOf(c.role)};FullScreenEditorPage(onDismiss=onDismiss,title="${c.name} — Role",onConfirm={onSave(role)}){SimpleChoice(role,CUSTOMER_ROLES){role=it}}}
@Composable private fun ComplaintDialog(c:Complaint,onDismiss:()->Unit,onSave:(String,String?)->Unit){var status by remember{mutableStateOf(c.status)};var note by remember{mutableStateOf(c.adminNote?:"")};FullScreenEditorPage(onDismiss=onDismiss,title="অভিযোগ #${c.number}",confirmText="আপডেট",onConfirm={onSave(status,note.trim().ifBlank{null})}){Text(c.description);SimpleChoice(status,COMPLAINT_STATUSES){status=it};Field(note,{note=it},"Admin note",single=false)}}

@Composable private fun SimpleChoice(selected:String,options:List<String>,onChange:(String)->Unit){var open by remember{mutableStateOf(false)};Box{OutlinedButton({open=true}){Text(selected)};DropdownMenu(open,{open=false}){options.forEach{DropdownMenuItem(text={Text(it)},onClick={onChange(it);open=false})}}}}
@Composable private fun SwitchRow(label:String,value:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));Switch(value,onChange)}}
@Composable private fun Field(value:String,onValueChange:(String)->Unit,label:String,type:KeyboardType=KeyboardType.Text,single:Boolean=true){OutlinedTextField(value,onValueChange,label={Text(label)},singleLine=single,keyboardOptions=KeyboardOptions(keyboardType=type),modifier=Modifier.fillMaxWidth())}
@Composable private fun Confirm(title:String,text:String,onConfirm:()->Unit,onDismiss:()->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(text)},confirmButton={TextButton(onClick=onConfirm){Text("ডিলিট",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick=onDismiss){Text("বাতিল")}})}
private fun money(v:Double)=String.format("%.2f",v)

@Composable
fun Coupons() {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Coupon>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var add by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Coupon?>(null) }
    var del by remember { mutableStateOf<Coupon?>(null) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(refresh) {
        loading = true
        Repository().coupons().fold(
            { list = it; error = null },
            { error = it.message }
        )
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "কুপন / ডিসকাউন্ট",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text("Percent বা fixed discount")
            }

            IconButton(onClick = { refresh++ }) {
                Icon(Icons.Default.Refresh, "রিফ্রেশ")
            }

            FilledTonalButton({ add = true }) {
                Icon(Icons.Default.Add, null)
                Text("নতুন")
            }
        }

        Spacer(Modifier.height(10.dp))
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        RefreshableList(refresh, { refresh++ }) {
            if (loading && list.isEmpty()) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else if (error != null && list.isEmpty()) {
                item { EmptyState("কুপনের ডাটা লোড হয়নি") }
            } else if (list.isEmpty()) {
                item { EmptyState("এখানে কোনো কুপন নেই") }
            }

            items(list, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.code, fontWeight = FontWeight.Bold)
                            Text(
                                "${c.discountType}: ${c.discountValue} • ব্যবহার ${c.usedCount}/${c.usageLimit ?: "∞"}"
                            )
                        }

                        Switch(
                            checked = c.active,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    Repository().updateCoupon(c.copy(active = enabled)).fold(
                                        { refresh++ },
                                        { error = it.message }
                                    )
                                }
                            }
                        )

                        IconButton({ edit = c }) {
                            Icon(Icons.Default.Edit, null)
                        }

                        IconButton({ del = c }) {
                            Icon(Icons.Default.Delete, null)
                        }
                    }
                }
            }
        }
    }

    if (add) {
        CouponDialog(null, { add = false }) { v ->
            scope.launch {
                Repository().addCoupon(
                    v.code, v.title, v.type, v.value,
                    v.min, v.max, v.limit, v.start, v.end
                ).fold(
                    { add = false; refresh++ },
                    { error = it.message }
                )
            }
        }
    }

    edit?.let { c ->
        CouponDialog(c, { edit = null }) { v ->
            scope.launch {
                Repository().updateCoupon(
                    c.copy(
                        code = v.code,
                        title = v.title,
                        discountType = v.type,
                        discountValue = v.value,
                        minOrder = v.min,
                        maxDiscount = v.max,
                        usageLimit = v.limit,
                        startsAt = v.start,
                        expiresAt = v.end
                    )
                ).fold(
                    { edit = null; refresh++ },
                    { error = it.message }
                )
            }
        }
    }

    del?.let { c ->
        Confirm(
            "কুপন ডিলিট?",
            c.code,
            {
                scope.launch {
                    Repository().deleteCoupon(c.id).fold(
                        { del = null; refresh++ },
                        { error = it.message; del = null }
                    )
                }
            },
            { del = null }
        )
    }
}




@Composable
private fun SettingsScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var download by remember { mutableStateOf(DownloadState()) }
    var installedFile by remember { mutableStateOf<java.io.File?>(null) }
    var logoUrl by remember { mutableStateOf("") }
    var logoUploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            logoUploading = true
            scope.launch {
                ServerStorageClient(context).uploadImage(uri, "banners").fold(
                    { url ->
                        logoUrl = url
                        Repository().saveSetting("logo_url", JsonPrimitive(url))
                    },
                    { uploadError = it.message ?: "লোগো আপলোড ব্যর্থ" }
                )
                logoUploading = false
            }
        }
    }

    uploadError?.let { error ->
        UploadErrorDialog(
            message = error,
            onDismiss = { uploadError = null }
        )
    }

    fun check() {
        if (checking) return
        checking = true
        message = null
        scope.launch {
            when (val result = UpdateManager.checkForUpdate()) {
                UpdateResult.UpToDate -> {
                    update = null
                    message = "আপনার অ্যাপ বর্তমানে সর্বশেষ ভার্সনে আছে।"
                }
                is UpdateResult.Available -> {
                    update = result.info
                    message = null
                }
                is UpdateResult.Error -> {
                    update = null
                    message = result.message
                }
            }
            checking = false
        }
    }

    LaunchedEffect(Unit) { check() }
    LaunchedEffect(Unit) { Repository().setting("logo_url").getOrNull()?.jsonPrimitive?.contentOrNull?.let { logoUrl = it } }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("সেটিংস", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Fol Bazar Admin • বর্তমান ভার্সন ${BuildConfig.VERSION_NAME}")
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ওয়েবসাইট লোগো", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("সার্ভারে লোগো আপলোড করে site_settings.logo_url-এ সংরক্ষণ করুন।", style = MaterialTheme.typography.bodySmall)
                    if (logoUrl.isNotBlank()) AsyncImage(model = logoUrl, contentDescription = "Logo", modifier = Modifier.size(84.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { logoPicker.launch("image/*") }, enabled = !logoUploading) {
                            Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text(if (logoUploading) "আপলোড হচ্ছে…" else "লোগো আপলোড")
                        }
                        if (logoUrl.isNotBlank()) TextButton(onClick = { logoUrl = "" }) { Text("URL মুছুন") }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("অ্যাপ আপডেট", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "cPanel-এর update.json থেকে নতুন Admin APK-এর তথ্য যাচাই করা হয়। নতুন ভার্সন থাকলে এখান থেকেই নিরাপদভাবে ডাউনলোড ও ইনস্টল করা যাবে।",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    when {
                        checking -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("নতুন আপডেট খোঁজা হচ্ছে…")
                        }
                        update != null -> {
                            val info = update!!
                            Text("নতুন ভার্সন পাওয়া গেছে: ${info.versionName}", fontWeight = FontWeight.Bold)
                            Text(info.releaseName)
                            if (download.running) {
                                LinearProgressIndicator(
                                    progress = { download.progress / 100f },
                                    Modifier.fillMaxWidth()
                                )
                                Text(
                                    if (download.totalBytes > 0)
                                        "ডাউনলোড হচ্ছে… ${download.progress}% (${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalBytes)})"
                                    else
                                        "ডাউনলোড হচ্ছে… ${formatBytes(download.downloadedBytes)}"
                                )
                            } else if (installedFile != null) {
                                Text("ডাউনলোড সম্পন্ন হয়েছে। এখন ইনস্টল করুন।", fontWeight = FontWeight.Bold)
                                Button(
                                    onClick = {
                                        if (!UpdateManager.canInstallPackages(context)) {
                                            UpdateManager.openUnknownSourcesSettings(context)
                                        } else {
                                            UpdateManager.installApk(context, installedFile!!)
                                                .onFailure { message = it.message ?: "ইনস্টল শুরু করা যায়নি" }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    content = {
                                        Icon(Icons.Default.InstallMobile, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            if (UpdateManager.canInstallPackages(context))
                                                "আপডেট ইনস্টল করুন"
                                            else
                                                "Install permission চালু করুন"
                                        )
                                    }
                                )
                            } else {
                                Button(
                                    onClick = {
                                        download = DownloadState(running = true)
                                        scope.launch {
                                            UpdateManager.downloadUpdate(context, info) { state ->
                                                download = state
                                            }.fold(
                                                { installedFile = it; download = DownloadState(progress = 100, downloadedBytes = it.length(), totalBytes = it.length(), file = it) },
                                                { error -> message = error.message ?: "ডাউনলোড ব্যর্থ হয়েছে"; download = DownloadState(error = error.message) }
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    content = {
                                        Icon(Icons.Default.Download, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("নতুন আপডেট ডাউনলোড করুন")
                                    }
                                )
                            }
                        }
                        else -> {
                            message?.let {
                                Text(
                                    it,
                                    color = if (it.contains("সর্বশেষ")) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { check() },
                        enabled = !checking && !download.running,
                        modifier = Modifier.fillMaxWidth(),
                        content = {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(6.dp))
                            Text("আপডেট চেক করুন")
                        }
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("আপডেট কীভাবে কাজ করবে", fontWeight = FontWeight.Bold)
                    Text("1. cPanel-এর update.json-এ নতুন ভার্সন প্রকাশ হলে অ্যাপ সেটি শনাক্ত করবে।")
                    Text("2. নতুন ভার্সন থাকলে এই পেজে দেখাবে।")
                    Text("3. ডাউনলোডে চাপলে অগ্রগতি (%) দেখা যাবে।")
                    Text("4. ডাউনলোড শেষ হলে এখানেই Install বাটন আসবে।")
                    Text("5. Android-এর নিরাপত্তার কারণে প্রথমবার এই অ্যাপের জন্য 'Install unknown apps' অনুমতি লাগতে পারে।")
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("থিম", fontWeight = FontWeight.Bold)
                    Text("অ্যাপের লুক Light / Dark / Device অনুযায়ী বেছে নিন", style = MaterialTheme.typography.bodySmall)
                    var themeMode by remember { mutableStateOf(ThemePrefs.mode) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = themeMode == ThemePrefs.Mode.SYSTEM,
                            onClick = { ThemePrefs.updateMode(ThemePrefs.Mode.SYSTEM); themeMode = ThemePrefs.Mode.SYSTEM },
                            label = { Text("Device") },
                            leadingIcon = { Icon(Icons.Default.PhoneAndroid, null) }
                        )
                        FilterChip(
                            selected = themeMode == ThemePrefs.Mode.LIGHT,
                            onClick = { ThemePrefs.updateMode(ThemePrefs.Mode.LIGHT); themeMode = ThemePrefs.Mode.LIGHT },
                            label = { Text("Light") },
                            leadingIcon = { Icon(Icons.Default.LightMode, null) }
                        )
                        FilterChip(
                            selected = themeMode == ThemePrefs.Mode.DARK,
                            onClick = { ThemePrefs.updateMode(ThemePrefs.Mode.DARK); themeMode = ThemePrefs.Mode.DARK },
                            label = { Text("Dark") },
                            leadingIcon = { Icon(Icons.Default.DarkMode, null) }
                        )
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("অ্যাকাউন্ট", fontWeight = FontWeight.Bold)
                        Text("Admin account থেকে নিরাপদে লগআউট করুন")
                    }
                    OutlinedButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, null)
                        Spacer(Modifier.width(5.dp))
                        Text("লগআউট")
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
    return String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
