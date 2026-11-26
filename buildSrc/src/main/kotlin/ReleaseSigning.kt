import java.io.File
import java.util.Properties

/** Datos para firmar la release, vengan de donde vengan. */
data class SigningData(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)

/**
 * De dónde sale la firma de la release (021). Primero las cuatro variables `RELEASE_*` (así firma GitHub Actions,
 * con la clave que sube `tools/subir-secretos.ps1`); si no están todas, `keystore.properties` de la carpeta de la
 * clave en el PC. Si tampoco, null y un aviso: la release sale sin firmar. Nombres genéricos a propósito: valen para
 * otras apps.
 */
object ReleaseSigning {
    val ENV_NAMES = listOf("RELEASE_KEYSTORE_FILE", "RELEASE_KEYSTORE_PASSWORD", "RELEASE_KEY_ALIAS", "RELEASE_KEY_PASSWORD")
    private val PROPERTY_NAMES = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    private const val UNSIGNED = "la release sale sin firmar"

    /** Devuelve la firma y, si algo está a medias, un aviso para el log (sin valores). */
    fun resolve(env: (String) -> String?, propertiesFile: File): Pair<SigningData?, String?> {
        val fromEnv = ENV_NAMES.map { env(it)?.takeIf(String::isNotBlank) }
        if (fromEnv.all { it != null }) {
            val (file, storePass, alias, keyPass) = fromEnv.map { it!! }
            return SigningData(File(file), storePass, alias, keyPass) to null
        }
        val envWarning = if (fromEnv.any { it != null }) {
            "faltan variables de firma (${ENV_NAMES.filterIndexed { i, _ -> fromEnv[i] == null }.joinToString()})"
        } else {
            null
        }
        if (!propertiesFile.isFile) {
            return null to listOfNotNull(envWarning, "ni variables RELEASE_* ni keystore.properties: $UNSIGNED").joinToString("; ")
        }
        val props = Properties().apply { propertiesFile.inputStream().use { load(it) } }
        val missing = PROPERTY_NAMES.filter { props.getProperty(it).isNullOrBlank() }
        if (missing.isNotEmpty()) {
            return null to listOfNotNull(envWarning, "keystore.properties incompleto (falta ${missing.joinToString()}): $UNSIGNED").joinToString("; ")
        }
        // storeFile puede ser absoluto o relativo a la carpeta de keystore.properties.
        val store = propertiesFile.parentFile.resolve(props.getProperty("storeFile"))
        return SigningData(store, props.getProperty("storePassword"), props.getProperty("keyAlias"), props.getProperty("keyPassword")) to envWarning
    }
}
