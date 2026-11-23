"""Texto de demostración: el PO debe aprobarlo antes de un uso real."""
FINALIDAD_DNI = 'verificacion_dni'
VERSION_DNI = 'demo-dni-v2'
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
TEXTO_DNI = TEXTO_DNI_V1.replace(
    'Revocar no elimina las fotografías ya guardadas: la solicitud de eliminación '
    'todavía no está disponible en esta demostración.',
    'Revocar no elimina las fotografías ya guardadas. Puedo solicitar por separado '
    'la eliminación de fotografías y datos derivados de la verificación del DNI. '
    'La solicitud conserva mi cuenta y el DNI registrado, y no se considera atendida '
    'hasta comprobar el borrado. Se mantiene un historial mínimo sin fotografías ni datos leídos.',
)
