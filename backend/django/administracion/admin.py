from django.contrib import admin

from .models import RevisionDocumento, SancionCuenta


@admin.register(RevisionDocumento)
class RevisionDocumentoAdmin(admin.ModelAdmin):
    list_display = ['documento', 'estado', 'revisor', 'fecha_revision', 'creado']
    list_filter = ['estado']
    readonly_fields = ['creado']


@admin.register(SancionCuenta)
class SancionCuentaAdmin(admin.ModelAdmin):
    """Solo lectura: las sanciones se aplican y levantan por la API (HU-13), que deja el historial."""
    list_display = ['usuario', 'tipo', 'hasta', 'creada', 'creada_por', 'levantada']
    list_filter = ['tipo']
    search_fields = ['usuario__username', 'motivo']

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False
