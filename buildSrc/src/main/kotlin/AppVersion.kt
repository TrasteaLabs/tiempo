import java.util.Properties

/**
 * Versión de la app (021). `versionName` vive en `version.properties` de la raíz; `versionCode` es el número de
 * commits, salvo que se pase `-PversionCode=N` o la variable `VERSION_CODE` (así lo fija GitHub Actions).
 * Sin nada de eso (sin git, una copia en zip), 1.
 */
object AppVersion {
    const val FALLBACK_CODE = 1

    /**
     * El primero que venga de los tres, en orden; `gitCount` solo se llama si los dos primeros están vacíos. Un valor
     * pedido a mano (propiedad o variable) que no sea un entero positivo falla: no se publica con otro número.
     */
    fun versionCode(property: String?, env: String?, gitCount: () -> String?): Int =
        explicit(property, "-PversionCode") ?: explicit(env, "VERSION_CODE")
            ?: parse(runCatching(gitCount).getOrNull()) ?: FALLBACK_CODE

    private fun explicit(value: String?, name: String): Int? {
        if (value.isNullOrBlank()) return null
        return requireNotNull(parse(value)) { "$name tiene que ser un entero positivo (hay «${value.trim()}»)" }
    }

    /** `versionName` de `version.properties`; sin fichero o sin la clave, falla: no se publica una versión sin nombre. */
    fun versionName(propertiesText: String?): String {
        val props = Properties().apply { propertiesText?.reader()?.use { load(it) } }
        val name = props.getProperty("versionName")?.trim().orEmpty()
        require(SEMVER.matches(name)) { "version.properties: versionName tiene que ser X.Y.Z (hay «$name»)" }
        return name
    }

    /** Nombre del APK de release: el actualizador lee el `versionCode` de aquí. */
    fun apkName(appSlug: String, versionName: String, versionCode: Int) = "$appSlug-$versionName-$versionCode.apk"

    private fun parse(value: String?): Int? = value?.trim()?.toIntOrNull()?.takeIf { it > 0 }

    private val SEMVER = Regex("""\d+\.\d+\.\d+""")
}
