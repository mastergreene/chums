plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23"
}

android {
    namespace = "club.chums.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "club.chums.ogandroid"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Package the upstream artwork directly; keep one canonical copy in the repository.
val originalArtwork by tasks.registering(Copy::class) {
    into(layout.buildDirectory.dir("generated/originalArtwork"))
    from(rootProject.file("../../docs/media/characters")) { into("previews") }
    from(rootProject.file("../Shared/Characters.xcassets")) {
        include("avatar-*/avatar-*.png")
        eachFile { path = "avatars/$name" }
        includeEmptyDirs = false
    }
}
android.sourceSets.getByName("main").assets.srcDir(originalArtwork)
tasks.named("preBuild").configure { dependsOn(originalArtwork) }
val originalIcon by tasks.registering(Copy::class) {
    from(rootProject.file("../ChumsPhone/Assets.xcassets/AppIcon.appiconset/icon-1024.png"))
    into(layout.buildDirectory.dir("generated/originalResources/drawable"))
    rename { "chums_original_icon.png" }
}
android.sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/originalResources"))
tasks.named("preBuild").configure { dependsOn(originalIcon) }

dependencies {
    testImplementation("junit:junit:4.13.2")
    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    // OkHttp & Serialization
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
