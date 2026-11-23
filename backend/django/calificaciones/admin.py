from django.contrib import admin

from .models import Calificacion
from .servicio import actualizar_promedio


@admin.register(Calificacion)
class CalificacionAdmin(admin.ModelAdmin):
    list_display = ['id', 'prestador', 'estrellas', 'cliente', 'solicitud', 'creada']
    list_filter = ['estrellas']
    search_fields = ['prestador__nombres', 'prestador__apellidos', 'comentario']
    # Se consultan aquí; se crean solo desde la API, que valida estado y propietario.
    readonly_fields = ['solicitud', 'prestador', 'cliente', 'estrellas', 'comentario', 'creada']

    def has_add_permission(self, request):
        return False

    def delete_model(self, request, obj):
        prestador_id = obj.prestador_id
        super().delete_model(request, obj)
        actualizar_promedio(prestador_id)

    def delete_queryset(self, request, queryset):
        prestadores = set(queryset.values_list('prestador_id', flat=True))
        super().delete_queryset(request, queryset)
        for prestador_id in prestadores:
            actualizar_promedio(prestador_id)
