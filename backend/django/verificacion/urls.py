from django.urls import path

from .views import FotoIntentoDni, IntentoDniView
from .vistas_consentimiento import ConsentimientoView, RevocarConsentimientoView
from .vistas_eliminacion import EliminacionesDniView, EliminacionDniDetalleView, ReintentarEliminacionDniView

urlpatterns = [
    path('eliminaciones/', EliminacionesDniView.as_view(), name='eliminaciones_dni'),
    path('eliminaciones/<uuid:identificador>/', EliminacionDniDetalleView.as_view(), name='eliminacion_dni'),
    path('eliminaciones/<uuid:identificador>/reintentar/', ReintentarEliminacionDniView.as_view(), name='reintentar_eliminacion_dni'),
    path('consentimiento/', ConsentimientoView.as_view(), name='consentimiento'),
    path('consentimiento/revocar/', RevocarConsentimientoView.as_view(), name='revocar_consentimiento'),
    path('dni/', IntentoDniView.as_view(), name='verificacion_dni'),
    path('dni/<int:intento_id>/foto/<str:tipo>/', FotoIntentoDni.as_view(), name='verificacion_dni_foto'),
]
