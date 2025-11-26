/* File: Procesador.java */
import java.util.ArrayList;
import java.util.List;

public class Procesador extends Thread {

    private final int id;
    private final RoundRobin rr;
    private volatile boolean ejecutando = true;
    private final List<Proceso> terminados = new ArrayList<>();
    private long ticksEjecutados = 0;
    private long ticksTotales = 0;

    public Procesador(int id, int quantum) {
        this.id = id;
        this.rr = new RoundRobin(quantum);
        rr.setCpuId(id);

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
        this.interrupt(); // para despertar si está durmiendo
    }

    public List<Proceso> getTerminados() {
        return terminados;
    }

    public long getTicksEjecutados() {
        return ticksEjecutados;
    }

    public long getTicksTotales() {
        return ticksTotales;
    }

    @Override
    public void run() {
        System.out.println("CPU " + id + " iniciando...");
        while (ejecutando) {
            int[] resultado = rr.ejecutarTick(); // [ticksAvanzados, ticksTrabajados]
            int avanzados = resultado[0];
            int trabajados = resultado[1];

            ticksTotales += avanzados;
            ticksEjecutados += trabajados;
        }
        System.out.println("CPU " + id + " detenido.");
    }

    // Exponer tiempo global del RR para que el planificador asigne la llegada correcta
    public int getTiempoGlobal() {
        return rr.getTiempoGlobal();
    }
}
