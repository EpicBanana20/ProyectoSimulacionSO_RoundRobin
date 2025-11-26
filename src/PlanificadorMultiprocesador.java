/* File: PlanificadorMultiprocesador.java */
import java.util.ArrayList;
import java.util.List;

public class PlanificadorMultiprocesador {

    private final List<Procesador> cpus;
    private final Object tickLock = new Object(); // lock compartido para ticks
    private volatile boolean relojEjecutando = false;
    private Thread hiloReloj = null;

    public List<Procesador> getCpus() {
        return cpus;
    }

    public PlanificadorMultiprocesador(int numProcesadores, int quantum) {
        cpus = new ArrayList<>();

        // crear CPUs
        for (int i = 0; i < numProcesadores; i++) {
            cpus.add(new Procesador(i, quantum, tickLock));
        }

        // establecer referencia al planificador en cada CPU (para permitir robo)
        for (Procesador cpu : cpus) {
            cpu.setPlanificador(this);
        }
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
        Procesador cpuMenosCarga = cpus.get(0);
        for (Procesador cpu : cpus) {
            if (cpu.getCarga() < cpuMenosCarga.getCarga()) {
                cpuMenosCarga = cpu;
            }
        }

        // asignar tiempo de llegada REAL usando el TiempoGlobal actual (mismo para todos)
        int llegada = TiempoGlobal.get();
        p.setTiempoLlegada(llegada);

        cpuMenosCarga.agregarProceso(p);
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
        Proceso p = origen.getColasSnapshot().isEmpty() ? null : origen.rr.extraerProcesoParaRobo();
        // nota: usamos método package-private/accessible porque estamos en el mismo paquete;
        // si necesitas encapsulación más estricta, lo exponemos con getter en Procesador.
        return p;
    }

    public List<Proceso> getTodosTerminados() {
        List<Proceso> r = new ArrayList<>();
        for (Procesador cpu : cpus) {
            r.addAll(cpu.getTerminados());
        }
        return r;
    }
}
