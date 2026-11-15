plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "ru.mexator.core.ui"
  compileSdk = 37

  defaultConfig {
    minSdk = 24
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  jvmToolchain(17)
}
