import java.util.ArrayList;
import java.util.List;

public class AdministradorMemoria {

    public static class Bloque {
        public int inicio;
        public int tamano; 
        public Proceso proceso; // null si libre

        public Bloque(int inicio, int tamano, Proceso proceso) {
            this.inicio = inicio;
            this.tamano = tamano;
            this.proceso = proceso;
        }

        public boolean estaLibre() {
            return proceso == null;
        }

        @Override
        public String toString() {
            if (estaLibre()) {
                return String.format("[Libre | inicio=%dKB | tam=%dKB]", inicio, tamano);
            } else {
                return String.format("[P%d | inicio=%dKB | tam=%dKB]", proceso.getId(), inicio, tamano);
            }
        }
    }

    private final List<Bloque> bloques;
    private final int tamTotalKB;

    public AdministradorMemoria(int tamTotalKB) {
        this.tamTotalKB = tamTotalKB;
        this.bloques = new ArrayList<>();
        // toda la memoria como bloque libre
        this.bloques.add(new Bloque(0, tamTotalKB, null));
    }

    // intento de asignación best-fit; retorna true si se asignó
    public synchronized boolean asignarBestFit(Proceso p) {
        int need = p.getTamMemoriaKB();
        if (need <= 0) {
            // procesos sin requerimiento de memoria pasan sin ocupar
            return true;
        }

        Bloque mejor = null;
        for (Bloque b : bloques) {
            if (b.estaLibre() && b.tamano >= need) {
                if (mejor == null || b.tamano < mejor.tamano) {
                    mejor = b;
                }
            }
        }

        if (mejor == null) return false;

        int idx = bloques.indexOf(mejor);
        if (mejor.tamano == need) {
            mejor.proceso = p;
        } else {
            // dividir: ocupado al inicio, libre después
            Bloque ocupado = new Bloque(mejor.inicio, need, p);
            Bloque libre = new Bloque(mejor.inicio + need, mejor.tamano - need, null);
            // reemplazar
            bloques.set(idx, ocupado);
            bloques.add(idx + 1, libre);
        }

        return true;
    }

    // libera memoria ocupada por proceso p
    public synchronized void liberar(Proceso p) {
        if (p == null) return;
        for (Bloque b : bloques) {
            if (!b.estaLibre() && b.proceso == p) {
                b.proceso = null;
                break;
            }
        }
        fusionarHuecos();
    }

    // fusiona bloques libres contiguos
    private synchronized void fusionarHuecos() {
        for (int i = 0; i < bloques.size() - 1; i++) {
            Bloque a = bloques.get(i);
            Bloque b = bloques.get(i + 1);
            if (a.estaLibre() && b.estaLibre()) {
                a.tamano += b.tamano;
                bloques.remove(i + 1);
                i--; // revisar nuevamente en la misma posición
            }
        }
    }

    // snapshot para GUI (copia)
    public synchronized List<Bloque> getSnapshot() {
        return new ArrayList<>(bloques);
    }

    public int getTamTotalKB() {
        return tamTotalKB;
    }

    // helper: uso total actualmente ocupado (KB)
    public synchronized int getOcupadoKB() {
        int s = 0;
        for (Bloque b : bloques) {
            if (!b.estaLibre()) s += b.tamano;
        }
        return s;
    }
}
