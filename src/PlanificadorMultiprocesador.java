import java.util.*;

public class PlanificadorMultiprocesador {

    private List<Procesador> cpus;

    public PlanificadorMultiprocesador(int numProcesadores, int quantum) {
        cpus = new ArrayList<>();

        for (int i = 0; i < numProcesadores; i++) {
            cpus.add(new Procesador(i, quantum));
        }
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

    public List<Proceso> ejecutar() {
        List<Proceso> result = new ArrayList<>();

        for (Procesador cpu : cpus) cpu.start();

        for (Procesador cpu : cpus) {
            try { cpu.join(); } 
            catch (InterruptedException e) { e.printStackTrace(); }
        }

        for (Procesador cpu : cpus) {
            result.addAll(cpu.getTerminados());
        }

        return result;
    }
}
