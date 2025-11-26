/* File: Main.java */
import java.util.List;
import java.util.Scanner;
import javax.swing.SwingUtilities;

public class Main {

    public static int parseMemKB(String token) {
        if (token == null) return 0;
        token = token.trim().toLowerCase();
        try {
            if (token.endsWith("kb")) {
                return Integer.parseInt(token.substring(0, token.length() - 2).trim());
            } else if (token.endsWith("mb")) {
                int mb = Integer.parseInt(token.substring(0, token.length() - 2).trim());
                return mb * 1024;
            } else {
                // asumimos número en KB
                return Integer.parseInt(token);
            }
        } catch (Exception e) {
            return 0;
        }
    }

    public static void main(String[] args) {

        // Reiniciar tiempo global (por si se ejecutó antes en la misma JVM)
        TiempoGlobal.reset();

        // ejemplo: 6 CPUs, quantum 4, RAM 16MB (opc)
        PlanificadorMultiprocesador plan = new PlanificadorMultiprocesador(3, 4, 1 * 1024);

        plan.iniciar(); // arranca CPUs + reloj

        // Lanzar GUI en Swing (ventana aparte)
        SwingUtilities.invokeLater(() -> {
            new InterfaSim(plan);
        });

        Scanner sc = new Scanner(System.in);
        int id = 1;

        System.out.println("=== SIMULADOR INICIADO ===");
        System.out.println("Comandos:");
        System.out.println("  add <prioridad> <tiempoCPU> [mem]   -> agrega proceso (mem opcional: 512, 2mb, 256kb)");
        System.out.println("  ENTER (línea vacía)                 -> salir");

        while (true) {
            String linea = sc.nextLine();

            // salir si línea vacía
            if (linea == null || linea.trim().equals("")) {
                System.out.println("Cerrando simulador...");
                plan.detener();
                break;
            }

            // agregar proceso
            if (linea.trim().toLowerCase().startsWith("add")) {
                try {
                    String[] p = linea.trim().split("\\s+");
                    if (p.length < 3)
                        throw new IllegalArgumentException();

                    int prioridad = Integer.parseInt(p[1]);
                    int cpuTime = Integer.parseInt(p[2]);

                    int memKB = 0;
                    if (p.length >= 4) {
                        memKB = parseMemKB(p[3]);
                    }

                    Proceso nuevo = new Proceso(id++, prioridad, -1, cpuTime, memKB); // llegada la fijará el planificador
                    plan.agregarProceso(nuevo);

                    System.out.println("Proceso agregado: P" + nuevo.getId() +
                            " (prio=" + prioridad + ", cpu=" + cpuTime +
                            ", mem=" + memKB + "KB, llegada=" + nuevo.getTiempoLlegada() + ")");
                } catch (Exception e) {
                    System.out.println("Formato inválido. Usa: add prioridad tiempoCPU [mem]");
                }
            } else {
                System.out.println("Comando desconocido. Usa 'add' o presiona ENTER para salir.");
            }
        }

        // ------------------------------------------------------------
        // ESTADÍSTICAS FINALES
        // ------------------------------------------------------------

        System.out.println("\n=== ESTADÍSTICAS DE PROCESOS ===");

        List<Proceso> terminados = plan.getTodosTerminados();
        terminados.sort((a, b) -> Integer.compare(a.getId(), b.getId()));

        for (Proceso p : terminados) {
            System.out.println(
                    "P" + p.getId() +
                            " | Resp=" + p.getTiempoRespuesta() +
                            " | Espera=" + p.getTiempoEspera() +
                            " | Retorno=" + p.getTiempoRetorno() +
                            " | Llegada=" + p.getTiempoLlegada() +
                            " | Inicio=" + p.getTiempoInicio() +
                            " | Fin=" + p.getTiempoFin() +
                            " | Mem=" + p.getTamMemoriaKB() + "KB"
            );
        }

        System.out.println("\n=== USO DEL CPU ===");

        int i = 0;
        for (Procesador cpu : plan.getCpus()) {
            long usados = cpu.getTicksEjecutados();
            long total = cpu.getTicksTotales();

            double uso = (total == 0 ? 0 : (100.0 * usados / total));

            System.out.printf("CPU %d: %.2f%% (trabajo=%d, ticks=%d)\n",
                    i++, uso, usados, total);
        }

        double sumaUsos = 0;
        int cantidadCPUs = plan.getCpus().size();

        for (Procesador cpu : plan.getCpus()) {
            long usados = cpu.getTicksEjecutados();
            long total = cpu.getTicksTotales();
            double uso = (total == 0 ? 0 : (100.0 * usados / total));
            sumaUsos += uso;
        }

        double usoPromedio = (cantidadCPUs == 0 ? 0 : sumaUsos / cantidadCPUs);

        System.out.printf("\nUso promedio del CPU: %.2f%%\n", usoPromedio);

        System.out.println("\nPrograma finalizado.");
    }
}
