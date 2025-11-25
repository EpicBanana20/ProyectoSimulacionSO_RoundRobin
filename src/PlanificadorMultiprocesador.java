import java.util.ArrayList;
import java.util.List;

public class PlanificadorMultiprocesador {

    private final List<Procesador> cpus;

    public PlanificadorMultiprocesador(int numProcesadores, int quantum) {
        cpus = new ArrayList<>();

        for (int i = 0; i < numProcesadores; i++) {
            cpus.add(new Procesador(i, quantum));
        }
    }

    public void iniciar() {
        for (Procesador cpu : cpus) cpu.start();
    }

    public void detener() {
        for (Procesador cpu : cpus) cpu.detener();
    }

    // Reparto REAL: CPU con menos carga total
    public void agregarProceso(Proceso p) {
        Procesador cpuMenosCarga = cpus.get(0);
        for (Procesador cpu : cpus) {
            if (cpu.getCarga() < cpuMenosCarga.getCarga()) {
                cpuMenosCarga = cpu;
            }
        }
        cpuMenosCarga.agregarProceso(p);
    }
}
