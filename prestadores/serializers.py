from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password
from django.db import transaction
from rest_framework import serializers

from .models import Prestador, Rubro

User = get_user_model()


class RubroSerializer(serializers.ModelSerializer):
    class Meta:
        model = Rubro
        fields = ['id', 'nombre']


class RegistroPrestadorSerializer(serializers.ModelSerializer):
    username = serializers.CharField(write_only=True, max_length=150)
    email = serializers.EmailField(write_only=True)
    password = serializers.CharField(write_only=True, style={'input_type': 'password'})
    rubros = serializers.PrimaryKeyRelatedField(
        queryset=Rubro.objects.all(), many=True, allow_empty=False,
        error_messages={'empty': 'Elige al menos un rubro.'},
    )

    class Meta:
        model = Prestador
        fields = [
            'id', 'username', 'email', 'password', 'nombres', 'apellidos', 'dni',
            'telefono', 'rubros', 'latitud', 'longitud', 'radio_cobertura_km', 'estado',
        ]
        read_only_fields = ['id', 'estado']

    def validate_username(self, value):
        if User.objects.filter(username__iexact=value).exists():
            raise serializers.ValidationError('Ese nombre de usuario ya existe.')
        return value

    def validate_password(self, value):
        validate_password(value)
        return value

    @transaction.atomic
    def create(self, validated_data):
        rubros = validated_data.pop('rubros')
        usuario = User.objects.create_user(
            username=validated_data.pop('username'),
            email=validated_data.pop('email'),
            password=validated_data.pop('password'),
        )
        # El estado queda "Pendiente de verificación" por defecto.
        prestador = Prestador.objects.create(usuario=usuario, **validated_data)
        prestador.rubros.set(rubros)
        return prestador
