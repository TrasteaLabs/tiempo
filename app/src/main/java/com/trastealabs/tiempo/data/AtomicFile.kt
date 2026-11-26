package com.trastealabs.tiempo.data

import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Escribe [text] en [file] sin dejarlo a medias: primero en `<nombre>.tmp` y luego se renombra encima (017, H7).
 *
 * En Windows (las pruebas en el PC) el renombrado falla con `AccessDeniedException` mientras otro hilo tiene el destino
 * abierto para leerlo; dura milisegundos, así que se reintenta unas veces antes de rendirse. En Android no pasa nunca.
 */
internal object AtomicFile {
    private const val ATTEMPTS = 20
    private const val WAIT_MS = 10L

    fun write(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            tmp.writeText(text)
            move(tmp, file)
        } catch (e: IOException) {
            tmp.delete()
            throw e
        }
    }

    private fun move(from: File, to: File) {
        var attempt = 1
        while (true) {
            try {
                Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                return
            } catch (e: AccessDeniedException) {
                if (attempt++ >= ATTEMPTS) throw e
                Thread.sleep(WAIT_MS)
            }
        }
    }
}
