// Lógica de compilación que se puede probar (021): versión, nombre del APK y firma por variables.
// Sin el plugin de Android a propósito, para que se copie tal cual a otras apps.
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
