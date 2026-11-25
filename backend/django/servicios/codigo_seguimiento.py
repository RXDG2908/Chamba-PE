"""HU-11: el detalle de una solicitud solo lo ve su cliente o su prestador.

Un cliente sin cuenta (visitante) no tiene sesión que lo identifique: al crear la solicitud
recibe un código firmado con SECRET_KEY y lo envía como ?codigo= para seguirla. El código no se
guarda en la base y no se puede adivinar ni reutilizar para otra solicitud.
"""
from django.core import signing

_SAL = 'servicios.solicitud.seguimiento'


def codigo_de(solicitud_id):
    return signing.Signer(salt=_SAL).sign(str(solicitud_id)).rsplit(':', 1)[1]


def codigo_valido(solicitud_id, codigo):
    if not codigo or not isinstance(codigo, str):
        return False
    try:
        return signing.Signer(salt=_SAL).unsign(f'{solicitud_id}:{codigo}') == str(solicitud_id)
    except signing.BadSignature:
        return False


def puede_ver(solicitud, request):
    usuario = request.user
    if usuario and usuario.is_authenticated and usuario.pk in (solicitud.cliente_id, solicitud.prestador.usuario_id):
        return True
    return codigo_valido(solicitud.pk, request.query_params.get('codigo', ''))
