import java.util.*;

public class RoundRobin {

    // Colas organizadas por prioridad (TreeMap ordena 1,2,3,... automáticamente)
    private Map<Integer, Queue<Proceso>> colasListos;
    private Map<Integer, Queue<Proceso>> colasSuspendidos;

    private int quantum;
    private int tiempoGlobal;

    public RoundRobin(int quantum) {
        this.quantum = quantum;

        // Una cola por nivel de prioridad
        this.colasListos = new TreeMap<>();
        this.colasSuspendidos = new TreeMap<>();

        this.tiempoGlobal = 0;
    }

    // ====================================================
    //  Agregar proceso a su cola según prioridad
    // ====================================================
    public void agregarProceso(Proceso p) {
        colasListos.putIfAbsent(p.getPrioridad(), new LinkedList<>());
        p.cambiarEstado(Proceso.Estado.LISTO);
        colasListos.get(p.getPrioridad()).add(p);
    }

    // ====================================================
    //  Obtener siguiente proceso según prioridad
    // ====================================================
    private Proceso obtenerSiguienteProceso() {
        for (Queue<Proceso> cola : colasListos.values()) {
            if (!cola.isEmpty()) {
                return cola.poll();
            }
        }
        return null;
    }

    // ====================================================
    //  ¿Hay procesos en cualquier cola?
    // ====================================================
    private boolean hayProcesosPendientes() {
        boolean hayListos = colasListos.values().stream().anyMatch(q -> !q.isEmpty());
        boolean haySuspendidos = colasSuspendidos.values().stream().anyMatch(q -> !q.isEmpty());
        return hayListos || haySuspendidos;
    }

    // ====================================================
    //  ¿Todas las colas LISTO están vacías?
    // ====================================================
    private boolean todasLasColasListasVacias() {
        return colasListos.values().stream().allMatch(Queue::isEmpty);
    }

    // ====================================================
    //  Reanudar procesos suspendidos (siguen en su prioridad)
    // ====================================================
    private void reanudarSuspendidos() {
        for (Map.Entry<Integer, Queue<Proceso>> entry : colasSuspendidos.entrySet()) {
            int prioridad = entry.getKey();
            Queue<Proceso> colaSusp = entry.getValue();

            colasListos.putIfAbsent(prioridad, new LinkedList<>());

            while (!colaSusp.isEmpty()) {
                Proceso p = colaSusp.poll();
                p.cambiarEstado(Proceso.Estado.LISTO);
                colasListos.get(prioridad).add(p);
            }
        }
    }

    // ====================================================
    //  EJECUTAR Round Robin con Prioridad
    // ====================================================
    public List<Proceso> ejecutar() {

        List<Proceso> terminados = new ArrayList<>();

        while (hayProcesosPendientes()) {

            // Si no hay listos → pasar todos suspendidos a listos
            if (todasLasColasListasVacias()) {
                System.out.println("\n=== Todos agotaron quantum → Nuevo ciclo ===\n");
                reanudarSuspendidos();
            }

            Proceso actual = obtenerSiguienteProceso();
            if (actual == null) continue;

            actual.cambiarEstado(Proceso.Estado.EJECUTANDO);

            // Primera vez que corre
            if (actual.getTiempoInicio() == -1) {
                actual.setTiempoInicio(tiempoGlobal);
            }

            // Ejecutar por quantum o por lo que reste
            int tiempoEjecutado = Math.min(quantum, actual.getTiempoRestante());
            actual.consumirCPU(tiempoEjecutado);

            tiempoGlobal += tiempoEjecutado;

            // Proceso terminó
            if (actual.getTiempoRestante() == 0) {
                actual.setTiempoFin(tiempoGlobal);
                actual.cambiarEstado(Proceso.Estado.TERMINADO);
                terminados.add(actual);

            } else {
                // No terminó → suspender en su misma prioridad
                actual.cambiarEstado(Proceso.Estado.SUSPENDIDO);
                colasSuspendidos.putIfAbsent(actual.getPrioridad(), new LinkedList<>());
                colasSuspendidos.get(actual.getPrioridad()).add(actual);
            }
        }

        return terminados;
    }

    public int getTiempoGlobal() {
        return tiempoGlobal;
    }

    
}
