dependencies {
    implementation(project(":events"))
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-jdbc")
    implementation("org.slf4j:slf4j-api")
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}

tasks.test {
    inputs.property("telegramLiveTest", System.getenv("TELEGRAM_LIVE_TEST") ?: "false")
    inputs.property("groqLiveTest", System.getenv("GROQ_LIVE_TEST") ?: "false")
}
