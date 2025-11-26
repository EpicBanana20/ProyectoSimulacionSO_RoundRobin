/* File: Proceso.java */
public class Proceso {

    public enum Estado {
        NUEVO,
        LISTO,
        EJECUTANDO,
        SUSPENDIDO,
        TERMINADO
    }

    private final int id;
    private int prioridad;

    private int tiempoLlegada; // asignado por el planificador al agregar
    private int tiempoInicio;
    private int tiempoFin;

    private final int tiempoCPU; // ráfaga total requerida
    private int tiempoRestante; // para RR

    private Estado estado;

    public Proceso(int id, int prioridad, int tiempoLlegada, int tiempoCPU) {
        this.id = id;
        this.prioridad = prioridad;
        this.tiempoLlegada = tiempoLlegada;
        this.tiempoCPU = tiempoCPU;
        this.tiempoRestante = tiempoCPU;

        this.estado = Estado.NUEVO;
        this.tiempoInicio = -1;
        this.tiempoFin = -1;
    }

    public int getId() {
        return id;
    }

    public int getPrioridad() {
        return prioridad;
    }

    public void setPrioridad(int prioridad) {
        this.prioridad = prioridad;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado nuevoEstado) {
        this.estado = nuevoEstado;
    }

    public int getTiempoLlegada() {
        return tiempoLlegada;
    }

    public void setTiempoLlegada(int tiempoLlegada) {
        this.tiempoLlegada = tiempoLlegada;
    }

    public int getTiempoInicio() {
        return tiempoInicio;
    }

    public void setTiempoInicio(int tiempoInicio) {
        this.tiempoInicio = tiempoInicio;
    }

    public int getTiempoFin() {
        return tiempoFin;
    }

    public void setTiempoFin(int tiempoFin) {
        this.tiempoFin = tiempoFin;
    }

    public int getTiempoCPU() {
        return tiempoCPU;
    }

    public int getTiempoRestante() {
        return tiempoRestante;
    }

    public void consumirCPU(int unidades) {
        tiempoRestante -= unidades;
        if (tiempoRestante < 0) tiempoRestante = 0;
    }

    public void cambiarEstado(Estado nuevoEstado) {
        this.estado = nuevoEstado;
    }

    // TIEMPOS DERIVADOS

    // Tiempo de respuesta = inicio - llegada
    public int getTiempoRespuesta() {
        if (tiempoInicio == -1 || tiempoLlegada == -1) return -1;
        return tiempoInicio - tiempoLlegada;
    }

    // Tiempo de retorno = fin - llegada
    public int getTiempoRetorno() {
        if (tiempoFin == -1 || tiempoLlegada == -1) return -1;
        return tiempoFin - tiempoLlegada;
    }

    // Tiempo de espera = retorno - CPU total
    public int getTiempoEspera() {
        int retorno = getTiempoRetorno();
        if (retorno == -1) return -1;
        return retorno - tiempoCPU;
    }

    @Override
    public String toString() {
        return "P" + id + "(R=" + tiempoRestante + ",Pr=" + prioridad + ")";
    }
}
