import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

// ============================================
// 🔒 LOAD SECRETS SECURELY
// ============================================
val secretsPropertiesFile = rootProject.file("secrets.properties")
val secretsProperties = Properties()

if (secretsPropertiesFile.exists()) {
    secretsProperties.load(FileInputStream(secretsPropertiesFile))
} else {
    secretsProperties["API_BASE_URL"] = System.getenv("API_BASE_URL") ?: "https://shopper-zibt.onrender.com/"
}

android {
    namespace = "com.smallbasket.dozo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smallbasket.dozo"
        minSdk = 24
        targetSdk = 35
        versionCode = 10
        versionName = "2.0.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${secretsProperties["API_BASE_URL"]}\""
        )

        buildConfigField("int", "VERSION_CODE", "$versionCode")
        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))

                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = signingConfigs.getByName("release")
        }

        getByName("debug") {
            isMinifyEnabled = false
            // ✅ NO suffix - uses same Firebase app as release
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")

    // ViewPager2
    implementation("androidx.viewpager2:viewpager2:1.0.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:32.7.1"))

    // Firebase dependencies
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // MapLibre
    implementation("org.maplibre.gl:android-sdk:13.3.0")
    implementation("org.maplibre.gl:android-plugin-annotation-v9:1.0.0")

    // RecyclerView + CardView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")

    // Play Services Location
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // SwipeRefreshLayout
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Shimmer
    implementation("com.facebook.shimmer:shimmer:0.5.0")
}