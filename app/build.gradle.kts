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
        release {
            isMinifyEnabled = false
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
        viewBinding = false
        buildConfig = false
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
