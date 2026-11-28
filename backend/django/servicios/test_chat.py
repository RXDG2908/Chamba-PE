"""Chat de la solicitud: el cliente y el prestador coordinan desde que se acepta."""
from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse

from prestadores.models import Prestador, Rubro

from .models import Mensaje, Solicitud

E = Solicitud.Estado


class ChatTests(TestCase):
    def setUp(self):
        self.rubro = Rubro.objects.create(nombre='Gasfitería de prueba')
        self.prestador = self.crear_prestador('ana', telefono='987654321')
        self.cliente = get_user_model().objects.create_user(username='carlos')
        self.solicitud = self.crear_solicitud(E.ACEPTADA)

    def crear_prestador(self, username, telefono=''):
        usuario = get_user_model().objects.create_user(username=username)
        prestador = Prestador.objects.create(
            usuario=usuario, dni=f'{Prestador.objects.count() + 1:08d}', nombres='Ana', apellidos='Pérez',
            telefono=telefono, latitud=0, longitud=0, radio_cobertura_km=5, disponible=True,
            estado=Prestador.Estado.VERIFICADO,
        )
        prestador.rubros.set([self.rubro])
        return prestador

    def crear_solicitud(self, estado, cliente=None):
        return Solicitud.objects.create(
            prestador=self.prestador, rubro=self.rubro, cliente=cliente or self.cliente, cliente_nombre='Carlos',
            cliente_telefono='999888777', descripcion='Tubería rota.', latitud=0, longitud=0, estado=estado,
        )

    def url(self, solicitud=None):
        return reverse('chat_solicitud', args=[(solicitud or self.solicitud).id])

    def leer(self, usuario, solicitud=None):
        self.client.force_login(usuario)
        return self.client.get(self.url(solicitud))

    def enviar(self, usuario, texto, solicitud=None):
        self.client.force_login(usuario)
        return self.client.post(self.url(solicitud), {'texto': texto}, content_type='application/json')

    def test_ruta(self):
        self.assertEqual(reverse('chat_solicitud', args=[7]), '/api/servicios/solicitudes/7/chat/')

    def test_conversacion_entre_cliente_y_prestador(self):
        r = self.enviar(self.cliente, 'Hola, ¿a qué hora llega?')
        self.assertEqual(r.status_code, 201)
        self.assertEqual(r.json()['de'], 'cliente')
        self.assertEqual(r.json()['texto'], 'Hola, ¿a qué hora llega?')
        self.assertEqual(self.enviar(self.prestador.usuario, 'En 20 minutos.').json()['de'], 'prestador')

        for usuario, yo in ((self.cliente, 'cliente'), (self.prestador.usuario, 'prestador')):
            r = self.leer(usuario)
            self.assertEqual(r.status_code, 200)
            self.assertEqual(r['Cache-Control'], 'private, no-store')
            datos = r.json()
            self.assertEqual(datos['yo'], yo)
            self.assertTrue(datos['activo'])
            self.assertEqual(datos['estado_display'], 'Confirmado')
            self.assertEqual([(m['de'], m['texto']) for m in datos['mensajes']],
                             [('cliente', 'Hola, ¿a qué hora llega?'), ('prestador', 'En 20 minutos.')])
            self.assertEqual(set(datos['mensajes'][0]), {'id', 'de', 'texto', 'enviado'})

    def test_cada_uno_ve_el_contacto_del_otro(self):
        self.assertEqual(self.leer(self.cliente).json()['contraparte'],
                         {'nombre': 'Ana Pérez', 'telefono': '987654321'})
        self.assertEqual(self.leer(self.prestador.usuario).json()['contraparte'],
                         {'nombre': 'Carlos', 'telefono': '999888777'})

    def test_prestador_sin_telefono(self):
        self.prestador.telefono = ''
        self.prestador.save()
        self.assertIsNone(self.leer(self.cliente).json()['contraparte']['telefono'])

    def test_activo_en_cada_etapa_del_seguimiento(self):
        for estado in (E.ACEPTADA, E.EN_CAMINO, E.EN_SERVICIO):
            solicitud = self.crear_solicitud(estado)
            self.assertEqual(self.enviar(self.cliente, 'Hola', solicitud).status_code, 201, estado)
            self.assertTrue(self.leer(self.cliente, solicitud).json()['activo'])

    def test_finalizada_queda_solo_para_leer(self):
        Mensaje.objects.create(solicitud=self.solicitud, autor=self.cliente, texto='Gracias',
                               enviado='2026-11-28T10:00:00Z')
        Solicitud.objects.filter(pk=self.solicitud.pk).update(estado=E.FINALIZADA)
        datos = self.leer(self.cliente).json()
        self.assertFalse(datos['activo'])
        self.assertEqual([m['texto'] for m in datos['mensajes']], ['Gracias'])
        # Al terminar el servicio ya no se muestra el teléfono del prestador; el del cliente sí al prestador.
        self.assertIsNone(datos['contraparte']['telefono'])
        self.assertEqual(self.leer(self.prestador.usuario).json()['contraparte']['telefono'], '999888777')
        for usuario in (self.cliente, self.prestador.usuario):
            r = self.enviar(usuario, 'Otro')
            self.assertEqual(r.status_code, 409)
            self.assertEqual(r.json(), {'chat': 'El servicio ya finalizó; el chat queda solo para leer.'})
        self.assertEqual(Mensaje.objects.count(), 1)

    def test_pendiente_y_rechazada_no_tienen_chat(self):
        casos = {
            E.PENDIENTE: 'El chat se abre cuando el prestador acepta la solicitud.',
            E.RECHAZADA: 'La solicitud fue rechazada y no tiene chat.',
        }
        for estado, detalle in casos.items():
            solicitud = self.crear_solicitud(estado)
            for r in (self.leer(self.cliente, solicitud), self.enviar(self.prestador.usuario, 'Hola', solicitud)):
                self.assertEqual(r.status_code, 409, estado)
                self.assertEqual(r.json(), {'chat': detalle})
        self.assertFalse(Mensaje.objects.exists())

    def test_sin_sesion_403(self):
        for r in (self.client.get(self.url()), self.client.post(self.url(), {'texto': 'Hola'})):
            self.assertEqual(r.status_code, 403)
            self.assertEqual(r.json(), {'detail': 'Authentication credentials were not provided.'})

    def test_otros_no_ven_ni_escriben_404(self):
        otro_cliente = get_user_model().objects.create_user(username='intruso')
        otro_prestador = self.crear_prestador('beto').usuario
        admin = get_user_model().objects.create_user(username='admin', is_staff=True)
        for usuario in (otro_cliente, otro_prestador, admin):
            for r in (self.leer(usuario), self.enviar(usuario, 'Hola')):
                self.assertEqual(r.status_code, 404, usuario.username)
                self.assertEqual(r.json(), {'detail': 'No Solicitud matches the given query.'})
        self.assertEqual(self.client.get(reverse('chat_solicitud', args=[999])).status_code, 404)
        self.assertFalse(Mensaje.objects.exists())

    def test_validacion_del_texto(self):
        casos = [
            ({}, {'texto': ['This field is required.']}),
            ({'texto': ''}, {'texto': ['Escribe un mensaje.']}),
            ({'texto': '   \n'}, {'texto': ['Escribe un mensaje; no uses solo espacios.']}),
            ({'texto': None}, {'texto': ['This field may not be null.']}),
            ({'texto': 'x' * 501}, {'texto': ['Ensure this field has no more than 500 characters.']}),
            ({'texto': ['Hola']}, {'texto': ['Not a valid string.']}),
            ({'texto': True}, {'texto': ['Not a valid string.']}),
            ({'texto': 'a\x00'}, {'texto': ['Null characters are not allowed.']}),
            ({'texto': 'x' * 501 + '\x00'},
             {'texto': ['Ensure this field has no more than 500 characters.', 'Null characters are not allowed.']}),
            ('"hola"', {'non_field_errors': ['Invalid data. Expected a dictionary, but got str.']}),
        ]
        self.client.force_login(self.cliente)
        for cuerpo, errores in casos:
            r = self.client.post(self.url(), cuerpo, content_type='application/json')
            self.assertEqual(r.status_code, 400, cuerpo)
            self.assertEqual(r.json(), errores)
        self.assertEqual(self.enviar(self.cliente, 'x' * 500).status_code, 201)
        # Un número se guarda como texto, como cualquier CharField de DRF.
        self.assertEqual(self.client.post(self.url(), {'texto': 12}, content_type='application/json').json()['texto'],
                         '12')
        # El texto se guarda tal cual, sin recortar espacios.
        self.assertEqual(self.enviar(self.cliente, '  Hola  ').json()['texto'], '  Hola  ')

    def test_mensajes_en_orden_de_envio(self):
        for i in range(3):
            self.enviar(self.cliente if i % 2 == 0 else self.prestador.usuario, f'm{i}')
        self.assertEqual([m['texto'] for m in self.leer(self.cliente).json()['mensajes']], ['m0', 'm1', 'm2'])

    def test_empresa_se_muestra_con_su_nombre_comercial(self):
        self.prestador.tipo = Prestador.Tipo.EMPRESA
        self.prestador.nombre_comercial = 'Gasfiteros SAC'
        self.prestador.ruc = '20123456789'
        self.prestador.save()
        self.assertEqual(self.leer(self.cliente).json()['contraparte']['nombre'], 'Gasfiteros SAC')
