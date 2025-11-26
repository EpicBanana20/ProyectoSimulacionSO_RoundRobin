/* File: RoundRobin.java */
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

    // Agregar proceso (thread-safe)
    public synchronized void agregarProceso(Proceso p) {
        colasListos.putIfAbsent(p.getPrioridad(), new LinkedList<>());
        p.cambiarEstado(Proceso.Estado.LISTO);
        colasListos.get(p.getPrioridad()).add(p);
    }

    // Devuelve la cantidad total de procesos en las colas + el actual (si existe)
    public synchronized int getCantidadProcesos() {
        int total = (actual != null && actual.getEstado() != Proceso.Estado.TERMINADO ? 1 : 0);
        for (Queue<Proceso> q : colasListos.values()) total += q.size();
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
}
