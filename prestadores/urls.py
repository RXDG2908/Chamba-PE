from django.urls import path

from .views import RegistroPrestadorView, RubroListView

urlpatterns = [
    path('registro/', RegistroPrestadorView.as_view(), name='registro_prestador'),
    path('rubros/', RubroListView.as_view(), name='rubros'),
]
