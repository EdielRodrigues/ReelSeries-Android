plugins { id("com.android.application"); id("com.google.gms.google-services") }
android {
 namespace = "com.reelseries.android"
 compileSdk = 35
 defaultConfig { applicationId = "com.reelseries.android"; minSdk = 24; targetSdk = 35; versionCode = 1000020; versionName = "21.3" }
 signingConfigs {
  getByName("debug") {
   storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
   storePassword = "android"
   keyAlias = "androiddebugkey"
   keyPassword = "android"
  }
 }
 buildTypes {
  getByName("debug") {
   signingConfig = signingConfigs.getByName("debug")
  }
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
 implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
 implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
 implementation("com.google.firebase:firebase-auth")
 implementation("com.google.firebase:firebase-firestore")
 implementation("androidx.media3:media3-exoplayer:1.5.1")
 implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
 implementation("androidx.media3:media3-ui:1.5.1")
 implementation("com.google.android.gms:play-services-cast-framework:22.2.0")
}
