plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ── 릴리스 서명 ────────────────────────────────────────────────────────────────
// 키스토어는 저장소에 두지 않는다. CI 는 base64 시크릿을 임시 파일로 풀어 아래 환경변수로 넘긴다.
// 넷 중 하나라도 비어 있으면 서명을 아예 구성하지 않는다 — 포크나 클린 클론에서도 빌드는 통과해야 한다.
val keystorePath: String? = System.getenv("ANDROID_KEYSTORE_PATH")
val keystorePassword: String? = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("ANDROID_KEY_PASSWORD")
val hasReleaseSigning = !keystorePath.isNullOrBlank() &&
    !keystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.example.cicdsample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.cicdsample"
        minSdk = 26
        targetSdk = 35

        // CI 에서는 실행 번호를 넣어 태그마다 versionCode 가 반드시 올라가게 한다.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
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

    lint {
        // 경고는 보고하되, 에러가 있으면 CI 를 세운다.
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
    }

    // ── 관리형 디바이스(Gradle Managed Devices) ────────────────────────────
    // AVD 생성·부팅·종료를 Gradle 이 직접 한다. CI 에서 별도 에뮬레이터 액션 없이
    //   ./gradlew :app:pixel6api30DebugAndroidTest
    // 한 줄로 Compose UI 테스트를 돌릴 수 있다.
    // aosp-atd 는 Google 이 테스트 전용으로 줄여 만든 이미지라 google_apis 보다 부팅이 빠르다
    // (Play 서비스가 빠져 있지만, 이 앱의 UI 테스트는 필요로 하지 않는다).
    testOptions {
        managedDevices {
            localDevices {
                create("pixel6api30") {
                    device = "Pixel 6"
                    apiLevel = 30
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // :domain 이 공개한 페이크 저장소를 그대로 가져다 쓴다.
    testImplementation(testFixtures(project(":domain")))

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
