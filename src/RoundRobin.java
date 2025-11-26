import java.util.*;

public class RoundRobin {

    // Colas organizadas por prioridad
    private final Map<Integer, Queue<Proceso>> colasListos;
    private final Map<Integer, Queue<Proceso>> colasSuspendidos;
    private java.util.function.Consumer<Proceso> onFinishListener;
    private boolean trabajoEsteTick = false;

    private final int quantum;
    private int tiempoGlobal;

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

    // thread-safe: varios hilos pueden agregar procesos
    public synchronized void agregarProceso(Proceso p) {
        colasListos.putIfAbsent(p.getPrioridad(), new LinkedList<>());
        p.cambiarEstado(Proceso.Estado.LISTO);
        colasListos.get(p.getPrioridad()).add(p);
    }

    private Proceso obtenerSiguienteProceso() {
        for (Queue<Proceso> cola : colasListos.values()) {
            if (!cola.isEmpty())
                return cola.poll();
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

    // Ejecuta UN TICK (una unidad de trabajo) — diseñado para llamarse
    // repetidamente por el hilo del CPU
    public void ejecutarTick() {

        Proceso actual = null;

        // sincronizar el acceso a colas para evitar race conditions
        synchronized (this) {
            if (todasLasColasListasVacias()) {
                // si no hay listos pero hay suspendidos, reanudar
                if (!colasSuspendidos.isEmpty() && colasSuspendidos.values().stream().anyMatch(q -> !q.isEmpty())) {
                    reanudarSuspendidos();
                }
            }

            actual = obtenerSiguienteProceso();
        }

        if (actual == null) {
            // sin trabajo: avanzamos tiempo global y dormimos un poco
            try {
                Thread.sleep(1000);
                System.out.println("[CPU " + cpuId + "] Idle | TiempoGlobal=" + tiempoGlobal);
            } catch (InterruptedException ignored) {
            }
            tiempoGlobal++;
            return;
        }

        actual.cambiarEstado(Proceso.Estado.EJECUTANDO);

        if (actual.getTiempoInicio() == -1) {
            actual.setTiempoInicio(tiempoGlobal);
        }

        // consumir 1 unidad (un tick)
        actual.consumirCPU(1);
        trabajoEsteTick = true;
        tiempoGlobal++;

        System.out.println("[CPU " + cpuId + "] Ejecutando P" + actual.getId() +
                " | Restante=" + actual.getTiempoRestante() +
                " | TiempoGlobal=" + tiempoGlobal);

        // si terminó, marcar terminado
        if (actual.getTiempoRestante() == 0) {
            actual.setTiempoFin(tiempoGlobal);
            actual.cambiarEstado(Proceso.Estado.TERMINADO);
            // terminado fuera de las colas (no reinsertamos)
            if (onFinishListener != null) {
                onFinishListener.accept(actual);
            }
        } else {
            // si no termina, lo suspendemos (vuelve a la cola suspendidos)
            actual.cambiarEstado(Proceso.Estado.SUSPENDIDO);
            synchronized (this) {
                colasSuspendidos.putIfAbsent(actual.getPrioridad(), new LinkedList<>());
                colasSuspendidos.get(actual.getPrioridad()).add(actual);
            }
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }
    }

    // retorna la cantidad de procesos que están en colas (listos + suspendidos)
    public synchronized int getCantidadProcesos() {
        int total = 0;
        for (Queue<Proceso> q : colasListos.values())
            total += q.size();
        for (Queue<Proceso> q : colasSuspendidos.values())
            total += q.size();
        return total;
    }

    // getter del tiempo global si lo necesitas
    public int getTiempoGlobal() {
        return tiempoGlobal;
    }

    public void setOnFinishListener(java.util.function.Consumer<Proceso> listener) {
        this.onFinishListener = listener;
    }

    public boolean hizoTrabajoEnEsteTick() {
        return trabajoEsteTick;
    }
}
