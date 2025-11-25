import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        PlanificadorMultiprocesador plan =
                new PlanificadorMultiprocesador(2, 4); // 2 CPUs, quantum 4

        plan.iniciar();  // arranca CPUs en background

        Scanner sc = new Scanner(System.in);
        int id = 1;

        System.out.println("=== SIMULADOR INICIADO ===");
        System.out.println("Comandos:");
        System.out.println("  add <prioridad> <tiempoCPU>   -> agrega proceso");
        System.out.println("  ENTER (línea vacía)           -> salir");

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
                    if (p.length < 3) throw new IllegalArgumentException();

                    int prioridad = Integer.parseInt(p[1]);
                    int cpu = Integer.parseInt(p[2]);

                    Proceso nuevo = new Proceso(id++, prioridad, 0, cpu);
                    plan.agregarProceso(nuevo);

                    System.out.println("Proceso agregado: " + nuevo.getId() +
                                       " (prio=" + prioridad + ", cpu=" + cpu + ")");
                } catch (Exception e) {
                    System.out.println("Formato inválido. Usa: add prioridad tiempoCPU");
                }
            } else {
                System.out.println("Comando desconocido. Usa 'add' o presiona ENTER para salir.");
            }
        }

        System.out.println("Programa finalizado.");
    }
}
