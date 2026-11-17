from django.contrib import admin

from .models import Prestador, Rubro


@admin.register(Rubro)
class RubroAdmin(admin.ModelAdmin):
    search_fields = ['nombre']


@admin.register(Prestador)
class PrestadorAdmin(admin.ModelAdmin):
    list_display = ['dni', 'nombres', 'apellidos', 'estado', 'disponible']
    list_filter = ['estado', 'disponible', 'rubros']
    search_fields = ['dni', 'nombres', 'apellidos']
