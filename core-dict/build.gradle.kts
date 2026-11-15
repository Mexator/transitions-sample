plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "dev.anton.core.dict"
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
