package pe.tecsup.chambape.usuarios;

/** Acceso de prueba al chequeo de contraseña (es del paquete usuarios). */
public final class VincularCuentaControllerAcceso {
    private VincularCuentaControllerAcceso() {
    }

    public static boolean contrasena(String clave, String hash) {
        return VincularCuentaController.contrasenaDjango(clave, hash);
    }
}
