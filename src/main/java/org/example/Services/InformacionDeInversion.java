package org.example.Services;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.example.DatabaseConnection;
import org.example.Models.InversionOportunidad;
import org.example.Models.Oportunidad;
import org.example.Models.ResultadoSimulacion;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class InformacionDeInversion {
    DatabaseConnection databaseConnection = new DatabaseConnection();
    Oportunidad oportunidad = databaseConnection.executeQuery(42, 2643); // Atributos y configuración

    private final double Pg = oportunidad.getPg(); // Probabilidad geológica
    private final double P10 = oportunidad.getPceP10();
    private final double P90 = oportunidad.getPceP90();
    private final double P10Area = oportunidad.getArea10();
    private final double P90Area = oportunidad.getArea90();
    private final double factorConversionAceite = oportunidad.getFcAceite();
    private final double factorConversionGas = oportunidad.getFcGas();
    private final double factorCondensado = oportunidad.getFcCondensado();
    private final double GastoInicialAceiteMin = oportunidad.getGastoMINAceite();
    private final double GastoInicialAceiteMP = oportunidad.getGastoMPAceite();
    private final double GastoInicialAceiteMax = oportunidad.getGastoMAXAceite();
    private final double PrimeraDeclinacionMin = oportunidad.getPrimeraDeclinacionMin();
    private final double PrimeraDeclinacionMP = oportunidad.getPrimeraDeclinacionMP();
    private final double PrimeraDeclinacionMax = oportunidad.getPrimeraDeclinacionMAX();
    private final InversionOportunidad inversionOportunidad = oportunidad.getInversionOportunidad();

    private Random random = new Random();



    public ResponseEntity<List<ResultadoSimulacion>> runSimulation() {
        List<ResultadoSimulacion> results = new ArrayList<>();


        for (int i = 0; i < 200; i++) {
            ResultadoSimulacion ResultadoSimulacion = new ResultadoSimulacion();

            double aleatorioRecurso = random.nextDouble();
            double recurso = calcularRecursoProspectivo(aleatorioRecurso, P10, P90);
            ResultadoSimulacion.setPce(recurso);


            if (inversionOportunidad.getBateriaMin() != 0) {
                double AleatorioInverision = random.nextDouble();
                double triangularInversionBat = triangularDistribution(recurso, inversionOportunidad.getBateriaMin(),
                        inversionOportunidad.getBateriaMax(),
                        inversionOportunidad.getBateriaMp(),
                        AleatorioInverision);
                ResultadoSimulacion.setBateria(triangularInversionBat);
            }

            if (inversionOportunidad.getPlataformadesarrolloMin() != 0) {
                double AleatorioInverision = random.nextDouble();
                double triangularInversionPlataforma = triangularDistribution(recurso, inversionOportunidad.getPlataformadesarrolloMin(),
                        inversionOportunidad.getPlataformadesarrolloMax(),
                        inversionOportunidad.getPlataformadesarrolloMp(),
                        AleatorioInverision);
                ResultadoSimulacion.setPlataformaDesarrollo(triangularInversionPlataforma);
            }

            if (inversionOportunidad.getLineadedescargaMin() != 0) {
                double AleatorioInverision = random.nextDouble();
                double triangularInversionLineaDescarga = triangularDistribution(recurso, inversionOportunidad.getLineadedescargaMin(),
                        inversionOportunidad.getLineadedescargaMax(),
                        inversionOportunidad.getLineadedescargaMp(),
                        AleatorioInverision);
                ResultadoSimulacion.setLineaDescarga(triangularInversionLineaDescarga);
            }

            if (inversionOportunidad.getEstacioncompresionMin() != 0) {
                double AleatorioInverision = random.nextDouble();
                double triangularInversionEstacionCompresion = triangularDistribution(recurso, inversionOportunidad.getEstacioncompresionMin(),
                        inversionOportunidad.getEstacioncompresionMax(),
                        inversionOportunidad.getEstacioncompresionMp(),
                        AleatorioInverision);
                ResultadoSimulacion.setEComprension(triangularInversionEstacionCompresion);
            }

            if (inversionOportunidad.getDuctoMin() != 0) {
                double AleatorioInverision = random.nextDouble();
                double triangularInversionDucto = triangularDistribution(recurso, inversionOportunidad.getDuctoMin(),
                        inversionOportunidad.getDuctoMax(),
                        inversionOportunidad.getDuctoMp(),
                        AleatorioInverision);
                ResultadoSimulacion.setDucto(triangularInversionDucto);
            }







            results.add(ResultadoSimulacion);




        }


        System.out.println("Simulation completed.");


        return ResponseEntity.ok(results);    }





    private double triangularDistribution(double PCE, double gmin, double gmax, double gmp, double aleatorio) {
        if (PCE == 0) {
            return 0.0;
        }

        if (gmax - gmin != 0) {
            double fraction = (gmp - gmin) / (gmax - gmin);
            if (aleatorio < fraction) {
                return gmin + Math.sqrt(aleatorio * (gmax - gmin) * (gmp - gmin));
            } else {
                return gmax - Math.sqrt((1 - aleatorio) * (gmax - gmin) * (gmax - gmp));
            }
        }
        return gmin;
    }


    private double calcularRecursoProspectivo(double aleatorio, double percentil10, double percentil90) {
        NormalDistribution normalStandard = new NormalDistribution(0, 1);
        double z90 = normalStandard.inverseCumulativeProbability(0.9);
        double mediaLog = (Math.log(percentil10) + Math.log(percentil90)) / 2;
        double desviacionLog = (Math.log(percentil10) - mediaLog) / z90;

        NormalDistribution normal = new NormalDistribution(mediaLog, desviacionLog);

        double logValue = normal.inverseCumulativeProbability(aleatorio);
        return Math.exp(logValue);
    }



}
