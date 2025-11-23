import java.util.List;

public class Main {

    public static void main(String[] args) {

        PlanificadorMultiprocesador plan =
                new PlanificadorMultiprocesador(2, 4); // 2 CPUs, quantum 4

        plan.agregarProceso(new Proceso(1, 1, 0, 10));
        plan.agregarProceso(new Proceso(2, 1, 0, 6));
        plan.agregarProceso(new Proceso(3, 2, 0, 8));
        plan.agregarProceso(new Proceso(4, 3, 0, 3));

        List<Proceso> terminados = plan.ejecutar();

        System.out.println("=== PROCESOS TERMINADOS ===");
        for (Proceso p : terminados) {
            System.out.println(p);
        }
    }
}
