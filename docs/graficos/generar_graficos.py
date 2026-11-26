"""Genera los gráficos SVG del README (burndown del Sprint 4 y pruebas automatizadas).

Método de la skill dataviz: una sola escala, marcas finas, etiquetas directas, texto en tinta
(no en el color de la serie), modo oscuro con sus propios tonos validados con
`validate_palette.js` (claro #16734f/#c2701a, oscuro #2f9e72/#d07f22: todo PASS).

Uso: python docs/graficos/generar_graficos.py  (sin dependencias). Actualiza los números aquí.
"""
from pathlib import Path

AQUI = Path(__file__).parent

ESTILO = """
<style>
  .fondo { fill: #fcfcfb; }
  .tinta { fill: #1c2b23; } .suave { fill: #5a6b62; } .rejilla { stroke: #e3e9e5; }
  .eje { stroke: #b8c4bd; } .real { stroke: #16734f; } .punto { fill: #16734f; stroke: #fcfcfb; }
  .ideal { stroke: #8a9a91; } .barra { fill: #16734f; }
  text { font-family: 'Segoe UI', system-ui, -apple-system, Roboto, sans-serif; }
  @media (prefers-color-scheme: dark) {
    .fondo { fill: #1a1a19; }
    .tinta { fill: #e6ece8; } .suave { fill: #a7b4ad; } .rejilla { stroke: #2e3330; }
    .eje { stroke: #4a524d; } .real { stroke: #2f9e72; } .punto { fill: #2f9e72; stroke: #1a1a19; }
    .ideal { stroke: #7d8a83; } .barra { fill: #2f9e72; }
  }
</style>
"""


def svg(ancho, alto, titulo, desc, cuerpo):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {ancho} {alto}" width="{ancho}" height="{alto}" '
            f'role="img" aria-labelledby="t d"><title id="t">{titulo}</title><desc id="d">{desc}</desc>{ESTILO}'
            f'<rect class="fondo" width="{ancho}" height="{alto}" rx="12"/>{cuerpo}</svg>\n')


def burndown():
    # SPRINTS.md · Sprint 4 (12/11 – 25/11): horas de las 26 que aún no estaban en main.
    ideal = [26, 23.4, 20.8, 18.2, 15.6, 13, 10.4, 7.8, 5.2, 2.6, 0]
    real = [26, 26, 26, 26, 26, 26, 26, 26, 13, 13, 0]
    dias = ['Inicio', '12/11', '13/11', '16/11', '17/11', '18/11', '19/11', '20/11', '23/11', '24/11', '25/11']
    W, H, izq, der, arr, aba = 760, 380, 56, 170, 64, 52
    ancho, alto = W - izq - der, H - arr - aba

    def x(i): return izq + ancho * i / (len(dias) - 1)
    def y(v): return arr + alto * (1 - v / 30)

    c = [f'<text class="tinta" x="{izq}" y="30" font-size="17" font-weight="700">Burndown del Sprint 4</text>',
         f'<text class="suave" x="{izq}" y="50" font-size="13">Horas pendientes de unir a main (26 h comprometidas)</text>']
    for v in (0, 10, 20, 30):
        c.append(f'<line class="rejilla" x1="{izq}" x2="{izq + ancho}" y1="{y(v)}" y2="{y(v)}" stroke-width="1"/>')
        c.append(f'<text class="suave" x="{izq - 10}" y="{y(v) + 4}" font-size="12" text-anchor="end">{v} h</text>')
    c.append(f'<line class="eje" x1="{izq}" x2="{izq + ancho}" y1="{y(0)}" y2="{y(0)}" stroke-width="1.5"/>')
    for i, d in enumerate(dias):
        if i % 2 == 0 or i == len(dias) - 1:
            c.append(f'<text class="suave" x="{x(i)}" y="{H - 26}" font-size="12" text-anchor="middle">{d}</text>')
    pts = lambda serie: ' '.join(f'{x(i):.1f},{y(v):.1f}' for i, v in enumerate(serie))
    c.append(f'<polyline class="ideal" points="{pts(ideal)}" fill="none" stroke-width="2" stroke-dasharray="6 5"/>')
    c.append(f'<polyline class="real" points="{pts(real)}" fill="none" stroke-width="2.5" stroke-linejoin="round"/>')
    for i, v in enumerate(real):
        c.append(f'<circle class="punto" cx="{x(i):.1f}" cy="{y(v):.1f}" r="4.5" stroke-width="2"><title>{dias[i]}: {v} h</title></circle>')
    # Etiquetas directas al final de cada serie (identidad sin depender del color).
    c.append(f'<text class="tinta" x="{x(len(dias) - 1) + 12}" y="{y(0) - 20}" font-size="13" font-weight="700">Real: 0 h</text>')
    c.append(f'<text class="suave" x="{x(len(dias) - 1) + 12}" y="{y(0) - 2}" font-size="12">25/11: HU-10 y T11.3</text>')
    c.append(f'<text class="suave" x="{x(3)}" y="{y(ideal[3]) - 12}" font-size="13">Ideal</text>')
    c.append(f'<text class="suave" x="{x(8) + 6}" y="{y(13) - 12}" font-size="12">23/11: HU-11 y HU-12</text>')
    (AQUI / 'burndown-sprint4.svg').write_text(svg(
        W, H, 'Burndown del Sprint 4',
        'Línea real plana en 26 horas hasta el 20/11, baja a 13 el 23/11 y a 0 el 25/11; la ideal baja de 26 a 0.',
        ''.join(c)), encoding='utf-8')


