"""Lector del DNI detrás de una interfaz, para poder cambiar EasyOCR por otro motor."""
import io

# Zona del anverso donde está la foto del titular, como fracción de la imagen.
ZONA_FOTO = (0.03, 0.22, 0.34, 0.85)  # izquierda, arriba, derecha, abajo
CARACTERES_MRZ = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<'


def unir_por_filas(cajas):
    """[(caja, texto, confianza)] → un texto por fila, con los trozos de izquierda a derecha."""
    filas = []  # [centro_y, alto, [(x, texto)]]
    for caja, texto, *_ in sorted(cajas, key=lambda c: sum(p[1] for p in c[0]) / 4):
        ys = [p[1] for p in caja]
        centro, alto = sum(ys) / 4, max(ys) - min(ys)
        if filas and abs(centro - filas[-1][0]) < filas[-1][1] * 0.6:
            filas[-1][2].append((min(p[0] for p in caja), texto))
        else:
            filas.append([centro, alto, [(min(p[0] for p in caja), texto)]])
    return [''.join(t for _, t in sorted(fila[2])) for fila in filas]


class LectorDni:
    def leer_textos(self, imagen):
        """Devuelve las líneas de texto que se leen en la imagen (bytes)."""
        raise NotImplementedError

    def recortar_titular(self, imagen):
        """Devuelve la foto del titular en JPG (bytes), o None si no se pudo."""
        raise NotImplementedError


class LectorEasyOcr(LectorDni):
    _lector = None

    @classmethod
    def lector(cls):
        # Cargar el modelo tarda, así que se hace una vez y solo si hace falta.
        if cls._lector is None:
            import easyocr
            cls._lector = easyocr.Reader(['es'], gpu=False, verbose=False)
        return cls._lector

    def leer_textos(self, imagen):
        # EasyOCR parte una línea en varios trozos donde hay muchos '<' seguidos: se juntan por fila.
        cajas = self.lector().readtext(imagen, detail=1, allowlist=CARACTERES_MRZ)
        return unir_por_filas(cajas)

    def recortar_titular(self, imagen):
        try:
            from PIL import Image
            foto = Image.open(io.BytesIO(imagen)).convert('RGB')
            ancho, alto = foto.size
            izq, arr, der, aba = ZONA_FOTO
            recorte = foto.crop((int(ancho * izq), int(alto * arr), int(ancho * der), int(alto * aba)))
            salida = io.BytesIO()
            recorte.save(salida, 'JPEG')
            return salida.getvalue()
        except Exception:
            return None


def obtener_lector():
    return LectorEasyOcr()
