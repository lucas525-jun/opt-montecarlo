package com.pemex.oportexp.Models;

import com.pemex.oportexp.Models.EvaluacionEconomica.EvaluacionEconomica;

import java.util.List;

public class OportunidadExcel {
    private int id;
    private String proyecto;
    private String oportunidad;
    private String objetivo;
    private int anio;
    private double ingresos;
    private double inversiones;
    private List<EvaluacionEconomica> evaluacionEconomica;


    // Constructor por defecto (sin argumentos)
    public OportunidadExcel() {
    }

    // Constructor con argumentos
    public OportunidadExcel(int id, String proyecto, String oportunidad, String objetivo, int anio, double ingresos, double inversiones) {
        this.id = id;
        this.proyecto = proyecto;
        this.oportunidad = oportunidad;
        this.objetivo = objetivo;
        this.anio = anio;
        this.ingresos = ingresos;
        this.inversiones = inversiones;
    }

    // Getters y setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getProyecto() {


        System.out.println(oportunidad);



        return proyecto; }
    public void setProyecto(String proyecto) { this.proyecto = proyecto; }

    public String getOportunidad() { return oportunidad; }
    public void setOportunidad(String oportunidad) { this.oportunidad = oportunidad; }

    public String getObjetivo() { return objetivo; }
    public void setObjetivo(String objetivo) { this.objetivo = objetivo; }

    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }

    public double getIngresos() { return ingresos; }
    public void setIngresos(double ingresos) { this.ingresos = ingresos; }

    public double getInversiones() { return inversiones; }
    public void setInversiones(double inversiones) { this.inversiones = inversiones; }

    public List<EvaluacionEconomica> getEvaluacionEconomica() {return evaluacionEconomica;}

}
