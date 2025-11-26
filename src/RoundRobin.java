import java.util.*;

public class RoundRobin {

    // Multicolas por prioridad (clave: prioridad → cola FIFO)
    private final NavigableMap<Integer, Queue<Proceso>> colasListos;
    private final int quantum;

    // Proceso actualmente en ejecución (si lo hay) y ticks que ya le dimos en su quantum
    private Proceso actual = null;
    private int ticksEnQuantum = 0;

    private java.util.function.Consumer<Proceso> onFinishListener;

    public RoundRobin(int quantum) {
        this.quantum = quantum;
        this.colasListos = new TreeMap<>(); // orden ascendente de prioridad numérica
    }

    // Agregar proceso 
    public synchronized void agregarProceso(Proceso p) {
        colasListos.putIfAbsent(p.getPrioridad(), new LinkedList<>());
        p.cambiarEstado(Proceso.Estado.LISTO);
        colasListos.get(p.getPrioridad()).add(p);
        
    }

    // Devuelve la cantidad total de procesos en las colas + el actual si existe
    public synchronized int getCantidadProcesos() {
       int total = 0;

    // contar proceso actual si no ha terminado ni esta suspendido
    if (actual != null &&
        actual.getEstado() != Proceso.Estado.TERMINADO &&
        actual.getEstado() != Proceso.Estado.SUSPENDIDO) {
        total = 1;
    }

    // contar solo los procesos válidos en colas
    for (Queue<Proceso> q : colasListos.values()) {
        for (Proceso p : q) {
            if (p.getEstado() != Proceso.Estado.SUSPENDIDO &&
                p.getEstado() != Proceso.Estado.TERMINADO) {
                total++;
            }
        }
    }

    return total;
    }

    // Selecciona el siguiente proceso de las colas por prioridad (si actual == null)
    private synchronized void seleccionarSiguienteSiNecesario() {
        if (actual != null) return;
        for (Queue<Proceso> cola : colasListos.values()) {
            if (!cola.isEmpty()) {
                actual = cola.poll();
                ticksEnQuantum = 0;
                return;
            }
        }
    }

    // Ejecutar exactamente 1 tick para este RoundRobin (llamado por la CPU en cada tick global).
    // Retorna true si se hizo trabajo (se consumió 1 unidad CPU) o false si idle.
    public synchronized boolean ejecutarUnTick() {

        seleccionarSiguienteSiNecesario();

        if (actual == null) {
            // idle - nada que hacer
            return false;
        }

        // si es la primera vez que ejecuta, fijar tiempo inicio
        if (actual.getTiempoInicio() == -1) {
            actual.setTiempoInicio(TiempoGlobal.get());
        }

        // ejecutar 1 tick
        actual.consumirCPU(1);
        ticksEnQuantum++;

        actual.cambiarEstado(Proceso.Estado.EJECUTANDO);

        // si terminó
        if (actual.getTiempoRestante() == 0) {
            actual.setTiempoFin(TiempoGlobal.get());
            actual.cambiarEstado(Proceso.Estado.TERMINADO);
            // notificar terminado
            if (onFinishListener != null) {
                onFinishListener.accept(actual);
            }
            // limpiar actual
            actual = null;
            ticksEnQuantum = 0;
            return true;
        }

        // si alcanzó su quantum y no terminó, reinsertar al final de su cola
        if (ticksEnQuantum >= quantum) {
            actual.cambiarEstado(Proceso.Estado.LISTO);
            colasListos.putIfAbsent(actual.getPrioridad(), new LinkedList<>());
            colasListos.get(actual.getPrioridad()).add(actual);
            actual = null;
            ticksEnQuantum = 0;
        }

        return true;
    }

    public void setOnFinishListener(java.util.function.Consumer<Proceso> listener) {
        this.onFinishListener = listener;
    }

    // -----------------------
    // Métodos para la GUI
    // -----------------------

    // Devuelve un snapshot (copia) de las colas listo por prioridad
    public synchronized Map<Integer, List<Proceso>> getColasSnapshot() {
        Map<Integer, List<Proceso>> snap = new TreeMap<>();
        for (Map.Entry<Integer, Queue<Proceso>> e : colasListos.entrySet()) {
            snap.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return snap;
    }

    // Devuelve el proceso actual (puede ser null)
    public synchronized Proceso getProcesoActual() {
        return actual;
    }

    // Devuelve cuanto lleva ejecutado en el quantum actual (útil si quieres mostrar)
    public synchronized int getTicksEnQuantum() {
        return ticksEnQuantum;
    }

    // -----------------------
    // Work-stealing helpers
    // -----------------------

    // Extrae un proceso para que otro CPU lo robe.
    // Política: roba de la cola de menor prioridad (número mayor) primero.
    public synchronized Proceso extraerProcesoParaRobo() {
        // iterar prioridades en orden descendente (mayor número = prioridad más baja)
        for (Integer prio : colasListos.descendingKeySet()) {
            Queue<Proceso> q = colasListos.get(prio);
            if (q != null && !q.isEmpty()) {
                return q.poll();
            }
        }
        return null;
    }

    // Asigna inmediatamente un proceso robado como "actual" en este RR
    public synchronized void asignarProcesoRobado(Proceso p) {
        // si hay actual en ejecución, encolar (no debería pasar si llamamos desde idle)
        if (actual != null) {
            colasListos.putIfAbsent(p.getPrioridad(), new LinkedList<>());
            colasListos.get(p.getPrioridad()).add(p);
            return;
        }
        actual = p;
        ticksEnQuantum = 0;
        p.cambiarEstado(Proceso.Estado.LISTO); // aparecerá como ejecutable; se fijará inicio al ejecutar
    }
}
