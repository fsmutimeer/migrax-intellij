import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
  id("java")
  id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "org.migrax"
version = providers.gradleProperty("pluginVersion").get()

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(17)
  }
}

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  intellijPlatform {
    intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
    bundledPlugin("com.intellij.java")
  }
  testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
  pluginConfiguration {
    name = "Migrax"
    ideaVersion {
      sinceBuild = providers.gradleProperty("pluginSinceBuild")
      // No upper limit: newer IDE versions are checked by verifyPlugin instead.
      untilBuild = provider { null }
    }
  }
  pluginVerification {
    ides {
      if (providers.gradleProperty("verifyAll").isPresent) {
        // CI: every IDE release from the since-build on (several GB to download).
        recommended()
        // From 2025.3 on IntelliJ IDEA is one product (no separate Community build), which
        // recommended() does not list: the newest release of each line is named here. Raise
        // these when a new IDE line comes out (JetBrains release feed, product code IIU).
        for (version in listOf("2025.3.6.1", "2026.1.5", "2026.2.3")) {
          create(IntelliJPlatformType.IntellijIdea, version)
        }
      } else {
        // Locally one IDE: the full set downloads about 20 GB.
        ide("IC", providers.gradleProperty("platformVersion").get())
      }
    }
  }
}

tasks.withType<JavaCompile> {
  options.encoding = "UTF-8"
  options.release = 17
}
