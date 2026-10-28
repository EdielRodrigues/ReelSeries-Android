plugins { id("com.android.application") }
android {
 namespace = "com.reelseries.android"
 compileSdk = 35
 defaultConfig { applicationId = "com.reelseries.android"; minSdk = 24; targetSdk = 35; versionCode = 40; versionName = "4.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
 implementation("androidx.media3:media3-exoplayer:1.5.1")
 implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
 implementation("androidx.media3:media3-ui:1.5.1")
}
