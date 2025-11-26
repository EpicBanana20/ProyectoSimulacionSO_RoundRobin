/* File: PlanificadorMultiprocesador.java */
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlanificadorMultiprocesador {

    private final List<Procesador> cpus;
    private final Object tickLock = new Object(); // lock compartido para ticks
    private volatile boolean relojEjecutando = false;
    private Thread hiloReloj = null;

    // memoria
    private final AdministradorMemoria memManager;
    // procesos que no pudieron entrar por falta de espacio contiguo
    private final List<Proceso> suspendidos = new ArrayList<>();

    public List<Procesador> getCpus() {
        return cpus;
    }

    // constructor: num CPUs, quantum, y RAM por defecto 8192 KB (8 MB)
    public PlanificadorMultiprocesador(int numProcesadores, int quantum) {
        this(numProcesadores, quantum, 8 * 1024);
    }

    public PlanificadorMultiprocesador(int numProcesadores, int quantum, int ramTotalKB) {
        cpus = new ArrayList<>();
        this.memManager = new AdministradorMemoria(ramTotalKB);

        // crear CPUs
        for (int i = 0; i < numProcesadores; i++) {
            cpus.add(new Procesador(i, quantum, tickLock));
        }

        // establecer referencia al planificador en cada CPU (para permitir robo)
        for (Procesador cpu : cpus) {
            cpu.setPlanificador(this);
        }
    }

    public AdministradorMemoria getMemManager() {
        return memManager;
    }

    public void iniciar() {
        // iniciar CPUs
        for (Procesador cpu : cpus) {
            cpu.start();
        }

        // iniciar reloj global (un único hilo que avanza el tiempo y notifica a todos)
        relojEjecutando = true;
        hiloReloj = new Thread(() -> {
            System.out.println("Reloj global iniciando...");
            while (relojEjecutando) {
                try {
                    Thread.sleep(1000); // duración de un tick (1s). Ajusta si quieres más rápido.
                } catch (InterruptedException e) {
                    if (!relojEjecutando) break;
                }

                // Avanzar tiempo global una vez por tick y notificar a los CPUs
                TiempoGlobal.tick();

                synchronized (tickLock) {
                    tickLock.notifyAll();
                }
            }
            System.out.println("Reloj global detenido.");
        }, "Hilo-Reloj");
        hiloReloj.start();
    }

    public void detener() {
        // detener reloj primero
        relojEjecutando = false;
        if (hiloReloj != null) {
            hiloReloj.interrupt();
        }

        // notificar para despertar a CPUs y que puedan terminar
        synchronized (tickLock) {
            tickLock.notifyAll();
        }

        // detener CPUs
        for (Procesador cpu : cpus) {
            cpu.detener();
        }

        // esperar que terminen (opcional)
        for (Procesador cpu : cpus) {
            try {
                cpu.join(2000);
            } catch (InterruptedException ignored) {
            }
        }
        if (hiloReloj != null) {
            try {
                hiloReloj.join(1000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    // Reparto REAL: CPU con menos carga total (incluye procesos en colas y posible actual)
    public void agregarProceso(Proceso p) {
        // fijar llegada
        int llegada = TiempoGlobal.get();
        p.setTiempoLlegada(llegada);

        // intentar asignar memoria (Best Fit)
        boolean memOk = memManager.asignarBestFit(p);

        if (!memOk) {
            // suspender por falta de memoria contigua
            synchronized (suspendidos) {
                p.cambiarEstado(Proceso.Estado.SUSPENDIDO);
                suspendidos.add(p);
            }
            System.out.println("P" + p.getId() + " suspendido por falta de memoria (tam=" + p.getTamMemoriaKB() + "KB)");
            return;
        }

        // asignar a CPU con menos carga
        Procesador cpuMenosCarga = cpus.get(0);
        for (Procesador cpu : cpus) {
            if (cpu.getCarga() < cpuMenosCarga.getCarga()) {
                cpuMenosCarga = cpu;
            }
        }

        cpuMenosCarga.agregarProceso(p);
    }

    // llamado por Procesador cuando un proceso termina para liberar memoria
    public void procesoTerminado(Proceso p) {
        // liberar memoria
        memManager.liberar(p);

        // intentar cargar suspendidos (FIFO)
        synchronized (suspendidos) {
            List<Proceso> porCargar = new ArrayList<>(suspendidos);
            for (Proceso s : porCargar) {
                boolean ok = memManager.asignarBestFit(s);
                if (ok) {
                    s.cambiarEstado(Proceso.Estado.LISTO);
                    // asignar a CPU menos cargado
                    Procesador cpuMenosCarga = cpus.get(0);
                    for (Procesador cpu : cpus) {
                        if (cpu.getCarga() < cpuMenosCarga.getCarga()) {
                            cpuMenosCarga = cpu;
                        }
                    }
                    cpuMenosCarga.agregarProceso(s);
                    suspendidos.remove(s);
                    System.out.println("P" + s.getId() + " reactivado desde suspendidos (mem disponible)");
                }
            }
        }
    }

    // --- WORK STEALING: intentar robar para el CPU 'thief' ---
    public Proceso intentarRobar(Procesador thief) {
        // elegir CPU más cargado distinto de thief
        Procesador origen = null;
        int maxCarga = 0;
        for (Procesador cpu : cpus) {
            if (cpu == thief) continue;
            int carga = cpu.getCarga();
            if (carga > maxCarga) {
                maxCarga = carga;
                origen = cpu;
            }
        }

        if (origen == null || maxCarga == 0) return null;

        // pedir a la RR del origen que extraiga un proceso para robo
        Proceso p = origen.rr.extraerProcesoParaRobo();
        return p;
    }

    public List<Proceso> getTodosTerminados() {
        List<Proceso> r = new ArrayList<>();
        for (Procesador cpu : cpus) {
            r.addAll(cpu.getTerminados());
        }
        return r;
    }

    /**
     * Devuelve una lista con los procesos "activos" (listos o ejecutando)
     * encontrados recorriendo los CPUs y sus colas.
     */
    public List<Proceso> getProcesosActivos() {
        Set<Proceso> set = new HashSet<>();

        for (Procesador cpu : cpus) {
            // proceso actual
            Proceso actual = cpu.getProcesoActual();
            if (actual != null) set.add(actual);

            // colas snapshot
            Map<Integer, java.util.List<Proceso>> snap = cpu.getColasSnapshot();
            for (java.util.List<Proceso> q : snap.values()) {
                set.addAll(q);
            }
        }

        // también incluir suspendidos (opcional)
        synchronized (suspendidos) {
            set.addAll(suspendidos);
        }

        return new ArrayList<>(set);
    }
}
