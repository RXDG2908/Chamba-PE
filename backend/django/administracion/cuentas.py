"""HU-13: API de administración para suspender, bloquear y reactivar cuentas.

GET  /api/administracion/cuentas/?q=texto&estado=activa|suspendida|bloqueada
GET  /api/administracion/cuentas/<id>/                 con el historial de sanciones
POST /api/administracion/cuentas/<id>/sancionar/        {"tipo": "suspension", "dias": 7, "motivo": "..."}
                                                         {"tipo": "bloqueo", "motivo": "..."}
POST /api/administracion/cuentas/<id>/reactivar/        {"motivo": "..."}

Solo administradores. Se gestionan cuentas de clientes y prestadores: las de administración
no se pueden sancionar desde aquí (así nadie deja al equipo sin acceso).
"""
from datetime import timedelta

from django.contrib.auth import get_user_model
from django.db.models import Prefetch, Q
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework import serializers, status
from rest_framework.response import Response
from rest_framework.views import APIView

from usuarios.models import Perfil, rol_de
from usuarios.permisos import EsAdministrador

from .models import SancionCuenta, sanciones_vigentes
from .sanciones import SancionAbierta, reactivar, sancionar

User = get_user_model()

MAXIMO_DIAS = 365


def es_administracion(usuario):
    return usuario.is_staff or usuario.is_superuser or rol_de(usuario) == Perfil.Rol.ADMINISTRADOR


def cuentas_gestionables():
    """Clientes y prestadores, con su sanción vigente ya cargada (sin una consulta por cuenta)."""
    return (
        User.objects.filter(is_staff=False, is_superuser=False)
        .exclude(perfil__rol=Perfil.Rol.ADMINISTRADOR)
        .select_related('perfil', 'prestador')
        .prefetch_related(Prefetch(
            'sanciones', queryset=sanciones_vigentes().select_related('creada_por', 'levantada_por'),
            to_attr='vigentes',
        ))
        .order_by('username')
    )


class SancionSerializer(serializers.ModelSerializer):
    tipo_display = serializers.CharField(source='get_tipo_display', read_only=True)
    creada_por = serializers.StringRelatedField()
    levantada_por = serializers.StringRelatedField()
    vigente = serializers.SerializerMethodField()

    class Meta:
        model = SancionCuenta
        fields = [
            'id', 'tipo', 'tipo_display', 'motivo', 'hasta', 'creada', 'creada_por',
            'levantada', 'levantada_por', 'motivo_levantamiento', 'vigente',
        ]

    def get_vigente(self, sancion):
        return sancion.vigente()


class CuentaSerializer(serializers.Serializer):
    def to_representation(self, usuario):
        vigentes = getattr(usuario, 'vigentes', None)
        if vigentes is None:
            vigentes = list(sanciones_vigentes().filter(usuario=usuario))
        sancion = vigentes[0] if vigentes else None
        prestador = getattr(usuario, 'prestador', None)
        if prestador is not None:
            nombre = f'{prestador.nombres} {prestador.apellidos}'
        else:
            nombre = f'{usuario.first_name} {usuario.last_name}'.strip()
        return {
            'id': usuario.pk,
            'usuario': usuario.username,
            'nombre': nombre,
            'correo': usuario.email,
            'rol': rol_de(usuario),
            'dni': prestador.dni if prestador is not None else None,
            'registrada': usuario.date_joined,
            'estado': 'activa' if sancion is None else ('bloqueada' if sancion.tipo == 'bloqueo' else 'suspendida'),
            'sancion': SancionSerializer(sancion).data if sancion else None,
        }


