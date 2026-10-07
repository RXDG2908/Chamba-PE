from django.urls import path
from .views import upload_certification, listar_prestadores_por_distancia

urlpatterns = [

    path('upload/', upload_certification, name='upload_certification'),
    path('prestadores/cercanos/', listar_prestadores_por_distancia, name='prestadores_cercanos'),
]