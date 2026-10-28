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
      val verifyIde = providers.gradleProperty("verifyIde")
      if (verifyIde.isPresent) {
        // CI: one IDE per job, as -PverifyIde=IntellijIdeaCommunity:2024.2.6. All of them in one
        // job fill the runner's disk. The list of IDEs is the matrix in .github/workflows/ci.yml.
        val (type, version) = verifyIde.get().split(":", limit = 2)
        create(IntelliJPlatformType.valueOf(type), version)
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
