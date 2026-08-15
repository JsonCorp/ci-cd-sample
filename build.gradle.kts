plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    // 커버리지 집계는 루트가 맡는다 — 여기서만 apply false 가 아니다.
    alias(libs.plugins.kover)
}

// 리포트에 넣을 모듈. 각 모듈이 만든 "custom" 리포트 변형을 루트로 모은다.
dependencies {
    kover(project(":app"))
    kover(project(":data"))
    kover(project(":domain"))
}

kover {
    currentProject {
        // 루트에는 소스가 없다. 데이터는 위 kover(project(...)) 의존성에서 들어오므로 본문은 비운다.
        // 변형 이름을 "debug" 로 두면 Android 모듈이 자동 생성하는 변형과 충돌한다.
        createVariant("custom") {
        }
    }

    reports {
        filters {
            excludes {
                // 패턴은 FQCN 전체에 매칭된다 — 앞에 "*." 를 빼면 아무것도 안 걸린다.
                classes(
                    // Android 생성물
                    "*.R", "*.R$*", "*.BuildConfig",
                    "*.Manifest", "*.Manifest$*",

                    // Hilt / Dagger 생성물
                    "*.Hilt_*",
                    "*.Dagger*",
                    "*_HiltModules", "*_HiltModules$*",
                    "*_Factory", "*_Factory$*",
                    "*_MembersInjector",
                    "*_GeneratedInjector",
                    "*_ComponentTreeDeps",

                    // Compose 컴파일러 생성물
                    "*.ComposableSingletons$*",
                    "*.LiveLiterals$*",
                )
                packages(
                    "hilt_aggregated_deps",
                    "dagger.hilt.internal.aggregatedroot",
                )
            }
        }
    }
}

// androidGeneratedClasses() 는 쓰지 않는다 — 내부적으로 "*Activity", "*Fragment" 까지 제외해
// 직접 짠 MainActivity 가 조용히 커버리지에서 빠진다.
