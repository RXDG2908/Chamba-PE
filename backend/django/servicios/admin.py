from django.contrib import admin

from .models import Solicitud


@admin.register(Solicitud)
class SolicitudAdmin(admin.ModelAdmin):
    list_display = ['id', 'prestador', 'rubro', 'cliente_nombre', 'cliente', 'estado', 'creada']
    # HU-12: mientras HU-11 no tenga su pantalla, administración puede marcar Finalizada aquí.
    list_editable = ['estado']
    raw_id_fields = ['cliente']
    list_filter = ['estado', 'rubro']
    search_fields = ['cliente_nombre', 'prestador__nombres', 'prestador__apellidos']
