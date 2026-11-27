"""Perfil editable de clientes y prestadores (GET/PATCH /api/usuarios/perfil/).

Cada cuenta cambia aquí sus datos: foto, nombres, correo, teléfono y contraseña. El prestador además
elige si se presenta como independiente (con su nombre) o como empresa (con su nombre comercial y
RUC), escribe una descripción y ajusta sus rubros y su radio de cobertura.

Los nombres y apellidos del prestador se comparan con su DNI (HU-19): una vez verificado ya no se
cambian aquí, para que nadie verifique una identidad y luego se presente con otra.
"""
from decimal import Decimal
from io import BytesIO

from django.contrib.auth import get_user_model, update_session_auth_hash
from django.contrib.auth.password_validation import validate_password
from django.core.files.base import ContentFile
from django.db import transaction
from PIL import Image, ImageOps, UnidentifiedImageError
from rest_framework import serializers, status
from rest_framework.parsers import FormParser, MultiPartParser
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from prestadores.models import Prestador, Rubro

from .models import Perfil, rol_de
from .validadores import nombre_de_persona, telefono_peruano

User = get_user_model()

FOTO_MAXIMO_BYTES = 5 * 1024 * 1024
FOTO_LADO_MAXIMO = 512
FOTO_FORMATOS = {'JPEG', 'PNG', 'WEBP'}
MENSAJE_NOMBRE_VERIFICADO = (
    'Tu nombre ya se verificó con tu DNI y no se puede cambiar aquí. '
    'Si hay un error, escribe a administración.'
)


def perfil_de(usuario):
    perfil, _ = Perfil.objects.get_or_create(
        usuario=usuario, defaults={'rol': rol_de(usuario) or Perfil.Rol.CLIENTE},
    )
    return perfil


def prestador_de(usuario):
    """El prestador de la cuenta, solo si su rol es prestador (como en /yo/)."""
    if rol_de(usuario) != Perfil.Rol.PRESTADOR:
        return None
    return getattr(usuario, 'prestador', None)


def url_foto(perfil):
    """Ruta de la foto (/media/...), igual que en /yo/ y en la búsqueda; o None."""
    return perfil.foto.url if perfil and perfil.foto else None


def procesar_foto(archivo):
    """Valida la imagen y la devuelve como JPEG cuadrado de hasta 512 px, sin metadatos (EXIF/GPS)."""
    if archivo.size > FOTO_MAXIMO_BYTES:
        raise serializers.ValidationError({'foto': ['La foto pesa más de 5 MB.']})
    try:
        imagen = Image.open(archivo)
        formato = imagen.format
        imagen.load()
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError):
        raise serializers.ValidationError({'foto': ['El archivo no es una imagen válida.']})
    if formato not in FOTO_FORMATOS:
        raise serializers.ValidationError({'foto': ['Sube una foto JPG, PNG o WEBP.']})
    imagen = ImageOps.exif_transpose(imagen)
    if imagen.mode != 'RGB':
        fondo = Image.new('RGB', imagen.size, (255, 255, 255))
        con_alfa = imagen.convert('RGBA')
        fondo.paste(con_alfa, mask=con_alfa.getchannel('A'))
        imagen = fondo
    imagen = ImageOps.fit(imagen, (min(imagen.size),) * 2)  # recorte cuadrado centrado
    imagen.thumbnail((FOTO_LADO_MAXIMO, FOTO_LADO_MAXIMO))
    salida = BytesIO()
    imagen.save(salida, 'JPEG', quality=85, optimize=True)  # sin exif: se descartan los metadatos
    return ContentFile(salida.getvalue(), name='foto.jpg')


