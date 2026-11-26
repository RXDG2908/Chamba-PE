from django.urls import path
from rest_framework.routers import DefaultRouter

from .cuentas import CuentasView, DetalleCuentaView, ReactivarCuentaView, SancionarCuentaView
from .views import RevisionDocumentoViewSet

router = DefaultRouter()
router.register('revisiones', RevisionDocumentoViewSet, basename='revision-documento')

urlpatterns = router.urls + [
    # HU-13: suspensión y bloqueo de cuentas.
    path('cuentas/', CuentasView.as_view(), name='cuentas'),
    path('cuentas/<int:pk>/', DetalleCuentaView.as_view(), name='cuenta_detalle'),
    path('cuentas/<int:pk>/sancionar/', SancionarCuentaView.as_view(), name='cuenta_sancionar'),
    path('cuentas/<int:pk>/reactivar/', ReactivarCuentaView.as_view(), name='cuenta_reactivar'),
]
