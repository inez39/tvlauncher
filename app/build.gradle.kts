plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// As chaves vêm dos "Secrets" do GitHub (ver README). Nunca ficam escritas no código.
fun env(name: String): String =
    (System.getenv(name) ?: "").replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.inez.tvlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.inez.tvlauncher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "TMDB_KEY", "\"${env("TMDB_KEY")}\"")
        buildConfigField("String", "YT_KEY", "\"${env("YT_KEY")}\"")
        buildConfigField("String", "CAL_ICS_URL", "\"${env("CAL_ICS_URL")}\"")
        buildConfigField("String", "PEPPA_CHANNEL_ID", "\"${env("PEPPA_CHANNEL_ID")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
