public class Procesador extends Thread {

    private final int id;
    private final RoundRobin rr;
    private volatile boolean ejecutando = true;

    public Procesador(int id, int quantum) {
        this.id = id;
        this.rr = new RoundRobin(quantum);
        rr.setCpuId(id);
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

    @Override
    public void run() {
        System.out.println("CPU " + id + " iniciando...");
        while (ejecutando) {
            rr.ejecutarTick();
        }
        System.out.println("CPU " + id + " detenido.");
    }
}
