import java.util.*;

public class RoundRobin {

    // Colas organizadas por prioridad
    private Map<Integer, Queue<Proceso>> colasListos;
    private Map<Integer, Queue<Proceso>> colasSuspendidos;

    private int quantum;
    private int tiempoGlobal;

    // Identificador del CPU que usa este RoundRobin
    private int cpuId = -1;

    public RoundRobin(int quantum) {
        this.quantum = quantum;
        this.colasListos = new TreeMap<>();
        this.colasSuspendidos = new TreeMap<>();
        this.tiempoGlobal = 0;
    }

    public void setCpuId(int id) {
        this.cpuId = id;
    }

    public void agregarProceso(Proceso p) {
        colasListos.putIfAbsent(p.getPrioridad(), new LinkedList<>());
        p.cambiarEstado(Proceso.Estado.LISTO);
        colasListos.get(p.getPrioridad()).add(p);
    }

    private Proceso obtenerSiguienteProceso() {
        for (Queue<Proceso> cola : colasListos.values()) {
            if (!cola.isEmpty()) return cola.poll();
        }
        return null;
    }

    private boolean hayProcesosPendientes() {
        return colasListos.values().stream().anyMatch(q -> !q.isEmpty()) ||
               colasSuspendidos.values().stream().anyMatch(q -> !q.isEmpty());
    }

    private boolean todasLasColasListasVacias() {
        return colasListos.values().stream().allMatch(Queue::isEmpty);
    }

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

    public List<Proceso> ejecutar() {

        List<Proceso> terminados = new ArrayList<>();

        while (hayProcesosPendientes()) {

            if (todasLasColasListasVacias()) {
                System.out.println("\n=== Todos agotaron quantum → Nuevo ciclo ===\n");
                reanudarSuspendidos();
            }

            Proceso actual = obtenerSiguienteProceso();
            if (actual == null) continue;

            actual.cambiarEstado(Proceso.Estado.EJECUTANDO);

            if (actual.getTiempoInicio() == -1) {
                actual.setTiempoInicio(tiempoGlobal);
            }

            int ciclos = Math.min(quantum, actual.getTiempoRestante());

            for (int i = 0; i < ciclos; i++) {

                actual.consumirCPU(1);
                tiempoGlobal += 1;

                System.out.println(
                    "[CPU " + cpuId + "] Ejecutando P" + actual.getId() +
                    " | Restante=" + actual.getTiempoRestante() +
                    " | TiempoGlobal=" + tiempoGlobal
                );

                try { Thread.sleep(500); } 
                catch (InterruptedException e) { e.printStackTrace(); }
            }

            if (actual.getTiempoRestante() == 0) {
                actual.setTiempoFin(tiempoGlobal);
                actual.cambiarEstado(Proceso.Estado.TERMINADO);
                terminados.add(actual);
            } else {
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

    public int getCantidadProcesos() {
        int total = 0;
        for (Queue<Proceso> q : colasListos.values()) total += q.size();
        for (Queue<Proceso> q : colasSuspendidos.values()) total += q.size();
        return total;
    }
}
