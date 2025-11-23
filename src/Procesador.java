import java.util.List;

public class Procesador {

    private int id;
    private RoundRobin rr;

    public Procesador(int id, int quantum) {
        this.id = id;
        this.rr = new RoundRobin(quantum);
    }

    public int getId() {
        return id;
    }

    public RoundRobin getRoundRobin() {
        return rr;
    }

    public void agregarProceso(Proceso p) {
        rr.agregarProceso(p);
    }

    public List<Proceso> ejecutar() {
        return rr.ejecutar();
    }

    public int getTiempoGlobal() {
        return rr.getTiempoGlobal();
    }
}