class PerfilSerializer(serializers.Serializer):
    """Cuerpo de GET y PATCH /api/usuarios/perfil/. Los campos del prestador solo aparecen para él."""
    nombres = serializers.CharField(max_length=100, required=False)
    apellidos = serializers.CharField(max_length=100, required=False)
    email = serializers.EmailField(max_length=254, required=False)
    telefono = serializers.CharField(max_length=20, required=False, allow_blank=True)
    # Solo del prestador.
    tipo = serializers.ChoiceField(choices=Prestador.Tipo.choices, required=False)
    nombre_comercial = serializers.CharField(max_length=120, required=False, allow_blank=True)
    ruc = serializers.CharField(max_length=11, required=False, allow_blank=True)
    descripcion = serializers.CharField(max_length=500, required=False, allow_blank=True)
    rubros = serializers.PrimaryKeyRelatedField(
        queryset=Rubro.objects.all(), many=True, required=False, allow_empty=False,
        error_messages={'empty': 'Elige al menos un rubro.'},
    )
    radio_cobertura_km = serializers.IntegerField(min_value=1, max_value=100, required=False)
    # El mapa da más decimales de los que guarda el modelo: se redondean a 6 (≈ 10 cm).
    latitud = serializers.FloatField(min_value=-90, max_value=90, required=False)
    longitud = serializers.FloatField(min_value=-180, max_value=180, required=False)

    CAMPOS_PRESTADOR = ('tipo', 'nombre_comercial', 'ruc', 'descripcion', 'rubros', 'radio_cobertura_km',
                        'latitud', 'longitud')

    def __init__(self, usuario, *args, **kwargs):
        self.usuario = usuario
        self.prestador = prestador_de(usuario)
        super().__init__(*args, **kwargs)
        if self.prestador is None:
            for campo in self.CAMPOS_PRESTADOR:
                self.fields.pop(campo)

    def validate_nombres(self, valor):
        return nombre_de_persona(valor)

    def validate_apellidos(self, valor):
        return nombre_de_persona(valor)

    def validate_telefono(self, valor):
        return telefono_peruano(valor)

    def validate_email(self, valor):
        if User.objects.filter(email__iexact=valor).exclude(pk=self.usuario.pk).exists():
            raise serializers.ValidationError('Ya existe una cuenta con ese correo.')
        return valor

    def validate_nombre_comercial(self, valor):
        return ' '.join(valor.split())

    def validate_ruc(self, valor):
        valor = valor.strip()
        if valor and not Prestador._meta.get_field('ruc').validators[0].regex.fullmatch(valor):
            raise serializers.ValidationError('El RUC tiene 11 dígitos y empieza con 10, 15, 17 o 20.')
        return valor

    def validate_latitud(self, valor):
        return Decimal(str(round(valor, 6)))

    def validate_longitud(self, valor):
        return Decimal(str(round(valor, 6)))

    def validate_descripcion(self, valor):
        return valor.strip()

    def validate(self, datos):
        prestador = self.prestador
        if prestador is None:
            return datos
        if prestador.estado == Prestador.Estado.VERIFICADO:
            errores = {
                campo: [MENSAJE_NOMBRE_VERIFICADO] for campo in ('nombres', 'apellidos')
                if campo in datos and datos[campo] != getattr(prestador, campo)
            }
            if errores:
                raise serializers.ValidationError(errores)
        tipo = datos.get('tipo', prestador.tipo)
        nombre_comercial = datos.get('nombre_comercial', prestador.nombre_comercial)
        if tipo == Prestador.Tipo.EMPRESA and not nombre_comercial:
            raise serializers.ValidationError({'nombre_comercial': ['Escribe el nombre de tu empresa.']})
        return datos

    @transaction.atomic
    def save(self):
        datos = self.validated_data
        usuario, prestador, perfil = self.usuario, self.prestador, perfil_de(self.usuario)
        if 'email' in datos:
            usuario.email = datos['email']
        if prestador is None:
            usuario.first_name = datos.get('nombres', usuario.first_name)
            usuario.last_name = datos.get('apellidos', usuario.last_name)
            if 'telefono' in datos:
                perfil.telefono = datos['telefono']
                perfil.save(update_fields=['telefono'])
        else:
            for campo in ('nombres', 'apellidos', 'telefono', 'tipo', 'nombre_comercial', 'ruc',
                          'descripcion', 'radio_cobertura_km', 'latitud', 'longitud'):
                if campo in datos:
                    setattr(prestador, campo, datos[campo])
            prestador.save()
            if 'rubros' in datos:
                prestador.rubros.set(datos['rubros'])
            # La cuenta de Django lleva el mismo nombre (lo usan el admin y los avisos).
            usuario.first_name, usuario.last_name = prestador.nombres, prestador.apellidos
        usuario.save(update_fields=['email', 'first_name', 'last_name'])
        return usuario

    def to_representation(self, usuario):
        perfil = perfil_de(usuario)
        prestador = prestador_de(usuario)
        datos = {
            'username': usuario.username,
            'email': usuario.email,
            'rol': rol_de(usuario),
            'foto': url_foto(perfil),
            'tiene_contrasena': usuario.has_usable_password(),
            'cuenta_app': bool(perfil.firebase_uid),
        }
        if prestador is None:
            datos.update(nombres=usuario.first_name, apellidos=usuario.last_name, telefono=perfil.telefono)
            return datos
        datos.update(
            nombres=prestador.nombres,
            apellidos=prestador.apellidos,
            telefono=prestador.telefono,
            nombres_editables=prestador.estado != Prestador.Estado.VERIFICADO,
            tipo=prestador.tipo,
            nombre_comercial=prestador.nombre_comercial,
            ruc=prestador.ruc,
            descripcion=prestador.descripcion,
            nombre_publico=prestador.nombre_publico,
            rubros=[r.id for r in prestador.rubros.all()],
            radio_cobertura_km=prestador.radio_cobertura_km,
            latitud=float(prestador.latitud),
            longitud=float(prestador.longitud),
            estado=prestador.estado,
            # Solo los 3 últimos dígitos: la pantalla lo muestra para que la persona lo reconozca.
            dni=f'•••••{prestador.dni[-3:]}',
        )
        return datos


