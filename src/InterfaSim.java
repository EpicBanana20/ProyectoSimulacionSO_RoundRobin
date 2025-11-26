
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

public class InterfaSim extends JFrame {

    private final PlanificadorMultiprocesador plan;

    // Timer de simulación (el que controla la pausa)
    private Timer timerSimulacion;

    // Pausa (sincronización)
    private final Object pausaLock = new Object();
    private volatile boolean pausado = false;

    // Top
    private final JLabel lblTiempo = new JLabel();

    // Text areas
    private final JTextArea areaCPUs = new JTextArea();
    private final JTextArea areaColas = new JTextArea();
    private final JTextArea areaMemoria = new JTextArea();

    // Tabla
    private final DefaultTableModel modeloTablaProcesos;
    private final JTable tablaProcesos;

    // Form inputs
    private final JTextField txtPrioridad = new JTextField();
    private final JTextField txtTiempoCPU = new JTextField();
    private final JButton btnAgregar = new JButton("Agregar");
    private final JButton btnAgregarRandom = new JButton("Agregar Aleatorio");

    // Pause / Resume
    private final JButton btnPauseResume = new JButton("Pausar");

    // Auto ID
    private int autoID = 1;
    private final Random rnd = new Random();

    // Gantt
    private final int GANTT_WIDTH = 140;
    private final List<Deque<Integer>> ganttHistory = new ArrayList<>();

    // Visual panels
    private final CpuUsagePanel cpuUsagePanel;
    private final RamPanel ramPanel;
    private final GanttPanel ganttPanel;

    // Dark theme colors
    private final Color bg = new Color(34, 37, 41);
    private final Color panelBg = new Color(45, 49, 55);
    private final Color accent = new Color(100, 180, 255);
    private final Color text = new Color(220, 223, 226);
    private final Color muted = new Color(160, 165, 170);

    private final List<JLabel> statsLabels;

    public InterfaSim(PlanificadorMultiprocesador plan) {
        this.plan = plan;

        setTitle("Simulador - Visor de procesos (Dark)");
        setSize(1200, 800);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        getContentPane().setBackground(bg);

        //arriba barra
        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setBackground(panelBg);
        top.setBorder(new EmptyBorder(10, 10, 10, 10));

        lblTiempo.setFont(new Font("SansSerif", Font.BOLD, 18));
        lblTiempo.setForeground(text);
        lblTiempo.setText("TiempoGlobal: 0");
        top.add(lblTiempo, BorderLayout.WEST);

        //controles
        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        JPanel form = new JPanel(new GridLayout(2, 4, 6, 6));
        form.setOpaque(false);

        JLabel lPrio = new JLabel("Prioridad:");
        JLabel lCpu = new JLabel("Tiempo CPU:");
        styleLabel(lPrio);
        styleLabel(lCpu);

        txtPrioridad.setColumns(6);
        txtTiempoCPU.setColumns(6);
        styleField(txtPrioridad);
        styleField(txtTiempoCPU);

        btnAgregar.setBackground(accent);
        btnAgregar.setForeground(Color.BLACK);
        btnAgregar.addActionListener(e -> crearProcesoDesdeForm());

        btnAgregarRandom.setBackground(new Color(110, 200, 120));
        btnAgregarRandom.setForeground(Color.BLACK);
        btnAgregarRandom.addActionListener(e -> crearProcesoAleatorio());

        btnPauseResume.setBackground(new Color(240, 160, 60));
        btnPauseResume.setForeground(Color.BLACK);
        btnPauseResume.addActionListener(this::accionPausaResume);

        form.add(lPrio);
        form.add(lCpu);
        form.add(new JLabel());
        form.add(new JLabel());
        form.add(txtPrioridad);
        form.add(txtTiempoCPU);
        form.add(btnAgregar);
        form.add(btnAgregarRandom);

        controls.add(form);
        controls.add(btnPauseResume);

        top.add(controls, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 8, 8));
        center.setBorder(new EmptyBorder(8, 8, 8, 8));
        center.setBackground(bg);

        // panel izq
        JPanel left = new JPanel(new BorderLayout(8, 8));
        left.setBackground(panelBg);
        left.setBorder(new CompoundBorder(new EmptyBorder(8, 8, 8, 8), new LineBorder(new Color(60, 60, 60))));

        JPanel texts = new JPanel(new GridLayout(3, 1, 6, 6));
        texts.setBackground(panelBg);

        setupTextArea(areaCPUs);
        setupTextArea(areaColas);
        setupTextArea(areaMemoria);

        texts.add(wrapWithTitled("CPUs (texto)", areaCPUs));
        texts.add(wrapWithTitled("Colas (texto)", areaColas));
        texts.add(wrapWithTitled("Memoria", areaMemoria));

        left.add(texts, BorderLayout.CENTER);

