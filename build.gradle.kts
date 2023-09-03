plugins {
    alias(libs.plugins.blossom)
    alias(libs.plugins.shadowJar)
    java
}

var displayName = "Bastom"

group = "dev.andus.bastom"
version = "1.0.0"

dependencies {
    implementation(libs.minestom)
}

tasks {
    blossom {
        replaceToken("&Name", displayName)
        replaceToken("&version", version)
    }

    processResources {
        expand(
            mapOf(
                "Name" to displayName,
                "version" to version
            )
        )
    }

    shadowJar {
        manifest {
            attributes("Main-Class" to "dev.andus.bastom.Server")
        }
        archiveBaseName.set(displayName)
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())
        mergeServiceFiles()
    }

    test {
        useJUnitPlatform()
    }

    build {
        dependsOn(shadowJar)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}