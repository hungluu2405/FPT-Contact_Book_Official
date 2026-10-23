import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// Release signing key, kept out of git (see android/key.properties.example).
val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val signingKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")

// LAN demo APK only: -PdemoCleartextHost=<private IPv4 of the backend machine>
// lets the release build call that single host over plain HTTP.
val demoCleartextHost = (findProperty("demoCleartextHost") as String?)?.trim().orEmpty()

fun isPrivateIpv4(host: String): Boolean {
    val parts = host.split(".")
    if (parts.size != 4 || parts.any { it.isEmpty() || it.length > 3 || !it.all(Char::isDigit) }) {
        return false
    }
    val octets = parts.map { it.toInt() }
    if (octets.any { it > 255 }) {
        return false
    }
    return octets[0] == 10 ||
        (octets[0] == 172 && octets[1] in 16..31) ||
        (octets[0] == 192 && octets[1] == 168)
}

if (demoCleartextHost.isNotEmpty() && !isPrivateIpv4(demoCleartextHost)) {
    throw GradleException("demoCleartextHost must be a private IPv4 address (10.x, 172.16-31.x, 192.168.x)")
}

// The demo network config overrides src/main/res/xml/network_security_config.xml
// for the release build type only.
val demoNetworkConfigRes = layout.buildDirectory.dir("generated/demoNetworkConfig/res").get().asFile
if (demoCleartextHost.isNotEmpty()) {
    val xmlDir = File(demoNetworkConfigRes, "xml").apply { mkdirs() }
    File(xmlDir, "network_security_config.xml").writeText(
        """
        <?xml version="1.0" encoding="utf-8"?>
        <!-- Generated for the LAN demo APK. Do not use for a real deployment. -->
        <network-security-config>
            <base-config cleartextTrafficPermitted="false" />
            <domain-config cleartextTrafficPermitted="true">
                <domain includeSubdomains="false">$demoCleartextHost</domain>
            </domain-config>
        </network-security-config>
        """.trimIndent() + "\n",
    )
}

// Dart defines passed by `flutter build`, as KEY=VALUE.
fun dartDefines(): Map<String, String> =
    (findProperty("dart-defines") as String?).orEmpty()
        .split(",")
        .filter { it.isNotEmpty() }
        .map { String(Base64.getDecoder().decode(it), Charsets.UTF_8) }
        .filter { it.contains("=") }
        .associate { it.substringBefore("=") to it.substringAfter("=") }

// Fails the release build early instead of producing an APK that cannot
// start or is signed with the debug key.
fun checkReleaseBuild() {
    val defines = dartDefines()
    val baseUrl = defines["API_BASE_URL"].orEmpty()
    if (demoCleartextHost.isEmpty()) {
        if (!baseUrl.startsWith("https://")) {
            throw GradleException("Release builds need --dart-define=API_BASE_URL=https://...")
        }
        if (defines.containsKey("DEMO_CLEARTEXT_HOST")) {
            throw GradleException("DEMO_CLEARTEXT_HOST is only allowed together with -PdemoCleartextHost")
        }
    } else {
        if (defines["DEMO_CLEARTEXT_HOST"] != demoCleartextHost) {
            throw GradleException("--dart-define=DEMO_CLEARTEXT_HOST must equal -PdemoCleartextHost")
        }
        val demoUrl = Regex("^http://${Regex.escape(demoCleartextHost)}(:\\d+)?(/.*)?$")
        if (!baseUrl.startsWith("https://") && !demoUrl.matches(baseUrl)) {
            throw GradleException("API_BASE_URL must point to the demo host $demoCleartextHost")
        }
    }
    if (!keystorePropertiesFile.exists()) {
        throw GradleException(
            "Missing android/key.properties: create a release keystore first (docs/INSTALL_MOBILE.md)",
        )
    }
    val missing = signingKeys.filter { keystoreProperties.getProperty(it).isNullOrBlank() }
    if (missing.isNotEmpty()) {
        throw GradleException("android/key.properties is missing: ${missing.joinToString()}")
    }
}

android {
    namespace = "vn.edu.fpt.contactbook"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "vn.edu.fpt.contactbook"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keystoreProperties.getProperty("storeFile")?.let { storeFile = rootProject.file(it) }
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            if (demoCleartextHost.isNotEmpty()) {
                versionNameSuffix = "-demo-lan"
            }
        }
    }

    sourceSets {
        getByName("release") {
            if (demoCleartextHost.isNotEmpty()) {
                res.srcDir(demoNetworkConfigRes)
            }
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        doFirst { checkReleaseBuild() }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
