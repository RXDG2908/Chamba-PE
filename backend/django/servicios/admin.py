from django.contrib import admin

from .models import CambioEstado, Solicitud


class CambioEstadoInline(admin.TabularInline):
    model = CambioEstado
    extra = 0
    can_delete = False
    readonly_fields = ['estado', 'fecha']

    def has_add_permission(self, request, obj=None):
        return False


@admin.register(Solicitud)
class SolicitudAdmin(admin.ModelAdmin):
    list_display = ['id', 'prestador', 'rubro', 'cliente_nombre', 'cliente', 'estado', 'creada']
    # HU-11: el estado lo cambia el prestador asignado desde su bandeja, en orden y con historial.
    # Ya no se edita aquí (antes era temporal para probar HU-12), para que nadie se salte etapas.
    readonly_fields = ['estado', 'respondida']
    inlines = [CambioEstadoInline]
    raw_id_fields = ['cliente']
    list_filter = ['estado', 'rubro']
    search_fields = ['cliente_nombre', 'prestador__nombres', 'prestador__apellidos']
