/* File: InterfaSim.java */
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InterfaSim extends JFrame {

    private final PlanificadorMultiprocesador plan;
    private final JLabel lblTiempo;
    private final JTextArea areaCPUs;
    private final JTextArea areaColas;

    // tabla procesos
    private JTable tablaProcesos;
    private DefaultTableModel modeloTablaProcesos;

    public InterfaSim(PlanificadorMultiprocesador plan) {
        this.plan = plan;

        setTitle("Simulador - Visor de procesos");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        lblTiempo = new JLabel("TiempoGlobal: 0");
        lblTiempo.setFont(new Font("SansSerif", Font.BOLD, 16));
        add(lblTiempo, BorderLayout.NORTH);

        JPanel centro = new JPanel(new GridLayout(1, 2));
        areaCPUs = new JTextArea();
        areaCPUs.setEditable(false);
        areaCPUs.setFont(new Font("Monospaced", Font.PLAIN, 12));
        areaColas = new JTextArea();
        areaColas.setEditable(false);
        areaColas.setFont(new Font("Monospaced", Font.PLAIN, 12));

        centro.add(new JScrollPane(areaCPUs));
        centro.add(new JScrollPane(areaColas));

        add(centro, BorderLayout.CENTER);

        // --- tabla de procesos (sur) ---
        String[] cols = {"ID", "Prio", "Llegada", "Inicio", "CPU Rest", "Espera", "Respuesta", "Retorno", "Estado"};
        modeloTablaProcesos = new DefaultTableModel(cols, 0) {
            // no editable
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tablaProcesos = new JTable(modeloTablaProcesos);
        tablaProcesos.setFillsViewportHeight(true);

        JPanel sur = new JPanel(new BorderLayout());
        sur.add(new JScrollPane(tablaProcesos), BorderLayout.CENTER);

        add(sur, BorderLayout.SOUTH);

        setVisible(true);

        // Timer Swing para actualizar la GUI cada 200 ms (en EDT)
        Timer timer = new Timer(200, e -> refrescar());
        timer.start();
    }

    private void refrescar() {
        int tg = TiempoGlobal.get();
        lblTiempo.setText("TiempoGlobal: " + tg);

        StringBuilder sbCpu = new StringBuilder();
        List<Procesador> cpus = plan.getCpus();
        for (int i = 0; i < cpus.size(); i++) {
            Procesador p = cpus.get(i);
            Proceso proc = p.getProcesoActual();
            sbCpu.append(String.format("CPU %d: ", i));
            if (proc == null) {
                sbCpu.append("IDLE\n");
            } else {
                sbCpu.append(String.format("Ejecutando %s | ticksQ=%d\n",
                        proc.toString(), p.getTicksEnQuantum()));
            }
        }
        areaCPUs.setText(sbCpu.toString());

        StringBuilder sbColas = new StringBuilder();
        // Agregamos las colas de cada CPU (cada procesador tiene su propio RR/colas)
        for (int i = 0; i < cpus.size(); i++) {
            Procesador p = cpus.get(i);
            sbColas.append("---- CPU ").append(i).append(" ----\n");
            Map<Integer, java.util.List<Proceso>> snap = p.getColasSnapshot();
            if (snap.isEmpty()) {
                sbColas.append("(sin colas)\n");
            } else {
                for (Map.Entry<Integer, java.util.List<Proceso>> e : snap.entrySet()) {
                    sbColas.append("Prio ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
                }
            }
        }

        areaColas.setText(sbColas.toString());

        // actualizar tabla de procesos en tiempo real
        actualizarTablaProcesos();
    }

    private void actualizarTablaProcesos() {

        // limpiar la tabla
        modeloTablaProcesos.setRowCount(0);

        // obtener activos y terminados
        List<Proceso> activos = plan.getProcesosActivos();
        List<Proceso> terminados = plan.getTodosTerminados();

        // Unir: primero activos (para listar en primer plano), luego terminados que no estén en activos
        List<Proceso> todos = new ArrayList<>();
        todos.addAll(activos);
        for (Proceso t : terminados) {
            if (!todos.contains(t)) todos.add(t);
        }

        for (Proceso p : todos) {

            String estado;
            if (terminados.contains(p)) {
                estado = "Terminado";
            } else {
                Proceso actualDelCPU = encontrarProcesoEnCPUs(p);
                if (actualDelCPU != null && actualDelCPU == p) {
                    estado = "Ejecutando";
                } else {
                    estado = "Listo";
                }
            }

            Object inicio = (p.getTiempoInicio() >= 0 ? p.getTiempoInicio() : "-");
            Object resp = (p.getTiempoRespuesta() >= 0 ? p.getTiempoRespuesta() : "-");
            Object retorno = (p.getTiempoRetorno() >= 0 ? p.getTiempoRetorno() : "-");

            modeloTablaProcesos.addRow(new Object[]{
                    p.getId(),
                    p.getPrioridad(),
                    p.getTiempoLlegada(),
                    inicio,
                    p.getRafagaRestante(),
                    p.getTiempoEsperaActual(),
                    resp,
                    retorno,
                    estado
            });
        }
    }

    // busca si el proceso p es el actual de algún CPU (devuelve el objeto si lo encuentra)
    private Proceso encontrarProcesoEnCPUs(Proceso buscado) {
        for (Procesador cpu : plan.getCpus()) {
            Proceso actual = cpu.getProcesoActual();
            if (actual != null && actual == buscado) return actual;
        }
        return null;
    }
}
