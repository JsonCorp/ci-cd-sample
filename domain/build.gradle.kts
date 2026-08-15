plugins {
    alias(libs.plugins.kotlin.jvm)
    // 페이크 저장소를 :data / :app 테스트에서도 재사용하려고 테스트 픽스처를 공개한다.
    `java-test-fixtures`
    alias(libs.plugins.kover)
}

// 순수 Kotlin(JVM) 모듈 — Android 플러그인도, SDK 도 필요 없다.
// 그래서 :domain:test 는 에뮬레이터/AGP 없이 수 초에 끝난다.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // 도메인이 의존하는 유일한 외부 라이브러리. Android 도, DI 프레임워크도 모른다.
    implementation(libs.kotlinx.coroutines.core)

    // DI 는 생성자 주입만 쓰므로 어노테이션 표준(JSR-330)만 있으면 된다.
    implementation("javax.inject:javax.inject:1")

    // 픽스처는 도메인 모델/인터페이스만 알면 된다.
    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

kover {
    currentProject {
        sources {
            // testFixtures 는 Kover 의 기본 제외 대상이 아니다.
            // 빼지 않으면 FakeTaskRepository 가 프로덕션 코드로 잡혀 수치가 왜곡된다.
            excludedSourceSets.add("testFixtures")
        }
        // 순수 Kotlin(JVM) 모듈이 만드는 변형 이름은 "debug" 가 아니라 "jvm" 이다.
        createVariant("custom") {
            add("jvm")
        }
    }
}
