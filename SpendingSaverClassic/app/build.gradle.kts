import java.util.Properties

plugins {
    id("com.android.application")
}

// SpendingSaver Classic: a tiny native shell around an offline web app, for old Android devices
// (4.0.3 and up, for example the Kindle Fire 2nd generation). It has no dependencies, so it does not
// need the libraries the main app uses. It has its own application id, so it never replaces or
// shares data with the main app, but it is signed with the same release key.
android {
    namespace = "com.spendingsaver.classic"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.spendingsaver.classic"
        minSdk = 15
        targetSdk = 34
        versionCode = 1
        versionName = (findProperty("classicVersion") as String?) ?: "1.1.4"
    }

    // The release key lives with the main app (git-ignored keystore.properties there).
    val keystorePropsFile = rootProject.file("../SpendingSaverAndroid/keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
    }
    val keystoreFile = keystoreProps.getProperty("storeFile")?.takeIf { it.isNotBlank() }
        ?.let { keystorePropsFile.parentFile.resolve(it) }
    val hasReleaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .all { !keystoreProps.getProperty(it).isNullOrBlank() } && keystoreFile?.exists() == true
    if (!hasReleaseSigning) logger.warn("No release keystore found; the release build will be unsigned.")

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
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    lint {
        // Old on purpose: this build exists to run on Android 4.
        disable += setOf("OldTargetApi", "ExpiredTargetSdkVersion", "SetJavaScriptEnabled", "AddJavascriptInterface")
    }
}

dependencies {
    // Tests only: JUnit, and a real org.json (the Android one is a stub on the JVM).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