        String[] cols = {"ID", "Prio", "Llegada", "Inicio", "CPU Rest", "Espera", "Resp", "Retorno", "Estado", "MemKB"};
        modeloTablaProcesos = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        tablaProcesos = new JTable(modeloTablaProcesos);
        JScrollPane spTab = new JScrollPane(tablaProcesos);
        spTab.setBorder(new TitledBorder("Procesos"));
        left.add(spTab, BorderLayout.SOUTH);
        // panel derecho
        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.setBackground(panelBg);
        right.setBorder(new CompoundBorder(new EmptyBorder(8, 8, 8, 8), new LineBorder(new Color(60, 60, 60))));

        int cpusCount = Math.max(1, plan.getCpus().size());
        for (int i = 0; i < cpusCount; i++) {
            Deque<Integer> dq = new ArrayDeque<>(GANTT_WIDTH);
            for (int j = 0; j < GANTT_WIDTH; j++) dq.addLast(-1);
            ganttHistory.add(dq);
        }

        // instantiate visual panels
        cpuUsagePanel = new CpuUsagePanel();
        cpuUsagePanel.setPreferredSize(new Dimension(380, 120));
        cpuUsagePanel.setBorder(new TitledBorder("Uso CPU"));

        ramPanel = new RamPanel();
        ramPanel.setPreferredSize(new Dimension(380, 160));
        ramPanel.setBorder(new TitledBorder("Memoria RAM"));

        ganttPanel = new GanttPanel();
        ganttPanel.setPreferredSize(new Dimension(380, 220));
        ganttPanel.setBorder(new TitledBorder("Vista Gantt"));

        // stack vertically inside right column
        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.add(cpuUsagePanel);
        stack.add(Box.createVerticalStrut(8));
        stack.add(ramPanel);
        stack.add(Box.createVerticalStrut(8));
        stack.add(ganttPanel);

        // Stats
        JPanel stats = new JPanel(new GridLayout(3, 2, 6, 6));
        stats.setOpaque(false);
        stats.setBorder(new TitledBorder("Estadísticas"));

