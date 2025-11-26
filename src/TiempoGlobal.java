public class TiempoGlobal {
    private static int tiempo = 0;

    // Devuelve el tiempo actual (sin incrementar)
    public static synchronized int get() {
        return tiempo;
    }

    // Incrementa el tiempo global en 1 tick y devuelve el nuevo tiempo
    public static synchronized int tick() {
        tiempo++;
        return tiempo;
    }

    // Reiniciar (útil para pruebas)
    public static synchronized void reset() {
        tiempo = 0;
    }
}
