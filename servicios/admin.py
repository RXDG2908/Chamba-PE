from django.contrib import admin

from .models import Solicitud


@admin.register(Solicitud)
class SolicitudAdmin(admin.ModelAdmin):
    list_display = ['id', 'prestador', 'rubro', 'cliente_nombre', 'estado', 'creada']
    list_filter = ['estado', 'rubro']
    search_fields = ['cliente_nombre', 'prestador__nombres', 'prestador__apellidos']
