plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "cn.ggdoc.autoscroll"
    compileSdk = 36

    defaultConfig {
        applicationId = "cn.ggdoc.autoscroll"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "3.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 签名配置：优先读取环境变量（CI 注入），未配置则降级 debug 签名
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
            val keystorePwd = System.getenv("SIGNING_STORE_PASSWORD")
            val alias = System.getenv("SIGNING_KEY_ALIAS")
            val keyPwd = System.getenv("SIGNING_KEY_PASSWORD")
            if (!keystorePath.isNullOrBlank() && !keystorePwd.isNullOrBlank() &&
                !alias.isNullOrBlank() && !keyPwd.isNullOrBlank()
            ) {
                storeFile = file(keystorePath)
                storePassword = keystorePwd
                keyAlias = alias
                keyPassword = keyPwd
            }
        }
    }

    buildTypes {
        debug {
            // debug 也启用 minify 可以本地复现 R8 删除/重命名类导致的运行时崩溃，
            // 但会拖慢增量构建；这里保持关闭，仅在 release 严格校验。
            isMinifyEnabled = false
        }
        release {
            // 开启 R8 代码混淆 + 资源压缩：
            //  - 体积更小（约 -30%~50%）
            //  - 反编译后业务逻辑被混淆，无障碍自动化类应用尤其需要
            //  - 移除未使用代码，减少 65535 方法数压力
            isMinifyEnabled = true
            isShrinkResources = true
            // 保留 Kotlin 元数据，避免反射 / data class 序列化失效
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 环境变量齐全时使用正式签名，否则回退 debug 签名（保证 APK 可安装）
            val hasReleaseSigning = System.getenv("SIGNING_KEYSTORE_PATH") != null
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = false
    }
    // lint 配置：CI 跑 lintDebug 生成报告。
    // 首期保持非阻断（abortOnError=false）——项目已存在若干历史告警，
    // 先把报告接进来让问题可见，待清理后再改为 true 强制阻断。
    lint {
        abortOnError = false
        warningsAsErrors = false
        checkReleaseBuilds = false
        // 无障碍服务必然声明大量权限且面向所有 App，关掉相关噪音
        disable += setOf(
            "GoogleAppIndexingWarning",
            "PackageManagerGetSignatures",
            "UnusedResources"
        )
        // HTML 报告更直观，CI 上传 artifact 便于排查
        htmlReport = true
        xmlReport = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.recyclerview)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
