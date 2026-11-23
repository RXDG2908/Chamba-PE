"""Lector del DNI detrás de una interfaz, para poder cambiar EasyOCR por otro motor."""
import io

# Zona del anverso donde está la foto del titular, como fracción de la imagen.
ZONA_FOTO = (0.03, 0.22, 0.34, 0.85)  # izquierda, arriba, derecha, abajo
CARACTERES_MRZ = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<'


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
            cls._lector = easyocr.Reader(['es'], gpu=False)
        return cls._lector

    def leer_textos(self, imagen):
        return self.lector().readtext(imagen, detail=0, allowlist=CARACTERES_MRZ)

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
