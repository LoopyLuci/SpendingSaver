import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.20-1.0.14"
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.spendingsaver"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.spendingsaver"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Release signing is read from the git-ignored keystore.properties file
    // (storeFile, storePassword, keyAlias, keyPassword). Without it, release
    // builds stay unsigned; debug builds are unaffected.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
    }
    val keystoreComplete = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .all { !keystoreProps.getProperty(it).isNullOrBlank() }
    val keystoreFile = keystoreProps.getProperty("storeFile")?.takeIf { it.isNotBlank() }?.let { rootProject.file(it) }
    val hasReleaseSigning = keystoreComplete && keystoreFile?.exists() == true
    if (keystorePropsFile.exists() && !hasReleaseSigning) {
        logger.warn("keystore.properties is incomplete or its storeFile does not exist; the release build will be unsigned.")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "DEBUG_OVERLAYS", "false")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Opt-in side-by-side install, e.g. -PdebugAppIdSuffix=.debug, so a debug build
            // never has to replace (or uninstall) an app signed with a different key.
            (findProperty("debugAppIdSuffix") as String?)?.let { applicationIdSuffix = it }
            buildConfigField("boolean", "DEBUG_OVERLAYS", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.activity:activity-compose:1.7.2")
    implementation("androidx.compose.ui:ui:1.4.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.4.3")
    implementation("androidx.compose.compiler:compiler:1.5.4")
    implementation("androidx.compose.ui:ui-text-google-fonts:1.4.3")
    implementation("androidx.compose.material3:material3:1.1.0")
    implementation("androidx.compose.material:material-icons-extended:1.4.3")
    implementation("androidx.navigation:navigation-compose:2.6.0")
    implementation("androidx.room:room-runtime:2.5.2")
    ksp("androidx.room:room-compiler:2.5.2")
    implementation("androidx.room:room-ktx:2.5.2")
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    implementation("com.opencsv:opencsv:5.7.1")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.13.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
