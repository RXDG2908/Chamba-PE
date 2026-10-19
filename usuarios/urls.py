from django.urls import path

from .views import CsrfView, LoginView, LogoutView, RegistroClienteView, UsuarioActualView

urlpatterns = [
    path('csrf/', CsrfView.as_view(), name='csrf'),
    path('registro/', RegistroClienteView.as_view(), name='registro_cliente'),
    path('login/', LoginView.as_view(), name='login'),
    path('logout/', LogoutView.as_view(), name='logout'),
    path('yo/', UsuarioActualView.as_view(), name='usuario_actual'),
]
