import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
  if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { load(it) }
  }
}

val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
  if (localPropertiesFile.exists()) {
    val cleaned = localPropertiesFile.readText(Charsets.UTF_8).removePrefix("\uFEFF")
    load(cleaned.reader())
  }
}

val admobPropertiesFile = rootProject.file("admob.properties")
val admobProperties = Properties().apply {
  if (admobPropertiesFile.exists()) {
    val cleaned = admobPropertiesFile.readText(Charsets.UTF_8).removePrefix("\uFEFF")
    load(cleaned.reader())
  }
}

val officialPublisherPrefix = "ca-app-pub-5019798166308009"
val prodAppIdRegex = Regex("""^ca-app-pub-5019798166308009~\d{10}$""")
val prodUnitIdRegex = Regex("""^ca-app-pub-5019798166308009/\d{10}$""")

fun resolveOfficialAdMobValue(key: String, isAppId: Boolean): String {
  val candidate = System.getenv(key)?.trim()?.takeIf { it.isNotEmpty() }
    ?: localProperties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
    ?: admobProperties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
    ?: ""

  check(candidate.isNotEmpty()) {
    "admob.properties 또는 local.properties 파일에 정식 Google AdMob 설정($key)이 누락되었습니다."
  }
  check(candidate.startsWith(officialPublisherPrefix)) {
    "$key 값($candidate)에 비정식 또는 샘플 광고 게시자 ID는 허용되지 않습니다."
  }
  val expectedRegex = if (isAppId) prodAppIdRegex else prodUnitIdRegex
  check(expectedRegex.matches(candidate)) {
    "$key 값($candidate)이 정식 Google AdMob ID 형식과 일치하지 않습니다."
  }
  return candidate
}

val resolvedAdMobAppId = resolveOfficialAdMobValue("ADMOB_APP_ID", isAppId = true)
val resolvedBannerHomeUnitId = resolveOfficialAdMobValue("ADMOB_BANNER_HOME_UNIT_ID", isAppId = false)
val resolvedInterstitialExitUnitId = resolveOfficialAdMobValue("ADMOB_INTERSTITIAL_EXIT_UNIT_ID", isAppId = false)

android {
  namespace = "com.jjalddalmark.minigamerullet"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.jjalddalmark.minigamerullet"
    minSdk = 26
    targetSdk = 36
    versionCode = 13
    versionName = "1.7.5"

    // Debug · Release 전 빌드에서 테스트 광고 ID 없이 정식 Google AdMob ID만 주입
    manifestPlaceholders["admobAppId"] = resolvedAdMobAppId
    resValue("string", "admob_app_id", resolvedAdMobAppId)
    resValue("string", "admob_banner_home_unit_id", resolvedBannerHomeUnitId)
    resValue("string", "admob_interstitial_exit_unit_id", resolvedInterstitialExitUnitId)
  }

  signingConfigs {
    if (keystorePropertiesFile.exists()) {
      create("release") {
        storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
        storePassword = keystoreProperties.getProperty("storePassword")
        keyAlias = keystoreProperties.getProperty("keyAlias")
        keyPassword = keystoreProperties.getProperty("keyPassword")
      }
    }
  }

  androidResources {
    localeFilters += listOf("ko")
    noCompress += "mp4"
  }

  bundle {
    language {
      enableSplit = false
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      signingConfig = if (keystorePropertiesFile.exists()) {
        signingConfigs.getByName("release")
      } else {
        signingConfigs.getByName("debug")
      }
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    resValues = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  packaging {
    jniLibs {
      useLegacyPackaging = false
    }
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

// 빌드 완료 직후 .\dist 단일 폴더로 디버그 테스트 APK · 릴리스 서명 APK · 스토어 AAB 자동 동기화
val distDirFile = rootProject.layout.projectDirectory.dir("dist").asFile
val distAppId = android.defaultConfig.applicationId ?: "com.jjalddalmark.minigamerullet"
val distAppVer = android.defaultConfig.versionName ?: "1.7.5"

val syncDebugApkToDist = tasks.register<Copy>("syncDebugApkToDist") {
  val targetName = "${distAppId}-v${distAppVer}-debug.apk"
  val outDir = distDirFile
  from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
  into(outDir)
  rename { targetName }
  doFirst {
    outDir.mkdirs()
    outDir.listFiles()
      ?.filter { it.isFile && it.name.endsWith("-debug.apk") && it.name != targetName }
      ?.forEach { it.delete() }
  }
}

val syncReleaseApkToDist = tasks.register<Copy>("syncReleaseApkToDist") {
  val targetName = "${distAppId}-v${distAppVer}-release.apk"
  val outDir = distDirFile
  from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
  into(outDir)
  rename { targetName }
  doFirst {
    outDir.mkdirs()
    outDir.listFiles()
      ?.filter { it.isFile && it.name.endsWith("-release.apk") && it.name != targetName }
      ?.forEach { it.delete() }
  }
}

val syncReleaseBundleToDist = tasks.register<Copy>("syncReleaseBundleToDist") {
  val targetName = "${distAppId}-v${distAppVer}-release.aab"
  val outDir = distDirFile
  from(layout.buildDirectory.file("outputs/bundle/release/app-release.aab"))
  into(outDir)
  rename { targetName }
  doFirst {
    outDir.mkdirs()
    outDir.listFiles()
      ?.filter { it.isFile && it.name.endsWith("-release.aab") && it.name != targetName }
      ?.forEach { it.delete() }
  }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
  finalizedBy(syncDebugApkToDist)
}
tasks.matching { it.name == "assembleRelease" }.configureEach {
  finalizedBy(syncReleaseApkToDist)
}
tasks.matching { it.name == "bundleRelease" }.configureEach {
  finalizedBy(syncReleaseBundleToDist)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  debugImplementation(libs.androidx.compose.ui.tooling)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Google AdMob 정식 수익화 SDK (홈 하단 배너 및 종료·최소화 전면 광고)
  implementation(libs.play.services.ads)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
}
