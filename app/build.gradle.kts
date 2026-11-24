import groovy.json.JsonSlurper
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.kotlin.serialization)
}

val expectedPackageName = "com.aistudio.mousomiai.xkmzpq"
val ciVersionCode =
  providers.gradleProperty("versionCode").orNull?.toIntOrNull()
    ?: providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull()
    ?: 1
val ciVersionName =
  providers.gradleProperty("versionName").orNull?.takeIf { it.isNotBlank() }
    ?: providers.environmentVariable("VERSION_NAME").orNull?.takeIf { it.isNotBlank() }
    ?: "1.0"

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = expectedPackageName
    minSdk = 24
    targetSdk = 36
    versionCode = ciVersionCode
    versionName = ciVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
        ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      val userHomeJar =
        file(
          "${System.getProperty("user.home")}/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7/android-all-instrumented-16-robolectric-13921718-i7.jar"
        )
      val containerCacheDir =
        file("/home/appuser/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7")
      val containerCacheJar =
        file("${containerCacheDir.absolutePath}/android-all-instrumented-16-robolectric-13921718-i7.jar")
      val explicitOffline = System.getProperty("robolectric.offline")
      val explicitDepDir = System.getProperty("robolectric.dependency.dir")
      val explicitRepoUrl = System.getProperty("robolectric.dependency.repo.url")
      val useContainerCache =
        explicitOffline == null &&
          explicitDepDir == null &&
          !userHomeJar.exists() &&
          containerCacheJar.exists()
      val containerCachePath = containerCacheDir.absolutePath
      all { testTask ->
        if (explicitOffline != null) {
          testTask.systemProperty("robolectric.offline", explicitOffline)
        }
        if (explicitDepDir != null) {
          testTask.systemProperty("robolectric.dependency.dir", explicitDepDir)
        }
        if (explicitRepoUrl != null) {
          testTask.systemProperty("robolectric.dependency.repo.url", explicitRepoUrl)
        }
        if (useContainerCache) {
          testTask.systemProperty("robolectric.offline", "true")
          testTask.systemProperty("robolectric.dependency.dir", containerCachePath)
        }
      }
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

val validateGoogleServicesConfig by tasks.registering {
  group = "verification"
  description =
    "Validates app/google-services.json package name ($expectedPackageName) and OAuth Web Client ID."
  val googleServicesFile = layout.projectDirectory.file("google-services.json").asFile
  val requiredPackage = expectedPackageName
  doLast {
    if (!googleServicesFile.exists() || googleServicesFile.length() == 0L) {
      throw GradleException(
        "Missing app/google-services.json. Add a real Firebase google-services.json for " +
          "$requiredPackage (or configure GOOGLE_SERVICES_JSON_BASE64 in CI) before building a signed Release APK."
      )
    }
    val root = JsonSlurper().parse(googleServicesFile) as? Map<*, *>
      ?: throw GradleException("Invalid JSON in app/google-services.json.")
    val clients = root["client"] as? List<*> ?: emptyList<Any>()
    val matchingClient = clients.filterIsInstance<Map<*, *>>().firstOrNull { client ->
      val clientInfo = client["client_info"] as? Map<*, *>
      val androidInfo = clientInfo?.get("android_client_info") as? Map<*, *>
      androidInfo?.get("package_name")?.toString() == requiredPackage
    } ?: throw GradleException(
      "app/google-services.json does not contain an Android client for package_name '$requiredPackage'."
    )

    val oauthClients = matchingClient["oauth_client"] as? List<*> ?: emptyList<Any>()
    val webClient = oauthClients.filterIsInstance<Map<*, *>>().firstOrNull { oauth ->
      val typeNum = (oauth["client_type"] as? Number)?.toInt()
      val clientId = oauth["client_id"]?.toString()?.trim().orEmpty()
      typeNum == 3 &&
        clientId.isNotEmpty() &&
        !clientId.equals("missing-google-services-json", ignoreCase = true)
    } ?: throw GradleException(
      "app/google-services.json client '$requiredPackage' is missing an OAuth Web Client ID " +
        "(oauth_client with client_type = 3). Register SHA-1/SHA-256 and enable Google Sign-In in Firebase Console, then re-download google-services.json."
    )

    val webClientId = webClient["client_id"]?.toString()?.trim().orEmpty()
    logger.lifecycle(
      "Validated app/google-services.json for package '$requiredPackage' (Web Client ID present: ${webClientId.take(12)}...)."
    )
  }
}

val verifyReleaseGoogleServicesOutput by tasks.registering {
  group = "verification"
  description = "Verifies that Google Services plugin generated default_web_client_id for Release."
  val generatedValuesFile =
    layout.buildDirectory.file("generated/res/processReleaseGoogleServices/values/values.xml")
  doLast {
    val file = generatedValuesFile.get().asFile
    if (!file.exists()) {
      throw GradleException(
        "Google Services plugin did not generate ${file.absolutePath}. Ensure app/google-services.json is valid."
      )
    }
    val text = file.readText()
    val match = Regex("<string name=\"default_web_client_id\"[^>]*>([^<]+)</string>").find(text)
    val value = match?.groupValues?.getOrNull(1)?.trim().orEmpty()
    if (value.isBlank() || value.equals("missing-google-services-json", ignoreCase = true)) {
      throw GradleException(
        "Generated Release resources do not contain a valid default_web_client_id."
      )
    }
    logger.lifecycle("Verified generated Release default_web_client_id (${value.take(12)}...).")
  }
}

tasks.matching { it.name == "processReleaseGoogleServices" }.configureEach {
  dependsOn(validateGoogleServicesConfig)
  finalizedBy(verifyReleaseGoogleServicesOutput)
}

configurations.all {
  resolutionStrategy {
    force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.21")
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.ai)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.mlkit.vision.objectdetection)
  implementation(libs.play.services.mlkit.subject.segmentation)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.coil.compose)
  implementation(libs.converter.kotlinx.serialization)
  implementation(libs.hilt.android)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.logging.interceptor)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.hilt.compiler)
}
