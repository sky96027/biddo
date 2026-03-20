plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":biddo-domain"))
    implementation(project(":biddo-infra"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}