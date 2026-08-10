pluginManagement {
    repositories {
        // 官方 Gradle 插件门户（GitHub 托管 Runner 直连最稳，放最前）
        gradlePluginPortal()
        // 阿里云镜像（境内外均可达，代理插件标记与 mavenCentral/google 构件）
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // 腾讯云镜像（聚合 gradle-plugin / maven-central / google）兜底
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/gradle-plugins/") }
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 优先官方源（GitHub Runner 直连），再阿里/腾讯镜像兜底
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
    }
}

rootProject.name = "AutoScroll"
include(":app")
