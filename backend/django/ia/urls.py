from django.urls import path

from .views import RecomendacionView

urlpatterns = [
    path('recomendacion/', RecomendacionView.as_view(), name='recomendacion'),
]
