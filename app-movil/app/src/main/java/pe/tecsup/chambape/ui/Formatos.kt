package pe.tecsup.chambape.ui

import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale

/**
 * Iniciales para el avatar: la primera letra real de las dos primeras palabras que tengan letras.
 *
 * Cuenta por grafemas (lo que la persona ve como un carácter), no por unidades UTF-16: con
 * `first()` un emoji con tono de piel quedaba partido («�»). Las palabras sin letras (emoji,
 * números) se saltan y las marcas combinadas apiladas se quitan («Z̷̢» → «Z»); «Ñ» y «Ç» se
 * conservan porque en NFC son un solo carácter. Sin ninguna letra devuelve «?».
 */
fun inicialesDe(nombre: String, locale: Locale = Locale.forLanguageTag("es-PE")): String {
    val letras = nombre.split(' ', '\t', '\n')
        .mapNotNull { primeraLetra(it) }
        .take(2)
        .joinToString("") { it.uppercase(locale) }
    return letras.ifEmpty { "?" }
}

private fun primeraLetra(palabra: String): String? {
    if (palabra.isBlank()) return null
    val texto = Normalizer.normalize(palabra, Normalizer.Form.NFC)
    val grafemas = BreakIterator.getCharacterInstance().apply { setText(texto) }
    var inicio = grafemas.first()
    var fin = grafemas.next()
    while (fin != BreakIterator.DONE) {
        val grafema = texto.substring(inicio, fin)
        if (Character.isLetter(grafema.codePointAt(0))) {
            // Base del grafema más lo que en NFC forme una sola letra; sin diacríticos combinados sueltos.
            return grafema.filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
                .ifEmpty { grafema }
        }
        inicio = fin
        fin = grafemas.next()
    }
    return null
}
