/* File: Procesador.java */
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Procesador extends Thread {

    private final int id;
    public final RoundRobin rr;
    private volatile boolean ejecutando = true;
    private final List<Proceso> terminados = new ArrayList<>();

    // Estadísticas de ticks
    private long ticksEjecutados = 0; // cuántos ticks con trabajo
    private long ticksTotales = 0;    // cuántos ticks totales (incluye idle)

    // Lock compartido por el planificador/clock para sincronización de ticks
    private final Object tickLock;

    // Referencia al planificador para solicitar robo de trabajo y notificar fin
    private PlanificadorMultiprocesador planificador;

    public Procesador(int id, int quantum, Object tickLock) {
        this.id = id;
        this.rr = new RoundRobin(quantum);
        this.tickLock = tickLock;

        // no fijamos listener aquí porque el planificador puede necesitarse para liberar memoria
        // lo configuramos en setPlanificador(...) para que incluya ambas acciones.
    }

    // Setter para que el planificador establezca la referencia (se llama desde el planificador)
    public void setPlanificador(PlanificadorMultiprocesador plan) {
        this.planificador = plan;

        // establecer listener que guarda en terminados y notifica al planificador
        rr.setOnFinishListener(proceso -> {
            synchronized (terminados) {
                terminados.add(proceso);
            }
            if (planificador != null) {
                planificador.procesoTerminado(proceso);
            }
        });
    }

    // delega al RoundRobin (thread-safe)
    public void agregarProceso(Proceso p) {
        rr.agregarProceso(p);
    }

    // permite asignar un proceso robado inmediatamente
    public void asignarProcesoRobado(Proceso p) {
        rr.asignarProcesoRobado(p);
    }

    // Exponer carga (colas + actual)
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
                }
            }

            if (!ejecutando) break;

            // En cada tick global ejecutamos exactamente 1 unidad del RR
            boolean hizoTrabajo = rr.ejecutarUnTick();

            ticksTotales++;
            if (hizoTrabajo) ticksEjecutados++;

            // Si no hizo trabajo, intentar robar de otro CPU
            if (!hizoTrabajo && planificador != null) {
                Proceso robado = planificador.intentarRobar(this);
                if (robado != null) {
                    // asignar proceso robado para que se ejecute a partir del próximo tick
                    asignarProcesoRobado(robado);
                }
            }
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
