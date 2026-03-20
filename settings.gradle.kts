plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "biddo"

include("biddo-domain")
include("biddo-infra")
include("biddo-api")