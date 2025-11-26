/* File: RoundRobin.java */
import java.util.*;

public class RoundRobin {

    // Colas organizadas por prioridad (clave = prioridad, cola FIFO)
    private final NavigableMap<Integer, Queue<Proceso>> colasListos;
    private java.util.function.Consumer<Proceso> onFinishListener;

    private final int quantum;
    private int tiempoGlobal;

    private int cpuId = -1;

    // Para reportar cuántos ticks se avanzaron en la última llamada
    private int ultimoTicksAvanzados = 0;
    private int ultimoTicksTrabajados = 0;

    public RoundRobin(int quantum) {
        this.quantum = quantum;
        this.colasListos = new TreeMap<>(); // orden ascendente de claves
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

    private synchronized Proceso obtenerSiguienteProceso() {
        for (Queue<Proceso> cola : colasListos.values()) {
            if (!cola.isEmpty())
                return cola.poll();
        }
        return null;
    }

    private synchronized boolean hayProcesosPendientes() {
        return colasListos.values().stream().anyMatch(q -> !q.isEmpty());
    }

    public synchronized int getCantidadProcesos() {
        int total = 0;
        for (Queue<Proceso> q : colasListos.values())
            total += q.size();
        return total;
    }

    // Ejecuta hasta un quantum (o menos si proceso termina).
    // Retorna un arreglo [ticksAvanzados, ticksTrabajados]
    public int[] ejecutarTick() {
        // reset de contadores de la llamada
        ultimoTicksAvanzados = 0;
        ultimoTicksTrabajados = 0;

        Proceso actual = null;

        synchronized (this) {
            actual = obtenerSiguienteProceso();
        }

        if (actual == null) {
            // sin trabajo: avanzamos tiempo global 1 tick y dormimos un poco
            try {
                Thread.sleep(1000); // 100 ms por tick (puedes ajustar)
                System.out.println("[CPU " + cpuId + "] Idle | TiempoGlobal=" + tiempoGlobal);
            } catch (InterruptedException ignored) {
            }
            tiempoGlobal++;
            ultimoTicksAvanzados = 1;
            return new int[]{ultimoTicksAvanzados, ultimoTicksTrabajados};
        }

        actual.cambiarEstado(Proceso.Estado.EJECUTANDO);

        if (actual.getTiempoInicio() == -1) {
            actual.setTiempoInicio(tiempoGlobal);
        }

        // Ejecutar hasta quantum ticks o hasta que termine
        int ticksHechosParaEsteProceso = 0;
        for (int i = 0; i < quantum; i++) {
            // consumir 1 unidad (un tick)
            actual.consumirCPU(1);
            ticksHechosParaEsteProceso++;
            ultimoTicksTrabajados++;
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
                break;
            }

            // dormir 1 tick real antes de siguiente unidad
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        }

        ultimoTicksAvanzados = ticksHechosParaEsteProceso;

        // si NO terminó, volver a poner al final de su cola (misma prioridad)
        if (actual.getTiempoRestante() > 0) {
            actual.cambiarEstado(Proceso.Estado.LISTO);
            synchronized (this) {
                colasListos.putIfAbsent(actual.getPrioridad(), new LinkedList<>());
                colasListos.get(actual.getPrioridad()).add(actual);
            }
        }

        return new int[]{ultimoTicksAvanzados, ultimoTicksTrabajados};
    }

    // getter del tiempo global si lo necesitas
    public int getTiempoGlobal() {
        return tiempoGlobal;
    }

    public void setOnFinishListener(java.util.function.Consumer<Proceso> listener) {
        this.onFinishListener = listener;
    }

    // accesores para los contadores del último tick (opcional)
    public int getUltimoTicksAvanzados() {
        return ultimoTicksAvanzados;
    }

    public int getUltimoTicksTrabajados() {
        return ultimoTicksTrabajados;
    }
}
