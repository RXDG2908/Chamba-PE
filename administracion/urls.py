from rest_framework.routers import DefaultRouter

from .views import RevisionDocumentoViewSet

router = DefaultRouter()
router.register('revisiones', RevisionDocumentoViewSet, basename='revision-documento')

urlpatterns = router.urls
