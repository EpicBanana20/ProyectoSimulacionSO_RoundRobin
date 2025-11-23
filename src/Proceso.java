public class Proceso {

    public enum Estado {
        NUEVO,
        LISTO,
        EJECUTANDO,
        SUSPENDIDO,
        TERMINADO
    }

    private int id;
    private Estado estado;

    public Proceso(int id) {
        this.id = id;
        this.estado = Estado.NUEVO; // Estado inicial
    }

    public int getId() {
        return id;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado nuevoEstado) {
        this.estado = nuevoEstado;
    }

    @Override
    public String toString() {
        return "Proceso { ID = " + id + ", Estado = " + estado + " }";
    }
}
