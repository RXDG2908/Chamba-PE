from django.urls import path

from .cuentas_compartidas import LoginFirebaseView, VincularCuentaView

from .views import (
    CsrfView, DisponibilidadView, LoginView, LogoutView, RegistroClienteMovilView, RegistroClienteView, UsuarioActualView,
)

urlpatterns = [
    path('csrf/', CsrfView.as_view(), name='csrf'),
    path('registro/', RegistroClienteView.as_view(), name='registro_cliente'),
    path('registro-movil/', RegistroClienteMovilView.as_view(), name='registro_cliente_movil'),
    path('login/', LoginView.as_view(), name='login'),
    path('login-firebase/', LoginFirebaseView.as_view(), name='login_firebase'),
    path('vincular/', VincularCuentaView.as_view(), name='vincular_cuenta'),
    path('logout/', LogoutView.as_view(), name='logout'),
    path('yo/', UsuarioActualView.as_view(), name='usuario_actual'),
    path('disponibilidad/', DisponibilidadView.as_view(), name='disponibilidad'),
]
