plugins { id("com.android.application"); id("com.google.gms.google-services") }
android {
 namespace = "com.reelseries.android"
 compileSdk = 35
 defaultConfig { applicationId = "com.reelseries.android"; minSdk = 24; targetSdk = 35; versionCode = 60; versionName = "6.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
 implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
 implementation("com.google.firebase:firebase-auth")
 implementation("com.google.firebase:firebase-firestore")
 implementation("androidx.media3:media3-exoplayer:1.5.1")
 implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
 implementation("androidx.media3:media3-ui:1.5.1")
}
