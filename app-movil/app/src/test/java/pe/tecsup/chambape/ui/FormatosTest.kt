package pe.tecsup.chambape.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Nombres raros en el avatar (auditoría «harden» del 26/11): nada de «�» ni marcas apiladas. */
class FormatosTest {
    @Test
    fun `nombre comun da nombre y apellido`() {
        assertEquals("RQ", inicialesDe("Rosa Quispe Lazo"))
        assertEquals("ÑÇ", inicialesDe("ñandú çağlar"))
    }

    @Test
    fun `emoji no se parte y se salta`() {
        assertEquals("RQ", inicialesDe("👷🏽‍♀️ Rosa 🔧🔧 Quispe ✨🏠"))
    }

    @Test
    fun `marcas combinadas se quitan pero la letra queda`() {
        assertEquals("ZÑ", inicialesDe("Z̷̢a̵̛l̶͝g̸̕o̷ Ñandú"))
        // «n» + tilde combinada (NFD) se lee como «Ñ».
        assertEquals("ÑP", inicialesDe("Ñandu Perez"))
    }

    @Test
    fun `otros alfabetos y un solo caracter`() {
        assertEquals("王李", inicialesDe("王小明 李"))
        assertEquals("ما", inicialesDe("محمد العبدالله"))
        assertEquals("AB", inicialesDe("A B"))
    }

    @Test
    fun `sin letras o vacio da interrogacion`() {
        assertEquals("?", inicialesDe(""))
        assertEquals("?", inicialesDe("   "))
        assertEquals("?", inicialesDe("🔧 123"))
    }

    @Test
    fun `nombre larguisimo sin espacios da una sola inicial`() {
        assertEquals("W", inicialesDe("Wolfeschlegelsteinhausenbergerdorff".repeat(3)))
    }
}
