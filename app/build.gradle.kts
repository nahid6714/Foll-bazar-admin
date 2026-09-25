import java.util.Properties
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(n: String): String = localProperties.getProperty(n, "")

android {
    namespace="com.folbazar.admin"
    compileSdk=35
    defaultConfig {
        applicationId="com.folbazar.admin"
        minSdk=26
        targetSdk=35
        val versionCodeValue = (System.getenv("APP_VERSION_CODE")?.toIntOrNull()
            ?: prop("APP_VERSION_CODE").toIntOrNull() ?: 19).coerceAtLeast(1)
        val versionNameValue = System.getenv("APP_VERSION_NAME")?.takeIf { it.isNotBlank() }
            ?: prop("APP_VERSION_NAME").takeIf { it.isNotBlank() }
            ?: "1.1.$versionCodeValue"
        versionCode=versionCodeValue
        versionName=versionNameValue
    }
    buildFeatures { compose=true; buildConfig=true }
    // Legacy Bus Terminal sources remain in the repository for history, but must not
    // be compiled as part of the current Fol Bazar Admin application.
    sourceSets["main"].java.exclude("com/example/**")
    kotlin.sourceSets.getByName("main").kotlin.exclude("com/example/**")
    buildTypes {
        debug {
            buildConfigField("String","ADMIN_API_BASE_URL","\"${prop("ADMIN_API_BASE_URL").ifBlank { "https://lakebazar.com/backend/api" }}\"")
            buildConfigField("String","ADMIN_UPDATE_BASE_URL","\"${prop("ADMIN_UPDATE_BASE_URL").ifBlank { "https://lakebazar.com/admin-app" }}\"")
            buildConfigField("String","ADMIN_UPDATE_MANIFEST_URL","\"${prop("ADMIN_UPDATE_MANIFEST_URL").ifBlank { "https://lakebazar.com/backend/api/update.php?action=manifest" }}\"")
        }
        release {
            isMinifyEnabled=false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro")
            buildConfigField(
                "String",
                "ADMIN_API_BASE_URL",
                "\"${System.getenv("ADMIN_API_BASE_URL")?.takeIf { it.isNotBlank() } ?: prop("ADMIN_API_BASE_URL").ifBlank { "https://lakebazar.com/backend/api" }}\""
            )
            buildConfigField(
                "String",
                "ADMIN_UPDATE_BASE_URL",
                "\"${System.getenv("ADMIN_UPDATE_BASE_URL")?.takeIf { it.isNotBlank() } ?: prop("ADMIN_UPDATE_BASE_URL").ifBlank { "https://lakebazar.com/admin-app" }}\""
            )
            buildConfigField(
                "String",
                "ADMIN_UPDATE_MANIFEST_URL",
                "\"${System.getenv("ADMIN_UPDATE_MANIFEST_URL")?.takeIf { it.isNotBlank() } ?: prop("ADMIN_UPDATE_MANIFEST_URL").ifBlank { "https://lakebazar.com/backend/api/update.php?action=manifest" }}\""
            )
        }
    }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget="17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

// Belt-and-braces removal of the legacy Bus Terminal sources (com/example/**).
// The sourceSet excludes above are sometimes ignored by the Kotlin compiler task,
// so this both (a) deletes the legacy folder on disk before every build, and
// (b) excludes it directly from every KotlinCompile task as a second safety net.
val legacyExampleDir = project.file("src/main/java/com/example")
tasks.matching { it.name == "preBuild" }.configureEach {
    doFirst {
        if (legacyExampleDir.exists()) {
            legacyExampleDir.deleteRecursively()
        }
    }
}
tasks.withType<KotlinCompile>().configureEach {
    exclude("**/com/example/**")
}
