from django.contrib import admin

from .models import Calificacion


@admin.register(Calificacion)
class CalificacionAdmin(admin.ModelAdmin):
    list_display = ['id', 'prestador', 'estrellas', 'cliente', 'solicitud', 'creada']
    list_filter = ['estrellas']
    search_fields = ['prestador__nombres', 'prestador__apellidos', 'comentario']
    # Se consultan aquí; se crean solo desde la API, que valida estado y propietario.
    readonly_fields = ['solicitud', 'prestador', 'cliente', 'estrellas', 'comentario', 'creada']

    # Al eliminar, el promedio se recalcula solo (calificaciones/senales.py).
    def has_add_permission(self, request):
        return False
