# Reglas extra de R8 solo para `perf` (la compilación de medir, que lleva la demo). 008, H10.

# DemoReceiver lee por reflexión el almacén del repositorio (`WeatherRepository.store`) para
# escribir la escena de demo donde la leen la app y el widget. Sin esta regla, R8 lo renombra.
-keepclassmembers class com.trastealabs.tiempo.refresh.WeatherRepository {
    *** store;
}
