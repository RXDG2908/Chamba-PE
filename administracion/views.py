from rest_framework import mixins, viewsets
from rest_framework.response import Response

from usuarios.permisos import EsAdministrador

from .models import RevisionDocumento
from .serializers import DecisionRevisionSerializer, RevisionDocumentoSerializer


class RevisionDocumentoViewSet(
    mixins.ListModelMixin, mixins.RetrieveModelMixin, mixins.CreateModelMixin,
    viewsets.GenericViewSet,
):
    """
    T9.1: cola de revisión de documentos, solo para administradores (rol `administrador` o is_staff).

    GET    /api/administracion/revisiones/?estado=pendiente
    GET    /api/administracion/revisiones/<id>/
    POST   /api/administracion/revisiones/          {"documento": <id>}
    PATCH  /api/administracion/revisiones/<id>/     {"estado": "aprobado|rechazado", "comentario": "..."}
    """

    permission_classes = [EsAdministrador]
    serializer_class = RevisionDocumentoSerializer

    def get_queryset(self):
        queryset = RevisionDocumento.objects.select_related('documento__prestador', 'revisor')
        estado = self.request.query_params.get('estado')
        if estado:
            queryset = queryset.filter(estado=estado)
        return queryset

    def partial_update(self, request, *args, **kwargs):
        revision = self.get_object()
        decision = DecisionRevisionSerializer(revision, data=request.data, context={'request': request})
        decision.is_valid(raise_exception=True)
        decision.save()
        return Response(self.get_serializer(revision).data)
