group = "app.joly0"

patches {
    // TODO: Update this section with your project details.
    about {
        name = "Joly0 Patches"
        description = "Playlist tools for YouTube"
        source = "git@github.com:Joly0/joly0-patches.git"
        author = "Joly0"
        contact = "na"
        website = "https://github.com/Joly0/joly0-patches"
        license = "GPLv3"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
