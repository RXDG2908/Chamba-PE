from django.urls import path

from .views import (
    AceptarSolicitudView, CrearSolicitudView, DetalleSolicitudView,
    RechazarSolicitudView, SolicitudesRecibidasView,
)

urlpatterns = [
    path('solicitudes/', CrearSolicitudView.as_view(), name='crear_solicitud'),
    path('solicitudes/recibidas/', SolicitudesRecibidasView.as_view(), name='solicitudes_recibidas'),
    path('solicitudes/<int:pk>/', DetalleSolicitudView.as_view(), name='detalle_solicitud'),
    path('solicitudes/<int:pk>/aceptar/', AceptarSolicitudView.as_view(), name='aceptar_solicitud'),
    path('solicitudes/<int:pk>/rechazar/', RechazarSolicitudView.as_view(), name='rechazar_solicitud'),
]
