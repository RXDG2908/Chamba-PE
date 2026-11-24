from django.apps import AppConfig


class CalificacionesConfig(AppConfig):
    default_auto_field = 'django.db.models.BigAutoField'
    name = 'calificaciones'
    verbose_name = 'Calificaciones (HU-12)'

    def ready(self):
        from . import senales  # noqa: F401  (registra el recálculo al eliminar)
