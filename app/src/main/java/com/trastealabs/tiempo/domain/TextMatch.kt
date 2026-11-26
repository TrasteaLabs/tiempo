package com.trastealabs.tiempo.domain

import java.text.Normalizer

/*
 * Reglas de texto de la búsqueda de lugares (006, H2): sin tildes ni mayúsculas, por inicio de nombre o de palabra.
 * Todo letra a letra: la clave normalizada mide lo mismo que el nombre, así que las posiciones de la negrita valen
 * para el nombre original.
 */

/** Tabla de los caracteres latinos (hasta U+024F): minúscula y sin marcas diacríticas. */
private val LATIN: CharArray = CharArray(0x250) { i -> foldSlow(i.toChar()) }

private fun foldSlow(c: Char): Char {
    val lower = c.lowercaseChar()
    val base = Normalizer.normalize(lower.toString(), Normalizer.Form.NFD)[0]
    // Si la descomposición deja algo que no es letra (p. ej. un signo suelto), se queda la minúscula tal cual.
    return if (base.isLetterOrDigit() || base == lower) base else lower
}

private fun fold(c: Char): Char = if (c.code < LATIN.size) LATIN[c.code] else foldSlow(c)

/** Minúsculas y sin marcas diacríticas, letra a letra: «Córdoba» → «cordoba», «A Coruña» → «a coruna». */
fun normalize(s: String): String {
    var i = 0
    while (i < s.length && fold(s[i]) == s[i]) i++
    if (i == s.length) return s   // ya normalizado: misma instancia, sin gastar memoria
    val out = CharArray(s.length)
    for (j in s.indices) out[j] = if (j < i) s[j] else fold(s[j])
    return String(out)
}

/** Lo escrito en el buscador: sin espacios en los extremos y normalizado como las claves. */
fun normalizeQuery(q: String): String = normalize(q.trim())

/** Una palabra empieza tras un espacio, guion, apóstrofo, barra o paréntesis. */
private fun isSeparator(c: Char): Boolean =
    c == ' ' || c == '-' || c == '\'' || c == '’' || c == '/' || c == '(' || c == ')'

/**
 * Primera posición de [key] donde empieza [query] al inicio del nombre o de una palabra; -1 si no hay.
 * [key] y [query] ya normalizados.
 */
fun matchStart(key: String, query: String, from: Int = 0): Int {
    if (query.isEmpty() || query.length > key.length) return -1
    var i = from
    val last = key.length - query.length
    while (i <= last) {
        if ((i == 0 || isSeparator(key[i - 1])) && key.startsWith(query, i)) return i
        i++
    }
    return -1
}

fun matches(key: String, query: String): Boolean = matchStart(key, query) >= 0

/** Tramos de [name] que coinciden con lo escrito, para la negrita (cada inicio de palabra que encaja). */
fun boldRanges(name: String, query: String): List<IntRange> {
    val q = normalizeQuery(query)
    if (q.isEmpty()) return emptyList()
    val key = normalize(name)
    val out = mutableListOf<IntRange>()
    var at = matchStart(key, q)
    while (at >= 0) {
        out += at until at + q.length
        at = matchStart(key, q, at + q.length)
    }
    return out
}
