from django.urls import path

from .views import FotoIntentoDni, IntentoDniView

urlpatterns = [
    path('dni/', IntentoDniView.as_view(), name='verificacion_dni'),
    path('dni/<int:intento_id>/foto/<str:tipo>/', FotoIntentoDni.as_view(), name='verificacion_dni_foto'),
]