def pruebas():
    # Pruebas automatizadas que pasan en main (26/11/2026, tras la auditoría «harden» y la migración a Firebase real).
    datos = [('Django (API)', 478), ('Playwright (web)', 115), ('Spring Boot', 58), ('Android (unitarias)', 42)]
    W, H, izq, der, arr = 720, 300, 170, 70, 70
    fila = 46
    maximo = 500
    ancho = W - izq - der
    c = ['<text class="tinta" x="24" y="30" font-size="17" font-weight="700">Pruebas automatizadas</text>',
         f'<text class="suave" x="24" y="50" font-size="13">{sum(v for _, v in datos)} pruebas, todas en verde</text>']
    for v in (0, 100, 200, 300, 400, 500):
        xv = izq + ancho * v / maximo
        c.append(f'<line class="rejilla" x1="{xv}" x2="{xv}" y1="{arr - 6}" y2="{arr + fila * len(datos)}" stroke-width="1"/>')
    for i, (nombre, valor) in enumerate(datos):
        yb = arr + i * fila + 8
        largo = ancho * valor / maximo
        c.append(f'<text class="tinta" x="{izq - 12}" y="{yb + 19}" font-size="13" text-anchor="end">{nombre}</text>')
        # Extremo redondeado de 4 px en el dato, anclado a la base (recta).
        c.append(f'<path class="barra" d="M{izq},{yb} h{largo - 4:.1f} a4,4 0 0 1 4,4 v20 a4,4 0 0 1 -4,4 h-{largo - 4:.1f} z">'
                 f'<title>{nombre}: {valor}</title></path>')
        c.append(f'<text class="tinta" x="{izq + largo + 8:.1f}" y="{yb + 19}" font-size="13" font-weight="700">{valor}</text>')
    c.append(f'<line class="eje" x1="{izq}" x2="{izq}" y1="{arr - 6}" y2="{arr + fila * len(datos)}" stroke-width="1.5"/>')
    (AQUI / 'pruebas-automatizadas.svg').write_text(svg(
        W, H, 'Pruebas automatizadas por módulo',
        'Django 478, Playwright 115, Spring Boot 58 y Android 42 pruebas, todas pasan.', ''.join(c)), encoding='utf-8')


burndown()
pruebas()
print('listo')
