plugins {
  alias(libs.plugins.android.application)
}

android {
  namespace = "dev.anton.slovo"
  compileSdk = 37

  defaultConfig {
    applicationId = "dev.anton.slovo"
    minSdk = 24
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    viewBinding = true
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(project(":core-dict"))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.transition)
  implementation(libs.material)

  testImplementation(libs.junit)
}
