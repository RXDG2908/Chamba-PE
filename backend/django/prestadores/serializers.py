from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password
from django.db import transaction
from rest_framework import serializers
from rest_framework.validators import UniqueValidator

from usuarios.validadores import nombre_de_persona, telefono_peruano

from .models import Prestador, Rubro

User = get_user_model()


class RubroSerializer(serializers.ModelSerializer):
    class Meta:
        model = Rubro
        fields = ['id', 'nombre']


class DatosPersonalesMixin:
    """Nombres, apellidos y teléfono con las mismas reglas en el registro web y en el de la app."""

    def validate_nombres(self, valor):
        return nombre_de_persona(valor)

    def validate_apellidos(self, valor):
        return nombre_de_persona(valor)

    def validate_telefono(self, valor):
        return telefono_peruano(valor)


class RegistroPrestadorSerializer(DatosPersonalesMixin, serializers.ModelSerializer):
    username = serializers.CharField(write_only=True, max_length=150, validators=[User.username_validator])
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

    def validate_email(self, value):
        if User.objects.filter(email__iexact=value).exists():
            raise serializers.ValidationError('Ya existe una cuenta con ese correo.')
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
        # El estado queda "Pendiente de verificación" por defecto; el rol prestador
        # lo asigna la señal de usuarios al crear el Prestador.
        prestador = Prestador.objects.create(usuario=usuario, **validated_data)
        prestador.rubros.set(rubros)
        return prestador


class RegistroPrestadorMovilSerializer(DatosPersonalesMixin, serializers.ModelSerializer):
    """Registro desde la app móvil: la identidad viene del token de Firebase, no de una contraseña."""
    rubros = serializers.PrimaryKeyRelatedField(
        queryset=Rubro.objects.all(), many=True, allow_empty=False,
        error_messages={'empty': 'Elige al menos un rubro.'},
    )

    class Meta:
        model = Prestador
        fields = [
            'id', 'nombres', 'apellidos', 'dni', 'telefono', 'rubros',
            'latitud', 'longitud', 'radio_cobertura_km', 'estado',
        ]
        read_only_fields = ['id', 'estado']
        extra_kwargs = {
            # Mismas reglas del modelo, con el aviso de DNI repetido en español.
            'dni': {'validators': [
                *Prestador._meta.get_field('dni').validators,
                UniqueValidator(queryset=Prestador.objects.all(), message='Ya existe un prestador con ese DNI.'),
            ]},
        }

    @transaction.atomic
    def create(self, validated_data):
        from usuarios.models import Perfil

        uid = self.context['firebase_uid']
        rubros = validated_data.pop('rubros')
        # Cuenta sin contraseña utilizable: solo se entra con el token de Firebase.
        usuario = User.objects.create_user(username=f'fb-{uid}', email=self.context['email'])
        prestador = Prestador.objects.create(usuario=usuario, **validated_data)
        prestador.rubros.set(rubros)
        # La señal ya dejó el perfil con rol prestador; aquí se vincula con la cuenta de Firebase.
        Perfil.objects.filter(usuario=usuario).update(firebase_uid=uid)
        return prestador
