# Colaborar

Gracias por querer ayudar.

## Antes de empezar

Para un cambio grande, abre antes un [issue](https://github.com/trastealabs/tiempo/issues) y lo hablamos: así no se pierde tu trabajo. Los arreglos pequeños puedes mandarlos directamente.

## Compilar y comprobar

Necesitas el JDK que trae Android Studio. Antes de dar un cambio por bueno, tiene que pasar:

```
./gradlew :buildSrc:test lintDebug testDebugUnitTest
```

`./gradlew installDebug` instala «Tiempo dev» en el móvil o el emulador conectado.

## Reglas

- Los textos de la app, la documentación y los mensajes de commit, en español. El código y sus nombres, en inglés.
- En los tests, datos inventados: nunca ubicaciones reales ni llamadas a GitHub.
- No pegues claves de IA, claves de firma ni ubicaciones tuyas en issues, commits o capturas.
- Una dependencia nueva se pregunta antes en un issue.

## Licencia

Lo que aportes se publica con la misma licencia del proyecto, GPL-3.0 (ver [LICENSE](LICENSE)). El nombre «Tiempo», «Trastea Labs» y el icono no entran en la licencia.
