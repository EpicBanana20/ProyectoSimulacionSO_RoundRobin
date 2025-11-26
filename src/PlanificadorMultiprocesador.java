/* File: PlanificadorMultiprocesador.java */
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlanificadorMultiprocesador {

    private final List<Procesador> cpus;
    private final Object tickLock = new Object();
    private volatile boolean relojEjecutando = false;
    private Thread hiloReloj = null;

    private final AdministradorMemoria memManager;
    private final List<Proceso> suspendidos = new ArrayList<>();

    public List<Procesador> getCpus() {
        return cpus;
    }

    public PlanificadorMultiprocesador(int numProcesadores, int quantum) {
        this(numProcesadores, quantum, 8 * 1024);
    }

    public PlanificadorMultiprocesador(int numProcesadores, int quantum, int ramTotalKB) {
        cpus = new ArrayList<>();
        this.memManager = new AdministradorMemoria(ramTotalKB);

        for (int i = 0; i < numProcesadores; i++) {
            cpus.add(new Procesador(i, quantum, tickLock));
        }

        for (Procesador cpu : cpus) {
            cpu.setPlanificador(this);
        }
    }

    public AdministradorMemoria getMemManager() {
        return memManager;
    }

    private boolean cpusIniciadas = false;
    private boolean pausado = false;
    private final Object pausaLock = new Object();

    public void iniciar() {
        if (!cpusIniciadas) {
            for (Procesador cpu : cpus) {
                cpu.start();         
            }
            cpusIniciadas = true;
        }

        if (hiloReloj == null) {
            relojEjecutando = true;

            hiloReloj = new Thread(() -> {
                System.out.println("Reloj global iniciando...");

                while (relojEjecutando) {
                    synchronized (pausaLock) {
                        while (pausado) {
                            try {
                                pausaLock.wait();
                            } catch (InterruptedException e) {
                                // ignorar
                            }
                        }
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        if (!relojEjecutando) break;
                    }

                    TiempoGlobal.tick();

                    // Intentar reactivar suspendidos en cada tick
                    intentarReactivarSuspendidos();

                    synchronized (tickLock) {
                        tickLock.notifyAll();
                    }
                }

                System.out.println("Reloj global detenido.");
            }, "Hilo-Reloj");

            hiloReloj.start();
        }
    }

    public void detener() {
        relojEjecutando = false;
        if (hiloReloj != null) {
            hiloReloj.interrupt();
        }

        synchronized (tickLock) {
            tickLock.notifyAll();
        }

        for (Procesador cpu : cpus) {
            cpu.detener();
        }

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

    public void agregarProceso(Proceso p) {
        int llegada = TiempoGlobal.get();
        p.setTiempoLlegada(llegada);

        boolean memOk = memManager.asignarBestFit(p);

        if (!memOk) {
            synchronized (suspendidos) {
                p.cambiarEstado(Proceso.Estado.SUSPENDIDO);
                suspendidos.add(p);
            }
            System.out.println("P" + p.getId() + " suspendido por falta de memoria (tam=" + p.getTamMemoriaKB() + "KB)");
            return;
        }

        Procesador cpuMenosCarga = cpus.get(0);
        for (Procesador cpu : cpus) {
            if (cpu.getCarga() < cpuMenosCarga.getCarga()) {
                cpuMenosCarga = cpu;
            }
        }

        cpuMenosCarga.agregarProceso(p);
    }

    public void procesoTerminado(Proceso p) {
        memManager.liberar(p);
        // No intentar reactivar aquí, se hace en el hilo del reloj
    }

    // Método separado que se llama desde el hilo del reloj
    private void intentarReactivarSuspendidos() {
        List<Proceso> porReactivar = new ArrayList<>();
        
        synchronized (suspendidos) {
            for (Proceso s : new ArrayList<>(suspendidos)) {
                boolean ok = memManager.asignarBestFit(s);
                if (ok) {
                    s.cambiarEstado(Proceso.Estado.LISTO);
                    porReactivar.add(s);
                    suspendidos.remove(s);
                    System.out.println("P" + s.getId() + " reactivado desde suspendidos (mem disponible)");
                }
            }
        }

        // Asignar fuera del bloque sincronizado de suspendidos
        for (Proceso s : porReactivar) {
            Procesador cpuMenosCarga = cpus.get(0);
            for (Procesador cpu : cpus) {
                if (cpu.getCarga() < cpuMenosCarga.getCarga()) {
                    cpuMenosCarga = cpu;
                }
            }
            cpuMenosCarga.agregarProceso(s);
        }
    }

    public Proceso intentarRobar(Procesador thief) {
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

    public List<Proceso> getProcesosActivos() {
        Set<Proceso> set = new HashSet<>();

        for (Procesador cpu : cpus) {
            Proceso actual = cpu.getProcesoActual();
            if (actual != null) set.add(actual);

            Map<Integer, java.util.List<Proceso>> snap = cpu.getColasSnapshot();
            for (java.util.List<Proceso> q : snap.values()) {
                set.addAll(q);
            }
        }

        synchronized (suspendidos) {
            set.addAll(suspendidos);
        }

        return new ArrayList<>(set);
    }

    public void agregarProcesoNuevo(String idStr, int prioridad, int llegada, int cpu, int memKB) {
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (Exception ex) {
            throw new IllegalArgumentException("El ID debe ser numérico.");
        }

        Proceso p = new Proceso(id, prioridad, llegada, cpu, memKB);
        agregarProceso(p);
    }

    public void pausar() {
        synchronized (pausaLock) {
            pausado = true;
        }
    }

    public void reanudar() {
        synchronized (pausaLock) {
            pausado = false;
            pausaLock.notifyAll();
        }
    }
}