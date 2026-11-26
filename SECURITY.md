# Seguridad

## Cómo avisar de un fallo

Si encuentras un fallo de seguridad, no lo publiques en un issue. Avísanos en privado con los avisos de seguridad de GitHub: en el repositorio, pestaña **Security** › **Report a vulnerability**. Si no tienes cuenta de GitHub, escribe a hola@trastealabs.com.

Cuenta qué has visto, cómo repetirlo y en qué versión (Ajustes › Acerca de). No incluyas claves de IA ni ubicaciones reales.

## Qué versión se cuida

Solo la última versión publicada en las [Releases](https://github.com/trastealabs/tiempo/releases).

## Qué comprueba el actualizador

Antes de instalar una versión nueva, la app comprueba:

- que el SHA-256 del fichero bajado coincide con el que da GitHub;
- que el paquete es el de Tiempo;
- que está firmado con el mismo certificado que la app instalada.

Si algo no cuadra, borra el fichero y no instala nada.
