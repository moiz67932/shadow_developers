import java.net.NetworkInterface
import java.net.Inet4Address

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.nasconsports"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.nasconsports"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("competition") {
            val signingPath = providers.environmentVariable("NASCON_SIGNING_STORE").orNull
            if (signingPath != null) {
                storeFile = file(signingPath)
                storePassword = providers.environmentVariable("NASCON_SIGNING_PASSWORD").orNull
                keyAlias = "nascon"
                keyPassword = providers.environmentVariable("NASCON_SIGNING_PASSWORD").orNull
            }
        }
    }
    buildTypes {
        release {
            if (providers.environmentVariable("NASCON_SIGNING_STORE").isPresent) signingConfig = signingConfigs.getByName("competition")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("com.google.zxing:core:3.5.3")
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

// Allow HTTP only for loopback/emulator and the explicitly configured local laptop hosts.
// Override discovery with -PnasconDevHosts=192.168.1.42,10.0.0.2 when building on another host.
val localHosts = providers.gradleProperty("nasconDevHosts").orElse(providers.provider {
    NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
        .filter { it is Inet4Address && it.isSiteLocalAddress }.map { it.hostAddress }.joinToString(",")
}).get().split(",").filter { it.matches(Regex("[a-zA-Z0-9.:-]+")) }
val securityDirectory = layout.buildDirectory.dir("generated/localNetworkResources")
abstract class GenerateNetworkSecurity : DefaultTask() {
    @get:Input abstract val hosts: ListProperty<String>
    @get:OutputDirectory abstract val destination: DirectoryProperty
    @TaskAction fun generate() {
        val xml = destination.file("xml/network_security_config.xml").get().asFile
        xml.parentFile.mkdirs()
        xml.writeText("<network-security-config><base-config cleartextTrafficPermitted=\"false\"><trust-anchors><certificates src=\"system\" /></trust-anchors></base-config><domain-config cleartextTrafficPermitted=\"true\">" + hosts.get().joinToString("") { "<domain>$it</domain>" } + "</domain-config></network-security-config>")
    }
}
val generateLocalNetworkSecurity = tasks.register<GenerateNetworkSecurity>("generateLocalNetworkSecurity") {
    hosts.set((listOf("127.0.0.1", "localhost", "10.0.2.2") + localHosts).distinct())
    destination.set(securityDirectory)
}
android.sourceSets.getByName("main").res.directories.add(securityDirectory.get().asFile.absolutePath)
tasks.named("preBuild").configure { dependsOn(generateLocalNetworkSecurity) }
