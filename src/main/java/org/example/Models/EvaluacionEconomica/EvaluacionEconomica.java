package org.example.Models.EvaluacionEconomica;

public class EvaluacionEconomica {
    private int anio;
    private PozosProduccion pozosProduccion;
    private ProduccionDiariaPromedio produccionDiariaPromedio;
    private Ingresos ingresos;
    private Inversiones inversiones;
    private Costos costos;
    private FlujoContable flujoContable;

    // Getters y setters
    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }

    public PozosProduccion getPozosProduccion() { return pozosProduccion; }
    public void setPozosProduccion(PozosProduccion pozosProduccion) { this.pozosProduccion = pozosProduccion; }

    public ProduccionDiariaPromedio getProduccionDiariaPromedio() { return produccionDiariaPromedio; }
    public void setProduccionDiariaPromedio(ProduccionDiariaPromedio produccionDiariaPromedio) {
        this.produccionDiariaPromedio = produccionDiariaPromedio;
    }

    public Ingresos getIngresos() { return ingresos; }
    public void setIngresos(Ingresos ingresos) { this.ingresos = ingresos; }

    public Inversiones getInversiones() { return inversiones; }
    public void setInversiones(Inversiones inversiones) { this.inversiones = inversiones; }

    public Costos getCostos() { return costos; }
    public void setCostos(Costos costos) { this.costos = costos; }

    public FlujoContable getFlujoContable() { return flujoContable; }
    public void setFlujoContable(FlujoContable flujoContable) { this.flujoContable = flujoContable; }
}