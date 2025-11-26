/* File: InterfaSim.java */
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class InterfaSim extends JFrame {

    private final PlanificadorMultiprocesador plan;
    private final JLabel lblTiempo;
    private final JTextArea areaCPUs;
    private final JTextArea areaColas;

    public InterfaSim(PlanificadorMultiprocesador plan) {
        this.plan = plan;

        setTitle("Simulador - Visor de procesos");
        setSize(700, 500);
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
    }
}
