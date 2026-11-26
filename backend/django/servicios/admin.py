from django.contrib import admin

from .models import CambioEstado, Solicitud


class CambioEstadoInline(admin.TabularInline):
    model = CambioEstado
    extra = 0
    can_delete = False
    readonly_fields = ['estado', 'fecha']

    def has_add_permission(self, request, obj=None):
        return False

    def has_change_permission(self, request, obj=None):
        return False


@admin.register(Solicitud)
class SolicitudAdmin(admin.ModelAdmin):
    """Solo para consultar (impedimento 15).

    Las solicitudes nacen por la API, que valida al prestador (verificado, disponible, sin
    sanción, con el rubro y dentro de su radio), y su estado lo cambia solo el prestador
    asignado, una vez y en orden, con historial (HU-4 y HU-11). Si aquí se pudiera crear o
    editar, se saltarían esas reglas: por ejemplo, pasar a otro prestador una solicitud ya
    aceptada o dejar `respondida` vacía. Borrar sí se permite, para quitar solicitudes falsas;
    el promedio de calificaciones se recalcula al borrar (HU-12).
    """
    list_display = ['id', 'prestador', 'rubro', 'cliente_nombre', 'cliente', 'estado', 'creada']
    inlines = [CambioEstadoInline]
    list_filter = ['estado', 'rubro']
    search_fields = ['cliente_nombre', 'prestador__nombres', 'prestador__apellidos']

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False
