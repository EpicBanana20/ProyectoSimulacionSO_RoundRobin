import java.util.*;

public class PlanificadorMultiprocesador {

    private List<Procesador> cpus;

    public PlanificadorMultiprocesador(int numProcesadores, int quantum) {
        cpus = new ArrayList<>();

        for (int i = 0; i < numProcesadores; i++) {
            cpus.add(new Procesador(i, quantum));
        }
    }

    // Reparto simple: al CPU con menos procesos en cola
    public void agregarProceso(Proceso p) {
        Procesador cpuMenosCarga = cpus.get(0);

        for (Procesador cpu : cpus) {
            if (cpu.getState() == Thread.State.NEW) {
                // todavía no ha iniciado, puedes agregar libremente
                cpuMenosCarga = cpu;
            }
        }

        cpuMenosCarga.agregarProceso(p);
    }

    // Ejecutar los CPUs en paralelo
    public List<Proceso> ejecutar() {
        List<Proceso> result = new ArrayList<>();

        // Iniciar hilos
        for (Procesador cpu : cpus) {
            cpu.start();
        }

        // Esperar a que los hilos terminen
        for (Procesador cpu : cpus) {
            try {
                cpu.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Recolectar terminados de cada CPU
        for (Procesador cpu : cpus) {
            result.addAll(cpu.getTerminados());
        }

        return result;
    }
}
