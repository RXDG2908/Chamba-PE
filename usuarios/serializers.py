from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password
from django.db import transaction
from rest_framework import serializers

from .models import Perfil, rol_de

User = get_user_model()


def validar_correo_unico(value):
    if User.objects.filter(email__iexact=value).exists():
        raise serializers.ValidationError('Ya existe una cuenta con ese correo.')
    return value


class RegistroClienteSerializer(serializers.Serializer):
    username = serializers.CharField(max_length=150)
    email = serializers.EmailField()
    password = serializers.CharField(write_only=True, style={'input_type': 'password'})
    nombres = serializers.CharField(max_length=100)
    apellidos = serializers.CharField(max_length=100)

    def validate_username(self, value):
        if User.objects.filter(username__iexact=value).exists():
            raise serializers.ValidationError('Ese nombre de usuario ya existe.')
        return value

    def validate_email(self, value):
        return validar_correo_unico(value)

    def validate_password(self, value):
        validate_password(value)
        return value

    @transaction.atomic
    def create(self, validated_data):
        usuario = User.objects.create_user(
            username=validated_data['username'],
            email=validated_data['email'],
            password=validated_data['password'],
            first_name=validated_data['nombres'],
            last_name=validated_data['apellidos'],
        )
        Perfil.objects.update_or_create(usuario=usuario, defaults={'rol': Perfil.Rol.CLIENTE})
        return usuario


class LoginSerializer(serializers.Serializer):
    username = serializers.CharField()
    password = serializers.CharField(style={'input_type': 'password'})


class UsuarioActualSerializer(serializers.Serializer):
    """Cuerpo de GET /api/usuarios/yo/."""

    def to_representation(self, usuario):
        prestador = getattr(usuario, 'prestador', None)
        return {
            'id': usuario.id,
            'username': usuario.username,
            'email': usuario.email,
            'nombres': prestador.nombres if prestador else usuario.first_name,
            'apellidos': prestador.apellidos if prestador else usuario.last_name,
            'rol': rol_de(usuario),
            'prestador_id': prestador.id if prestador else None,
        }