class PerfilView(APIView):
    """GET: datos del perfil. PATCH: cambia solo los campos enviados."""
    permission_classes = [IsAuthenticated]

    def get(self, request):
        return Response(PerfilSerializer(request.user, context={'request': request}).to_representation(request.user))

    def patch(self, request):
        serializer = PerfilSerializer(request.user, data=request.data, partial=True, context={'request': request})
        serializer.is_valid(raise_exception=True)
        usuario = serializer.save()
        return Response(serializer.to_representation(usuario))


class FotoPerfilView(APIView):
    """POST (multipart, campo «foto»): sube o reemplaza la foto. DELETE: la quita."""
    permission_classes = [IsAuthenticated]
    parser_classes = [MultiPartParser, FormParser]

    def post(self, request):
        archivo = request.FILES.get('foto')
        if archivo is None:
            return Response({'foto': ['Elige una foto.']}, status=status.HTTP_400_BAD_REQUEST)
        contenido = procesar_foto(archivo)
        perfil = perfil_de(request.user)
        anterior = perfil.foto.name if perfil.foto else None
        perfil.foto.save(contenido.name, contenido, save=True)
        if anterior and anterior != perfil.foto.name:
            try:
                perfil.foto.storage.delete(anterior)
            except OSError:  # p. ej. en uso en Windows: queda huérfana, la foto nueva ya está guardada
                pass
        return Response({'foto': url_foto(perfil)})

    def delete(self, request):
        perfil = perfil_de(request.user)
        if perfil.foto:
            perfil.foto.delete(save=True)
        return Response(status=status.HTTP_204_NO_CONTENT)


class CambioContrasenaSerializer(serializers.Serializer):
    actual = serializers.CharField(style={'input_type': 'password'})
    nueva = serializers.CharField(style={'input_type': 'password'})

    def validate(self, datos):
        usuario = self.context['usuario']
        if not usuario.check_password(datos['actual']):
            raise serializers.ValidationError({'actual': ['La contraseña actual no es correcta.']})
        try:
            validate_password(datos['nueva'], usuario)
        except Exception as error:  # ValidationError de Django → mensajes por campo
            raise serializers.ValidationError({'nueva': list(getattr(error, 'messages', [str(error)]))})
        return datos


class CambioContrasenaView(APIView):
    """POST {actual, nueva}: cambia la contraseña sin cerrar la sesión actual.

    Las cuentas creadas desde la app no tienen contraseña (entran con Firebase): reciben 400.
    """
    permission_classes = [IsAuthenticated]

    def post(self, request):
        usuario = request.user
        if not usuario.has_usable_password():
            return Response(
                {'detail': 'Tu cuenta entra con la app (Google/Firebase); cambia tu contraseña desde allí.'},
                status=status.HTTP_400_BAD_REQUEST,
            )
        serializer = CambioContrasenaSerializer(data=request.data, context={'usuario': usuario})
        serializer.is_valid(raise_exception=True)
        usuario.set_password(serializer.validated_data['nueva'])
        usuario.save(update_fields=['password'])
        update_session_auth_hash(request._request, usuario)
        return Response(status=status.HTTP_204_NO_CONTENT)
