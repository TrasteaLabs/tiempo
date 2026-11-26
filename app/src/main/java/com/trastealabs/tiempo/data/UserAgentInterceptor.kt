package com.trastealabs.tiempo.data

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 021: el mismo User-Agent en todas las llamadas (`Tiempo/1.0.0 (+https://github.com/trastealabs/tiempo)`), sin nada
 * del aparato. Open-Meteo limita «por IP y por aplicación» y puede cortar a una app que no se identifica; GitHub pide
 * uno a todo el que llama a su API.
 */
class UserAgentInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
}
