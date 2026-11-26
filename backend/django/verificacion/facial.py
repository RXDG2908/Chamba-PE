"""HU-20: comparación facial entre la selfie y la foto del titular del DNI.

El comparador va detrás de una interfaz, como el OCR de HU-19: las pruebas usan uno falso y
la app usa OpenCV (detector YuNet + reconocedor SFace, modelos ONNX de opencv_zoo). Los
modelos no se versionan: `python manage.py descargar_modelos_faciales` los baja y comprueba
su huella. Sin ellos el intento queda en revisión manual, nunca se aprueba a ciegas.
"""
from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path

from django.conf import settings

# Huellas de los modelos que se probaron (opencv_zoo, rama main, 25/11/2026).
MODELOS = {
    'detector': (
        'face_detection_yunet_2023mar.onnx',
        'https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/face_detection_yunet/'
        'face_detection_yunet_2023mar.onnx',
        '8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4',
    ),
    'reconocedor': (
        'face_recognition_sface_2021dec.onnx',
        'https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/face_recognition_sface/'
        'face_recognition_sface_2021dec.onnx',
        '0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79',
    ),
}

# Motivos (códigos estables; la web los traduce).
SIN_ROSTRO_SELFIE = 'sin_rostro_selfie'
VARIOS_ROSTROS_SELFIE = 'varios_rostros_selfie'
SIN_ROSTRO_DNI = 'sin_rostro_dni'
COMPARADOR_NO_DISPONIBLE = 'comparador_no_disponible'
SIMILITUD_BAJA = 'similitud_baja'
ZONA_GRIS = 'zona_gris'
DNI_EN_REVISION = 'dni_en_revision'


@dataclass
class Comparacion:
    similitud: float | None
    motivos: list = field(default_factory=list)


class ComparadorFacial:
    nombre = 'base'

    def comparar(self, foto_dni: bytes, selfie: bytes) -> Comparacion:
        raise NotImplementedError


def _imagen(contenido):
    import cv2
    import numpy as np
    return cv2.imdecode(np.frombuffer(contenido, dtype=np.uint8), cv2.IMREAD_COLOR)


class ComparadorOpenCV(ComparadorFacial):
    nombre = 'opencv-sface-2021dec'
    # Confianza mínima del detector para contar un rostro.
    CONFIANZA = 0.8

    def __init__(self, carpeta):
        import cv2
        self.cv2 = cv2
        self.detector = cv2.FaceDetectorYN.create(str(Path(carpeta) / MODELOS['detector'][0]), '', (320, 320),
                                                  self.CONFIANZA)
        self.reconocedor = cv2.FaceRecognizerSF.create(str(Path(carpeta) / MODELOS['reconocedor'][0]), '')

    def rostros(self, imagen):
        alto, ancho = imagen.shape[:2]
        # La foto del DNI es pequeña: se agranda para que YuNet la detecte.
        escala = 1.0
        if max(alto, ancho) < 320:
            escala = 320 / max(alto, ancho)
            imagen = self.cv2.resize(imagen, None, fx=escala, fy=escala, interpolation=self.cv2.INTER_CUBIC)
            alto, ancho = imagen.shape[:2]
        self.detector.setInputSize((ancho, alto))
        _, caras = self.detector.detect(imagen)
        return imagen, ([] if caras is None else list(caras))

    def comparar(self, foto_dni, selfie):
        img_dni, img_selfie = _imagen(foto_dni), _imagen(selfie)
        if img_selfie is None:
            return Comparacion(None, [SIN_ROSTRO_SELFIE])
        img_selfie, caras_selfie = self.rostros(img_selfie)
        if not caras_selfie:
            return Comparacion(None, [SIN_ROSTRO_SELFIE])
        if len(caras_selfie) > 1:
            return Comparacion(None, [VARIOS_ROSTROS_SELFIE])
        if img_dni is None:
            return Comparacion(None, [SIN_ROSTRO_DNI])
        img_dni, caras_dni = self.rostros(img_dni)
        if not caras_dni:
            return Comparacion(None, [SIN_ROSTRO_DNI])
        # En el recorte del DNI se usa el rostro más seguro.
        cara_dni = max(caras_dni, key=lambda c: c[-1])
        a = self.reconocedor.feature(self.reconocedor.alignCrop(img_dni, cara_dni))
        b = self.reconocedor.feature(self.reconocedor.alignCrop(img_selfie, caras_selfie[0]))
        return Comparacion(float(self.reconocedor.match(a, b, self.cv2.FaceRecognizerSF_FR_COSINE)))


class ComparadorNoDisponible(ComparadorFacial):
    nombre = 'no-disponible'

    def comparar(self, foto_dni, selfie):
        return Comparacion(None, [COMPARADOR_NO_DISPONIBLE])


def modelos_presentes(carpeta=None):
    carpeta = Path(carpeta or settings.MODELOS_FACIALES)
    return all((carpeta / nombre).is_file() for nombre, _, _ in MODELOS.values())


@lru_cache(maxsize=1)
def _opencv(carpeta):
    return ComparadorOpenCV(carpeta)


def obtener_comparador():
    carpeta = str(settings.MODELOS_FACIALES)
    if not modelos_presentes(carpeta):
        return ComparadorNoDisponible()
    try:
        return _opencv(carpeta)
    except Exception:
        return ComparadorNoDisponible()


def decidir(comparacion, intento_dni):
    """Verificado, revisión manual o rechazado, con sus motivos (T20.3).

    - Sin poder comparar (sin rostro, sin modelo): revisión manual, salvo que la selfie no
      tenga un único rostro, que se rechaza para que repita la foto.
    - similitud >= UMBRAL_VERIFICADO: verificado, si además el DNI salió validado (HU-19);
      con el DNI en revisión manual baja a revisión manual.
    - UMBRAL_RECHAZO <= similitud < UMBRAL_VERIFICADO: zona gris, revisión manual.
    - similitud < UMBRAL_RECHAZO: rechazado.
    """
    from .models import IntentoDni, IntentoFacial
    Decision = IntentoFacial.Decision
    motivos = list(comparacion.motivos)
    if comparacion.similitud is None:
        if SIN_ROSTRO_SELFIE in motivos or VARIOS_ROSTROS_SELFIE in motivos:
            return Decision.RECHAZADO, motivos
        return Decision.REVISION_MANUAL, motivos
    if comparacion.similitud < settings.UMBRAL_FACIAL_RECHAZO:
        return Decision.RECHAZADO, motivos + [SIMILITUD_BAJA]
    if comparacion.similitud < settings.UMBRAL_FACIAL_VERIFICADO:
        return Decision.REVISION_MANUAL, motivos + [ZONA_GRIS]
    if intento_dni.resultado != IntentoDni.Resultado.VALIDADO:
        return Decision.REVISION_MANUAL, motivos + [DNI_EN_REVISION]
    return Decision.VERIFICADO, motivos
