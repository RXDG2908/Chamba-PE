from rest_framework.permissions import BasePermission

from .models import Perfil, rol_de


class TieneRol(BasePermission):
    """Exige sesión iniciada y el rol indicado; si no, 403."""
    rol = None

    def has_permission(self, request, view):
        usuario = request.user
        return bool(usuario and usuario.is_authenticated and rol_de(usuario) == self.rol)


class EsCliente(TieneRol):
    message = 'Esta acción es solo para clientes.'
    rol = Perfil.Rol.CLIENTE


class EsPrestador(TieneRol):
    message = 'Esta acción es solo para prestadores.'
    rol = Perfil.Rol.PRESTADOR

    def has_permission(self, request, view):
        return super().has_permission(request, view) and hasattr(request.user, 'prestador')


class EsAdministrador(TieneRol):
    message = 'Esta acción es solo para administradores.'
    rol = Perfil.Rol.ADMINISTRADOR

    def has_permission(self, request, view):
        usuario = request.user
        return bool(usuario and usuario.is_authenticated and (
            usuario.is_staff or rol_de(usuario) == self.rol
        ))
