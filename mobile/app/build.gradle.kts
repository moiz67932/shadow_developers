plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.nasconsports"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.nasconsports"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("competition") {
            val signingPath = providers.environmentVariable("NASCON_SIGNING_STORE").orNull
            if (signingPath != null) {
                storeFile = file(signingPath)
                storePassword = providers.environmentVariable("NASCON_SIGNING_PASSWORD").orNull
                keyAlias = "nascon"
                keyPassword = providers.environmentVariable("NASCON_SIGNING_PASSWORD").orNull
            }
        }
    }
    buildTypes {
        release {
            if (providers.environmentVariable("NASCON_SIGNING_STORE").isPresent) signingConfig = signingConfigs.getByName("competition")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}