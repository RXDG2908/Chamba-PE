from django.urls import path

from .views import RegistroPrestadorView, RubroListView
from servicios.views import BuscarPrestadoresView

urlpatterns = [
    path('buscar/', BuscarPrestadoresView.as_view(), name='buscar_prestadores'),
    path('registro/', RegistroPrestadorView.as_view(), name='registro_prestador'),
    path('rubros/', RubroListView.as_view(), name='rubros'),
]
