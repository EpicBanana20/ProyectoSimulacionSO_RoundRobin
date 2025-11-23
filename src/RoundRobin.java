import java.util.*;

public class RoundRobin {

    private Queue<Proceso> colaListos;
    private int quantum;
    private int tiempoGlobal;

    public RoundRobin(int quantum) {
        this.quantum = quantum;
        this.colaListos = new LinkedList<>();
        this.tiempoGlobal = 0;
    }

    // ==========================================
    //  Agregar procesos a la cola READY
    // ==========================================
    public void agregarProceso(Proceso p) {
        p.setEstado(Proceso.Estado.LISTO);
        colaListos.add(p);
    }

    // ==========================================
    //  Ejecución Round Robin (1 solo CPU por ahora)
    // ==========================================
    public List<Proceso> ejecutar() {

        List<Proceso> terminados = new ArrayList<>();

        while (!colaListos.isEmpty()) {

            Proceso actual = colaListos.poll();
            actual.setEstado(Proceso.Estado.EJECUTANDO);

            // Marcar tiempo de inicio si es la primera vez que ejecuta
            if (actual.getTiempoInicio() == -1) {
                actual.setTiempoInicio(tiempoGlobal);
            }

            // Simulación de ejecución por quantum
            int tiempoEjecutado = Math.min(quantum, actual.getTiempoRestante());
            actual.consumirCPU(tiempoEjecutado);

            // Avanza tiempo global
            tiempoGlobal += tiempoEjecutado;

            // ======== Proceso terminado ========
            if (actual.getTiempoRestante() == 0) {
                actual.setEstado(Proceso.Estado.TERMINADO);
                actual.setTiempoFin(tiempoGlobal);
                terminados.add(actual);
            }
            // ======== Todavía tiene CPU por consumir ========  
            else {
                // Regresa a cola de listos
                actual.setEstado(Proceso.Estado.LISTO);
                colaListos.add(actual);
            }
        }

        return terminados;
    }

    // Getter del tiempo global para métricas desde GUI
    public int getTiempoGlobal() {
        return tiempoGlobal;
    }
}


////
/// 
/// RoundRobinCPU cpu1 = new RoundRobinCPU(quantum);
// RoundRobinCPU cpu2 = new RoundRobinCPU(quantum);