class SancionarSerializer(serializers.Serializer):
    tipo = serializers.ChoiceField(choices=SancionCuenta.Tipo.choices, error_messages={
        'invalid_choice': 'Elige suspensión o bloqueo.',
    })
    dias = serializers.IntegerField(required=False, min_value=1, max_value=MAXIMO_DIAS, error_messages={
        'min_value': 'La suspensión dura al menos 1 día.',
        'max_value': f'La suspensión dura como máximo {MAXIMO_DIAS} días; para más, bloquea la cuenta.',
        'invalid': 'Indica los días con un número entero.',
    })
    motivo = serializers.CharField(max_length=500, error_messages={
        'blank': 'Indica el motivo.', 'required': 'Indica el motivo.',
        'max_length': 'El motivo puede tener hasta 500 caracteres.',
    })

    def validate_motivo(self, motivo):
        motivo = motivo.strip()
        if len(motivo) < 10:
            raise serializers.ValidationError('Explica el motivo con al menos 10 caracteres.')
        return motivo

    def validate(self, datos):
        if datos['tipo'] == SancionCuenta.Tipo.SUSPENSION and 'dias' not in datos:
            raise serializers.ValidationError({'dias': 'Indica cuántos días dura la suspensión.'})
        if datos['tipo'] == SancionCuenta.Tipo.BLOQUEO and 'dias' in datos:
            raise serializers.ValidationError({'dias': 'El bloqueo no tiene fecha de fin.'})
        return datos


class ReactivarSerializer(serializers.Serializer):
    motivo = serializers.CharField(max_length=500, error_messages={
        'blank': 'Indica por qué se reactiva la cuenta.', 'required': 'Indica por qué se reactiva la cuenta.',
    })


class CuentasView(APIView):
    permission_classes = [EsAdministrador]

    def get(self, request):
        cuentas = cuentas_gestionables()
        texto = request.query_params.get('q', '').strip()
        if texto:
            cuentas = cuentas.filter(
                Q(username__icontains=texto) | Q(email__icontains=texto) | Q(first_name__icontains=texto)
                | Q(last_name__icontains=texto) | Q(prestador__nombres__icontains=texto)
                | Q(prestador__apellidos__icontains=texto) | Q(prestador__dni__icontains=texto)
            )
        datos = CuentaSerializer(cuentas, many=True).data
        estado = request.query_params.get('estado')
        if estado:
            if estado not in ('activa', 'suspendida', 'bloqueada'):
                return Response({'estado': 'Usa activa, suspendida o bloqueada.'}, status=status.HTTP_400_BAD_REQUEST)
            datos = [cuenta for cuenta in datos if cuenta['estado'] == estado]
        return Response(datos)


class DetalleCuentaView(APIView):
    permission_classes = [EsAdministrador]

    def get(self, request, pk):
        cuenta = get_object_or_404(cuentas_gestionables(), pk=pk)
        datos = CuentaSerializer(cuenta).data
        historial = cuenta.sanciones.select_related('creada_por', 'levantada_por')
        datos['historial'] = SancionSerializer(historial, many=True).data
        return Response(datos)


class SancionarCuentaView(APIView):
    permission_classes = [EsAdministrador]

    def post(self, request, pk):
        cuenta = get_object_or_404(User, pk=pk)
        if es_administracion(cuenta):
            return Response({'detail': 'Las cuentas de administración no se sancionan desde aquí.'},
                            status=status.HTTP_400_BAD_REQUEST)
        entrada = SancionarSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        datos = entrada.validated_data
        hasta = timezone.now() + timedelta(days=datos['dias']) if 'dias' in datos else None
        try:
            sancionar(cuenta, datos['tipo'], datos['motivo'], hasta, request.user)
        except SancionAbierta:
            return Response({'detail': 'La cuenta ya tiene una sanción vigente; reactívala antes de cambiarla.'},
                            status=status.HTTP_409_CONFLICT)
        cuenta = cuentas_gestionables().get(pk=cuenta.pk)
        return Response(CuentaSerializer(cuenta).data, status=status.HTTP_201_CREATED)


class ReactivarCuentaView(APIView):
    permission_classes = [EsAdministrador]

    def post(self, request, pk):
        cuenta = get_object_or_404(cuentas_gestionables(), pk=pk)
        entrada = ReactivarSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        if reactivar(cuenta, entrada.validated_data['motivo'].strip(), request.user) is None:
            return Response({'detail': 'La cuenta no tiene una sanción vigente.'}, status=status.HTTP_409_CONFLICT)
        cuenta = cuentas_gestionables().get(pk=cuenta.pk)
        return Response(CuentaSerializer(cuenta).data)
