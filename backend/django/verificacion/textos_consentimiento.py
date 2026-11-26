"""Texto de demostración: el PO debe aprobarlo antes de un uso real."""
FINALIDAD_DNI = 'verificacion_dni'
VERSION_DNI = 'demo-dni-v3'
# v1 y v2 se guardan tal cual se aceptaron (con la errata «DEMONSTRACIÓN»); no se reescriben.
TEXTO_DNI_V1 = (
    'DEMONSTRACIÓN: texto provisional pendiente de aprobación del Product Owner. '
    'No es un consentimiento legal definitivo.\n\n'
    'Acepto que Chamba-PE capture y procese las fotografías del anverso y reverso '
    'de mi DNI para leer sus datos, comprobar su coincidencia y permitir la revisión '
    'de mi identidad por administración. Incluye el recorte de la fotografía del '
    'titular y el almacenamiento privado de las imágenes para esa revisión.\n\n'
    'Esta aceptación no autoriza reconocimiento facial, comparación biométrica '
    'ni otros usos futuros. Puedo revocarla para impedir nuevos procesamientos. '
    'Revocar no elimina las fotografías ya guardadas: la solicitud de eliminación '
    'todavía no está disponible en esta demostración.'
)
TEXTO_DNI_V2 = TEXTO_DNI_V1.replace(
    'Revocar no elimina las fotografías ya guardadas: la solicitud de eliminación '
    'todavía no está disponible en esta demostración.',
    'Revocar no elimina las fotografías ya guardadas. Puedo solicitar por separado '
    'la eliminación de fotografías y datos derivados de la verificación del DNI. '
    'La solicitud conserva mi cuenta y el DNI registrado, y no se considera atendida '
    'hasta comprobar el borrado. Se mantiene un historial mínimo sin fotografías ni datos leídos.',
)
# v3 (25/11): corrige la errata «DEMONSTRACIÓN» → «DEMOSTRACIÓN». Las aceptaciones v2 quedan desactualizadas.
TEXTO_DNI = TEXTO_DNI_V2.replace('DEMONSTRACIÓN:', 'DEMOSTRACIÓN:', 1)

# HU-20: finalidad aparte. Aceptar el DNI no autoriza la comparación facial, ni al revés.
FINALIDAD_FACIAL = 'verificacion_facial'
VERSION_FACIAL = 'demo-facial-v1'
TEXTO_FACIAL = (
    'DEMOSTRACIÓN: texto provisional pendiente de aprobación del Product Owner. '
    'No es un consentimiento legal definitivo.\n\n'
    'Acepto que Chamba-PE tome una selfie en vivo con la cámara, compruebe que parpadeo '
    '(prueba de vida) y compare mi rostro con la foto del titular recortada de mi DNI, para '
    'confirmar que soy el titular del documento. Se guarda la selfie en un espacio privado, el '
    'porcentaje de similitud y la decisión con fecha y hora. Si el resultado queda en duda, un '
    'administrador ve las dos fotos lado a lado para decidir.\n\n'
    'La comparación no aprueba mi cuenta por sí sola: la decisión final es del administrador. '
    'No se usa para otros fines. Puedo revocar esta aceptación para impedir nuevas comparaciones '
    'y pedir que se borren mis selfies; se conservan solo el porcentaje, la decisión y la fecha.'
)
