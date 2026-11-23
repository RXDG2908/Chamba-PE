from django.urls import path

from servicios.views import BuscarPrestadoresView

from .views import RegistroPrestadorMovilView, RegistroPrestadorView, RubroListView

urlpatterns = [
    path('buscar/', BuscarPrestadoresView.as_view(), name='buscar_prestadores'),
    path('registro/', RegistroPrestadorView.as_view(), name='registro_prestador'),
    path('registro-movil/', RegistroPrestadorMovilView.as_view(), name='registro_prestador_movil'),
    path('rubros/', RubroListView.as_view(), name='rubros'),
]