        statsLabels = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            JLabel lbl = new JLabel();
            lbl.setForeground(text);
            stats.add(lbl);
            statsLabels.add(lbl);
        }

        right.add(stack, BorderLayout.CENTER);
        right.add(stats, BorderLayout.SOUTH);

        center.add(left);
        center.add(right);

        add(center, BorderLayout.CENTER);

        // boton
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.setBackground(panelBg);
        JLabel legend = new JLabel("Leyenda: idle = -1 (RAM: verde = ejecutando, azul = listo, gris = libre)");
        legend.setForeground(muted);
        bottom.add(legend);
        add(bottom, BorderLayout.SOUTH);

        //     refresco
        timerSimulacion = new Timer(300, e -> {
            if (!pausado) refrescar();
        });
        timerSimulacion.start();

        applyDarkStyles();
        setVisible(true);
    }

    // stop seguir
   

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

    // Botón de la UI
    private void accionPausaResume(ActionEvent ev) {
        if (pausado) {
            // REANUDAR
            reanudar();
            btnPauseResume.setText("Pausar");
            if (timerSimulacion != null) timerSimulacion.start();
        } else {
            // PAUSAR
            pausar();
            btnPauseResume.setText("Reanudar");
            if (timerSimulacion != null) timerSimulacion.stop();
        }
    }
    private void styleLabel(JLabel l) {
        l.setForeground(text);
    }

    private void styleField(JTextField f) {
        f.setBackground(new Color(60, 63, 68));
        f.setForeground(text);
    }

    private JScrollPane wrapWithTitled(String title, JTextArea area) {
        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(new TitledBorder(title));
        return sp;
    }

    private void setupTextArea(JTextArea a) {
        a.setEditable(false);
        a.setBackground(panelBg);
        a.setForeground(text);
        a.setFont(new Font("Monospaced", Font.PLAIN, 12));
    }

    private void applyDarkStyles() {
        // keep minimal; you can add more styling if desired
        tablaProcesos.setBackground(new Color(50, 54, 60));
        tablaProcesos.setForeground(text);
        tablaProcesos.setGridColor(new Color(70,70,70));
        tablaProcesos.getTableHeader().setBackground(new Color(60,63,68));
        tablaProcesos.getTableHeader().setForeground(text);
    }

    // Crear proceso desde formulario
    private void crearProcesoDesdeForm() {
        try {
            int prio = Integer.parseInt(txtPrioridad.getText().trim());
            int cpu = Integer.parseInt(txtTiempoCPU.getText().trim());
            crearProceso(prio, cpu);
            txtPrioridad.setText("");
            txtTiempoCPU.setText("");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Valores inválidos", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void crearProcesoAleatorio() {
        crearProceso(1 + rnd.nextInt(5), 5 + rnd.nextInt(25));
    }

    private void crearProceso(int prioridad, int tiempoCPU) {
        int id = autoID++;
        int llegada = TiempoGlobal.get();
        int mem = 50;
        Proceso p = new Proceso(id, prioridad, llegada, tiempoCPU, mem);
        plan.agregarProceso(p);
    }
    //refresh ui
    private void refrescar() {
        int tg = TiempoGlobal.get();
        lblTiempo.setText("TiempoGlobal: " + tg);

        actualizarTablaProcesos();
        cpuUsagePanel.updateData(plan.getCpus());
        actualizarGanttHistory();
        actualizarStats();

        areaCPUs.setText(textCPUs());
        areaColas.setText(textColas());
        areaMemoria.setText(textMemoria());

        cpuUsagePanel.repaint();
        ramPanel.repaint();
        ganttPanel.repaint();
    }

    private String textCPUs() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Procesador pr : plan.getCpus()) {
            Proceso cur = pr.getProcesoActual();
            sb.append("CPU ").append(i++).append(": ");
            sb.append(cur == null ? "IDLE" : cur.toString());
            sb.append('\n');
        }
        return sb.toString();
    }

    private String textColas() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Procesador pr : plan.getCpus()) {
            sb.append("CPU ").append(i++).append(":\n");
            Map<Integer,List<Proceso>> snap = pr.getColasSnapshot();
            for (var e : snap.entrySet()) {
                sb.append("  Prio ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
        }
        return sb.toString();
    }

    private String textMemoria() {
        StringBuilder sb = new StringBuilder();
        var blks = plan.getMemManager().getSnapshot();
        sb.append("Total: ").append(plan.getMemManager().getTamTotalKB())
                .append("  Usada: ").append(plan.getMemManager().getOcupadoKB()).append("\n");
        for (var b : blks) sb.append(b).append("\n");
        return sb.toString();
    }

    private void actualizarTablaProcesos() {
        modeloTablaProcesos.setRowCount(0);
        List<Proceso> todos = new ArrayList<>(plan.getProcesosActivos());
        for (Proceso t : plan.getTodosTerminados())
            if (!todos.contains(t)) todos.add(t);

        for (Proceso p : todos) {
            String estado;
            if (plan.getTodosTerminados().contains(p)) estado = "Terminado";
            else if (p.getEstado() == Proceso.Estado.SUSPENDIDO) estado = "Suspendido";
            else if (estaEnCPU(p)) estado = "Ejecutando";
            else estado = "Listo";

            modeloTablaProcesos.addRow(new Object[]{
                    p.getId(),
                    p.getPrioridad(),
                    p.getTiempoLlegada(),
                    p.getTiempoInicio() < 0 ? "-" : p.getTiempoInicio(),
                    p.getTiempoRestante(),
                    p.getTiempoEspera(),
                    p.getTiempoRespuesta() < 0 ? "-" : p.getTiempoRespuesta(),
                    p.getTiempoRetorno() < 0 ? "-" : p.getTiempoRetorno(),
                    estado,
                    p.getTamMemoriaKB()
            });
        }
    }

    private boolean estaEnCPU(Proceso p) {
        for (Procesador cpu : plan.getCpus())
            if (cpu.getProcesoActual() == p) return true;
        return false;
    }

    private void actualizarStats() {
        List<Proceso> activos = plan.getProcesosActivos();
        int terminados = plan.getTodosTerminados().size();
        int suspendidos = 0;
        for (Proceso p : activos) if (p.getEstado() == Proceso.Estado.SUSPENDIDO) suspendidos++;

        statsLabels.get(0).setText("Activos: " + activos.size());
        statsLabels.get(1).setText("Suspendidos: " + suspendidos);
        statsLabels.get(2).setText("Terminados: " + terminados);
        statsLabels.get(3).setText("Mem total: " + plan.getMemManager().getTamTotalKB());
        statsLabels.get(4).setText("Mem usada: " + plan.getMemManager().getOcupadoKB());
        statsLabels.get(5).setText("CPUs: " + plan.getCpus().size());
    }

    //Gantt 
    private void actualizarGanttHistory() {
        List<Procesador> cpus = plan.getCpus();
        while (ganttHistory.size() < cpus.size()) {
            Deque<Integer> dq = new ArrayDeque<>();
            for (int i = 0; i < GANTT_WIDTH; i++) dq.add(-1);
            ganttHistory.add(dq);
        }

        for (int i = 0; i < cpus.size(); i++) {
            Deque<Integer> dq = ganttHistory.get(i);
            if (dq.size() >= GANTT_WIDTH) dq.removeFirst();

            Proceso cur = cpus.get(i).getProcesoActual();
            dq.addLast(cur == null ? -1 : cur.getId());
        }
    }

    //Panels internos 
    private class CpuUsagePanel extends JPanel {
        List<Double> util = new ArrayList<>();

        public CpuUsagePanel() { setBackground(panelBg); }

        public void updateData(List<Procesador> cpus) {
            util.clear();
            for (Procesador p : cpus) {
                long e = p.getTicksEjecutados();
                long t = p.getTicksTotales();
                util.add(t == 0 ? 0.0 : (double) e / t);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth(), h = getHeight();
            int y = 10;

            for (int i = 0; i < util.size(); i++) {
                double u = util.get(i);
                int barH = 20;

                g.setColor(muted);
                g.fillRect(10, y, w - 20, barH);

                g.setColor(accent);
                g.fillRect(10, y, (int) ((w - 20) * u), barH);

                g.setColor(text);
                g.drawString("CPU " + i + ": " + (int)(u * 100) + "%", 15, y + 15);

                y += barH + 10;
            }
        }
    }

    private class GanttPanel extends JPanel {

        public GanttPanel() {
            setBackground(panelBg);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth(), h = getHeight();

            int rows = ganttHistory.size();
            int rowH = rows == 0 ? h : (h / rows);

            for (int r = 0; r < rows; r++) {
                Deque<Integer> dq = ganttHistory.get(r);

                int x = 10;
                int cellW = Math.max(2, (w - 20) / GANTT_WIDTH);

                int idx = 0;
                for (int pid : dq) {
                    g.setColor(pid == -1 ? new Color(70, 70, 70) : colorFromId(pid));
                    g.fillRect(x + idx * cellW, r * rowH + 5, cellW, rowH - 10);
                    idx++;
                }

                g.setColor(text);
                g.drawString("CPU " + r, 10, (r + 1) * rowH - 4);
            }
        }

        private Color colorFromId(int id) {
            rnd.setSeed(id * 777);
            return new Color(
                    80 + rnd.nextInt(140),
                    80 + rnd.nextInt(140),
                    80 + rnd.nextInt(140)
            );
        }
    }

    private class RamPanel extends JPanel {

        public RamPanel() {
            setPreferredSize(new Dimension(380, 160));
            setBackground(panelBg);
            setBorder(new TitledBorder("RAM"));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            int w = Math.max(10, getWidth() - 20);
            int h = Math.max(10, getHeight() - 50);
            int x0 = 10;
            int y0 = 20;

            var bloques = plan.getMemManager().getSnapshot();
            int total = plan.getMemManager().getTamTotalKB();
            if (total <= 0) total = 1; // evitar división por cero

            int cursorX = x0;

            for (AdministradorMemoria.Bloque b : bloques) {

                double frac = (double) b.tamano / total;
                int bw = Math.max(2, (int) (w * frac));

                Color col;

                if (b.estaLibre()) {
                    col = new Color(80, 80, 80);         // libre
                } else {
                    Proceso p = b.proceso;

                    // si el proceso está suspendido técnicamente no debería ocupar memoria
                    // pero como tu AdministradorMemoria asigna null para libres, aquí consideramos
                    // el estado del proceso actual en el bloque para colorear.
                    if (p != null && p.getEstado() == Proceso.Estado.EJECUTANDO) {
                        col = new Color(80, 180, 80);    // ejecutando foreground
                    } else if (p != null && p.getEstado() == Proceso.Estado.LISTO) {
                        col = new Color(100, 160, 255);  // listo - background
                    } else {
                        col = new Color(100, 100, 100);  // fallback / otros
                    }
                }

                g.setColor(col);
                g.fillRect(cursorX, y0, bw, h);

                g.setColor(new Color(30,30,30));
                g.drawRect(cursorX, y0, bw, h);

                cursorX += bw;
            }

            // Texto de uso
            g.setColor(text);
            int usado = plan.getMemManager().getOcupadoKB();
            g.drawString("Usado: " + usado + " / " + plan.getMemManager().getTamTotalKB() + " KB", x0, y0 + h + 18);

            // legenda pequeña
            int leyX = x0;
            int leyY = y0 + h + 32;
            int leyW = 14, leyH = 12, gap = 8;
            // ejecutando
            g.setColor(new Color(80,180,80));
            g.fillRect(leyX, leyY, leyW, leyH);
            g.setColor(text);
            g.drawString("Ejecutando", leyX + leyW + 6, leyY + leyH - 2);
            leyX += 90;
            // listo
            g.setColor(new Color(100,160,255));
            g.fillRect(leyX, leyY, leyW, leyH);
            g.setColor(text);
            g.drawString("Listo (en RAM)", leyX + leyW + 6, leyY + leyH - 2);
            leyX += 130;
            // libre
            g.setColor(new Color(80,80,80));
            g.fillRect(leyX, leyY, leyW, leyH);
            g.setColor(text);
            g.drawString("Libre", leyX + leyW + 6, leyY + leyH - 2);
        }
    }

}
