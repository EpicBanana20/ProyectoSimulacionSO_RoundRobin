import java.util.ArrayList;
import java.util.List;

public class Procesador extends Thread {

    private int id;
    private RoundRobin rr;
    private List<Proceso> terminados;

    public Procesador(int id, int quantum) {
        this.id = id;
        this.rr = new RoundRobin(quantum);
        this.terminados = new ArrayList<>();
    }

    public void agregarProceso(Proceso p) {
        rr.agregarProceso(p);
    }

    public List<Proceso> getTerminados() {
        return terminados;
    }

    @Override
    public void run() {
        System.out.println("CPU " + id + " iniciando...");

        // El RR corre dentro del hilo
        terminados = rr.ejecutar();

        System.out.println("CPU " + id + " terminó ejecución.");
    }

    public int getCarga() {
        return rr.getCantidadProcesos();
    }
}
