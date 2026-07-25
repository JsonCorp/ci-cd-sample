pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CicdSample"

// 공식 3계층을 Gradle 모듈로 분리한다.
// :domain 은 순수 Kotlin(JVM) 모듈이라 Android SDK 없이 초 단위로 테스트가 끝난다.
include(":app")
include(":data")
include(":domain")
