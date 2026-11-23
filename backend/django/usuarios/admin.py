from django.contrib import admin

from .models import Perfil


@admin.register(Perfil)
class PerfilAdmin(admin.ModelAdmin):
    list_display = ['usuario', 'rol', 'firebase_uid']
    list_filter = ['rol']
    search_fields = ['usuario__username', 'usuario__email', 'firebase_uid']

    def save_model(self, request, obj, form, change):
        # Vacío se guarda como NULL: así varias cuentas pueden estar sin vincular.
        obj.firebase_uid = (obj.firebase_uid or '').strip() or None
        super().save_model(request, obj, form, change)
