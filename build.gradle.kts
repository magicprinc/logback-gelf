plugins {
    `java-library`
    `maven-publish`
    signing
    pmd
    //checkstyle
    jacoco
    id("com.github.spotbugs") version "6.0.9"
}

group = "de.siegmar"
version = "9.0"

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all,-serial")
    options.compilerArgs.add("-parameters")
    options.release.set(17)
}

repositories {
    mavenCentral()
}

dependencies {
    api("ch.qos.logback:logback-classic:1.2.+!!")
    testImplementation("org.slf4j:slf4j-api:1.7.+!!")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.38.0")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.16.+")
    testImplementation("org.bouncycastle:bcpkix-jdk18on:1.78")
    testImplementation("org.wiremock:wiremock:3.4.2")
    testImplementation("org.awaitility:awaitility:4.2.1")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

pmd {
    isConsoleOutput = true
    ruleSets = emptyList()
    ruleSetFiles = files("${project.rootDir}/config/pmd/config.xml")
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    excludeFilter = file("${project.rootDir}/config/spotbugs/config.xml")
    reports.maybeCreate("xml").required = false
    reports.maybeCreate("html").required = true
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "logback-gelf"
            from(components["java"])

            pom {
                name = "Logback GELF"
                description = "Logback appender for sending GELF messages with zero additional dependencies."
                url = "https://github.com/osiegmar/logback-gelf"
                licenses {
                    license {
                        name = "GNU Lesser General Public License version 2.1"
                        url = "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt"
                    }
                }
                scm {
                    url = "https://github.com/osiegmar/logback-gelf"
                    connection = "scm:git:https://github.com/osiegmar/logback-gelf.git"
                }
                developers {
                    developer {
                        id = "osiegmar"
                        name = "Oliver Siegmar"
                        email = "oliver@siegmar.de"
                    }
                }
            }
        }
    }
//    repositories {
//        maven {
//            name = "ossrh"
//            credentials(PasswordCredentials::class)
//            url = if (version.toString().endsWith("SNAPSHOT")) {
//                uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
//            } else {
//                uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
//            }
//        }
//    }
}

//signing {
//    sign(publishing.publications["maven"])
//}