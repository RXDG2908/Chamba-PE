from django.apps import AppConfig


class AdministracionConfig(AppConfig):
    name = 'administracion'

    def ready(self):
        from . import signals  # noqa: F401
