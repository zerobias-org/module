plugins {
    id("zb.java-module")
}

// Gate-stamp inputs. zb.java-module inherits zb.typescript's defaults (api.yml, tsconfig.json,
// src/, test/) and does NOT add the Java tree — so a Java change would never invalidate a
// committed gate-stamp.json. Declare the real sources here (git-tracked files only are hashed;
// the generated schemas/ and structure-index/ trees are git-ignored and correctly excluded).
// DESIGN.md §11.5.
project.extra["sourceFiles"] = listOf(
    "api.yml", "tsconfig.json", "connectionProfile.yml", "runtimeConfig.yml",
    "Dockerfile", "startup.sh", "nginx.conf", "nginx-insecure.conf",
    "java/pom.xml", "java/codegen/pom.xml"
)
project.extra["sourceDirs"] = listOf("src", "java/src/main", "java/codegen/src/main")
project.extra["testDirs"]   = listOf("java/src/test", "java/codegen/src/test", "java/scripts")
