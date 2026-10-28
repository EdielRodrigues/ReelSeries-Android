plugins { id("com.android.application") }
android {
 namespace = "com.reelseries.android"
 compileSdk = 35
 defaultConfig { applicationId = "com.reelseries.android"; minSdk = 24; targetSdk = 35; versionCode = 30; versionName = "3.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
