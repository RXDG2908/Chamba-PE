"""Lectura de la MRZ del reverso del DNI (formato TD1 del estándar ICAO 9303)."""
import re
from dataclasses import dataclass
from datetime import date

PESOS = (7, 3, 1)
# Lo que el OCR suele confundir cuando se esperaba un número.
LETRA_A_NUMERO = {'O': '0', 'I': '1', 'Z': '2', 'S': '5', 'B': '8'}


class ErrorMrz(Exception):
    """La MRZ no se pudo leer o no pasa sus dígitos de control."""

    def __init__(self, motivo):
        super().__init__(motivo)
        self.motivo = motivo


@dataclass
class Lectura:
    dni: str
    apellidos: str
    nombres: str
    fecha_nacimiento: date
    fecha_vencimiento: date
    mrz_valida: bool = True


def valor(caracter):
    if caracter.isdigit():
        return int(caracter)
    if 'A' <= caracter <= 'Z':
        return ord(caracter) - ord('A') + 10
    return 0  # el relleno '<'


def digito_control(campo):
    """Suma de cada valor por el peso 7, 3, 1 repetido, módulo 10."""
    return sum(valor(c) * PESOS[i % 3] for i, c in enumerate(campo)) % 10


def limpiar_linea(texto):
    for parecido in '«‹〈':
        texto = texto.replace(parecido, '<')
    return texto.upper().replace(' ', '')


def completar(linea):
    """Repone los '<' de relleno que el OCR pierde al partir la fila (la línea debe medir 30)."""
    if len(linea) >= 30 or len(linea) < 20:
        return linea
    if linea[:2] in ('ID', 'I<'):
        return linea.ljust(30, '<')
    if re.match(r'^[0-9OISZB]{7}[MFX<][0-9OISZB]{7}[A-Z]{3}', linea):
        return linea[:18] + '<' * (30 - len(linea)) + linea[18:]  # el relleno va antes del último dígito
    if '<<' in linea:
        return linea.ljust(30, '<')
    return linea


def buscar_lineas(textos):
    """Devuelve las 3 líneas de 30 caracteres de la MRZ, o falla si no están."""
    lineas = [completar(limpiar_linea(t)) for t in textos]
    lineas = [t for t in lineas if len(t) == 30]
    for i, linea in enumerate(lineas):
        if linea[:2] in ('ID', 'I<') and linea[2:5] == 'PER' and len(lineas) - i >= 3:
            return lineas[i:i + 3]
    raise ErrorMrz('mrz_ilegible')


def a_numeros(texto):
    return ''.join(LETRA_A_NUMERO.get(c, c) for c in texto)


def leer_fecha(texto, es_nacimiento):
    """AAMMDD → fecha. El nacimiento mayor al año actual es del siglo pasado."""
    if not texto.isdigit():
        raise ErrorMrz('mrz_invalida')
    anio = int(texto[:2])
    if es_nacimiento:
        anio += 1900 if anio > date.today().year % 100 else 2000
    else:
        anio += 2000
    try:
        return date(anio, int(texto[2:4]), int(texto[4:6]))
    except ValueError:
        raise ErrorMrz('mrz_invalida')


def interpretar(textos):
    """Lee la MRZ, comprueba todos sus dígitos de control y devuelve la Lectura."""
    l1, l2, l3 = buscar_lineas(textos)
    # Los campos numéricos se corrigen antes de comprobar sus dígitos de control.
    l1 = l1[:5] + a_numeros(l1[5:15]) + l1[15:]
    l2 = a_numeros(l2[0:7]) + l2[7] + a_numeros(l2[8:15]) + l2[15:29] + a_numeros(l2[29])

    numero_doc = l1[5:14]
    control_doc = l1[14]
    nacimiento, control_nac = l2[0:6], l2[6]
    vencimiento, control_ven = l2[8:14], l2[14]
    control_total = l2[29]

    controles = [
        (numero_doc, control_doc),
        (nacimiento, control_nac),
        (vencimiento, control_ven),
        (l1[5:30] + l2[0:7] + l2[8:15] + l2[18:29], control_total),
    ]
    for campo, control in controles:
        if not control.isdigit() or digito_control(campo) != int(control):
            raise ErrorMrz('mrz_invalida')

    dni = a_numeros(numero_doc.replace('<', ''))
    if len(dni) != 8 or not dni.isdigit():
        raise ErrorMrz('mrz_invalida')

    apellidos, _, nombres = l3.partition('<<')
    nombres = nombres.strip('<').replace('<', ' ')
    apellidos = apellidos.replace('<', ' ')
    if not apellidos or not nombres:
        raise ErrorMrz('mrz_invalida')

    return Lectura(
        dni=dni,
        # La Ñ viaja como NXX en la MRZ del DNI peruano.
        apellidos=apellidos.replace('NXX', 'Ñ'),
        nombres=nombres.replace('NXX', 'Ñ'),
        fecha_nacimiento=leer_fecha(nacimiento, True),
        fecha_vencimiento=leer_fecha(vencimiento, False),
    )
