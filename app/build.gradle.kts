import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.activitytrace"
    compileSdk = 36

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "com.activitytrace"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
        debug {
            isDebuggable = true
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

    // Room schema JSONs are exported here and fed to the unit test (Robolectric)
    // apk-for-local-test via the variant assets. Robolectric does not serve test
    // source-set assets, and test resources are not on the unit-test classpath.
    sourceSets {
        getByName("debug").assets.srcDir("$projectDir/schemas")
        getByName("release").assets.srcDir("$projectDir/schemas")
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/assets/dexopt/*"
        }
    }
}

tasks.register("verifyNoInternetPermissionInRelease") {
    group = "verification"
    description = "Fails if the merged release manifest declares android.permission.INTERNET"
    dependsOn("assembleRelease")

    doLast {
        val sdkDir = project.rootProject.file("local.properties").takeIf { it.exists() }
            ?.readLines()
            ?.firstOrNull { it.startsWith("sdk.dir=") }
            ?.substringAfter("=")
            ?.trim('"')
            ?: System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: error("ANDROID_HOME is not set and no local.properties sdk.dir was found")
        val aapt = File(sdkDir, "build-tools")
            .listFiles()?.filter { it.isDirectory }
            ?.map { File(it, "aapt") }
            ?.filter { it.exists() }
            ?.maxByOrNull { it.parentFile.name }
            ?: error("aapt not found under $sdkDir/build-tools")
        val apk = File(projectDir, "build/outputs/apk/release")
            .listFiles { f -> f.extension == "apk" }
            ?.firstOrNull()
            ?: error("no release APK found in app/build/outputs/apk/release")
        val output = ByteArrayOutputStream()
        exec {
            commandLine(aapt.absolutePath, "dump", "permissions", apk.absolutePath)
            standardOutput = output
        }
        if (output.toString().contains("android.permission.INTERNET")) {
            throw GradleException("release manifest declares android.permission.INTERNET — review whether the app needs network access")
        }
        logger.lifecycle("Release manifest OK: no android.permission.INTERNET")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

configurations.all {
    // sqlcipher-android pulls androidx.sqlite 2.6.2+ transitively, which is
    // built against Kotlin 2.x metadata and is incompatible with the pinned
    // Kotlin 1.9.22. Force androidx.sqlite to the Kotlin-1.9-compatible 2.4.0.
    resolutionStrategy {
        force(
            "androidx.sqlite:sqlite:2.4.0",
            "androidx.sqlite:sqlite-ktx:2.4.0",
        )
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite.ktx)
    implementation(libs.pdfbox.android)
    implementation(libs.documentfile)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.arch.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)

    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.work.testing)
}
