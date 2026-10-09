plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.stockfortune.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.stockfortune.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"
        resourceConfigurations += listOf("zh", "zh-rCN")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 签名材料不随交付树分发：keystore 与口令存放在用户目录
    // ~/.stockfortune-keystore/（口令仅经环境变量注入，见 tools/build_all.sh）。
    // 可用 -PsfKeystoreDir=... 覆盖目录。
    //
    // 口令缺失时不建这个 config：create(name) { } 会立即执行配置块，若在配置期 error()
    // 则 test/lint/help 在任何无口令环境（CI、clone 后首次）全都跑不起来。release
    // 缺口令时改为产出 app-release-unsigned.apk，由 build_all.sh 第 [7]/[8] 步的 cp 与
    // apksigner verify 明确失败，不会静默产出"已签名的正式包"。
    val sfStorePassword = System.getenv("SF_STORE_PASSWORD")
    val sfKeyPassword = System.getenv("SF_KEY_PASSWORD")
    if (sfStorePassword != null && sfKeyPassword != null) {
        signingConfigs {
            create("localRelease") {
                val ksDir = providers.gradleProperty("sfKeystoreDir").orNull
                    ?: File(System.getProperty("user.home"), ".stockfortune-keystore").absolutePath
                storeFile = File(ksDir, "sf-release.jks")
                storePassword = sfStorePassword
                keyAlias = System.getenv("SF_KEY_ALIAS") ?: "sfrelease"
                keyPassword = sfKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("localRelease")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
    androidResources {
        // 预置库必须原样存放，禁止 aapt 压缩 assets 中的 .db（否则 Room 打开失败）
        noCompress += "db"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.useJUnit()
                // Robolectric 离线模式：android-all jar 由 tools/fetch_robolectric.sh 预取，
                // 避免测试 JVM 自己去拉 144MB 的镜像 jar（网络策略差异会导致卡死）。
                it.systemProperty("robolectric.offline", "true")
                it.systemProperty(
                    "robolectric.dependency.dir",
                    rootProject.file("build/robolectric-deps").absolutePath,
                )
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.espresso:espresso-core:3.6.1")
    // createComposeRule() 需要一个空的 ComponentActivity 宿主；只进 debug 包
    // （副作用：debug APK 会带 android.permission.DUMP，release 包不含该权限）
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
