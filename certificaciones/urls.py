from django.urls import path
from .views import upload_certification

urlpatterns = [
    path('upload/', upload_certification, name='upload_certification'),
]