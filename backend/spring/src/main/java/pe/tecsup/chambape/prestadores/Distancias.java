package pe.tecsup.chambape.prestadores;

/** Misma fórmula que {@code servicios/distancias.py} de Django. */
public final class Distancias {

    public static final double RADIO_TIERRA_KM = 6371.0088;

    private Distancias() {
    }

    /** Distancia geográfica Haversine en km. */
    public static double km(double latitudOrigen, double longitudOrigen, double latitudDestino, double longitudDestino) {
        double lat1 = Math.toRadians(latitudOrigen);
        double lon1 = Math.toRadians(longitudOrigen);
        double lat2 = Math.toRadians(latitudDestino);
        double lon2 = Math.toRadians(longitudDestino);
        double a = Math.pow(Math.sin((lat2 - lat1) / 2), 2)
            + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin((lon2 - lon1) / 2), 2);
        // Evita errores de dominio por redondeo en puntos antípodas.
        return 2 * RADIO_TIERRA_KM * Math.asin(Math.sqrt(Math.max(0.0, Math.min(1.0, a))));
    }
}
