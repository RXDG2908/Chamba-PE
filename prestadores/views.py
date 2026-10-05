from rest_framework import generics
from rest_framework.permissions import AllowAny

from .models import Rubro
from .serializers import RegistroPrestadorSerializer, RubroSerializer


class RegistroPrestadorView(generics.CreateAPIView):
    serializer_class = RegistroPrestadorSerializer
    permission_classes = [AllowAny]
    authentication_classes = []


class RubroListView(generics.ListAPIView):
    queryset = Rubro.objects.all()
    serializer_class = RubroSerializer
    permission_classes = [AllowAny]
    pagination_class = None
