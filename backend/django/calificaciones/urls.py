from django.urls import path

from .views import CalificarView, MisServiciosView

urlpatterns = [
    path('', CalificarView.as_view(), name='calificar'),
    path('mis-servicios/', MisServiciosView.as_view(), name='mis_servicios'),
]
