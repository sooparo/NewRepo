pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ScheduleCatcher"

// 순수 Kotlin 모듈(날짜 파서, iCalendar, CalDAV). Android SDK 없이도 테스트 가능.
include(":core")

// Android SDK 가 없는 환경에서는 `-PcoreOnly=true` 로 core 만 빌드/테스트할 수 있다.
if (providers.gradleProperty("coreOnly").orNull != "true") {
    include(":app")
}
