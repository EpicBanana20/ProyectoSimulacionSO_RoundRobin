/* File: Procesador.java */
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Procesador extends Thread {

    private final int id;
    private final RoundRobin rr;
    private volatile boolean ejecutando = true;
    private final List<Proceso> terminados = new ArrayList<>();

    // Estadísticas de ticks
    private long ticksEjecutados = 0; // cuántos ticks con trabajo
    private long ticksTotales = 0;    // cuántos ticks totales (incluye idle)

    // Lock compartido por el planificador/clock para sincronización de ticks
    private final Object tickLock;

    public Procesador(int id, int quantum, Object tickLock) {
        this.id = id;
        this.rr = new RoundRobin(quantum);
        this.tickLock = tickLock;

        // cuando un proceso termina, lo guardamos en la lista terminados
        rr.setOnFinishListener(proceso -> {
            synchronized (terminados) {
                terminados.add(proceso);
            }
        });
    }

    // delega al RoundRobin (thread-safe)
    public void agregarProceso(Proceso p) {
        rr.agregarProceso(p);
    }

    public int getCarga() {
        return rr.getCantidadProcesos();
    }

    public void detener() {
        ejecutando = false;
        this.interrupt();
        // despertamos por si está esperando
        synchronized (tickLock) {
            tickLock.notifyAll();
        }
    }

    public List<Proceso> getTerminados() {
        synchronized (terminados) {
            return new ArrayList<>(terminados);
        }
    }

    public long getTicksEjecutados() {
        return ticksEjecutados;
    }

    public long getTicksTotales() {
        return ticksTotales;
    }

    // Exponer tiempo global actual para que el planificador fije la llegada en el mismo reloj
    public int getTiempoGlobal() {
        return TiempoGlobal.get();
    }

    @Override
    public void run() {
        System.out.println("CPU " + id + " iniciando...");
        while (ejecutando) {
            // Esperar al siguiente tick global
            synchronized (tickLock) {
                try {
                    tickLock.wait(); // despertado por el reloj global cada tick
                } catch (InterruptedException e) {
                    if (!ejecutando) break;
                    // si fue interrupción, continuar esperando el tick o terminar
                }
            }

            if (!ejecutando) break;

            // En cada tick global ejecutamos exactamente 1 unidad del RR
            boolean hizoTrabajo = rr.ejecutarUnTick();

            ticksTotales++;
            if (hizoTrabajo) ticksEjecutados++;
        }
        System.out.println("CPU " + id + " detenido.");
    }

    // -----------------------
    // Métodos para la GUI
    // -----------------------

    // Retorna snapshot de las colas del RoundRobin de este procesador
    public Map<Integer, java.util.List<Proceso>> getColasSnapshot() {
        return rr.getColasSnapshot();
    }

    // Retorna el proceso que está ejecutando actualmente (puede ser null)
    public Proceso getProcesoActual() {
        return rr.getProcesoActual();
    }

    // Retorna ticks consumidos en el quantum actual (útil para mostrar)
    public int getTicksEnQuantum() {
        return rr.getTicksEnQuantum();
    }
}
