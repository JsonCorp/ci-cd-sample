plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kover)
}

// Room 이 만드는 스키마 JSON 을 저장소에 커밋한다. 이게 있어야 두 가지가 가능해진다.
//  1. MigrationTestHelper 가 "예전 버전 DB" 를 실제로 만들어 볼 수 있다.
//  2. 스키마를 바꾸면 diff 가 눈에 보인다 — 리뷰에서 마이그레이션 누락을 잡는 근거가 된다.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.example.cicdsample.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // MigrationTestHelper 는 스키마 JSON 을 '에셋'에서 읽는다.
    // 커밋해 둔 schemas/ 를 androidTest 에셋으로 그대로 실어 준다.
    sourceSets.getByName("androidTest") {
        assets.srcDir("$projectDir/schemas")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // :app 과 같은 기기 정의를 쓴다. 이름이 같아야 CI 에서 태스크명을 예측할 수 있다.
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
}

dependencies {
    // 구현이 인터페이스에 의존한다 — 화살표가 :data -> :domain 한 방향뿐이다.
    implementation(project(":domain"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // 마이그레이션 검증은 실제 SQLite 가 필요하므로 계측 테스트로만 돈다.
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

kover {
    currentProject {
        // debug 변형만 집계한다 → testDebugUnitTest 만 돌고 release 유닛테스트는 건드리지 않는다.
        createVariant("custom") {
            add("debug")
        }
    }
}
