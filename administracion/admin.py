from django.contrib import admin

from .models import RevisionDocumento


@admin.register(RevisionDocumento)
class RevisionDocumentoAdmin(admin.ModelAdmin):
    list_display = ['documento', 'estado', 'revisor', 'fecha_revision', 'creado']
    list_filter = ['estado']
    readonly_fields = ['creado']
