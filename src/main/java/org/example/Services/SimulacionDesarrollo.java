package org.example.Services;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.example.DatabaseConnection;
import org.example.Models.InversionOportunidad;
import org.example.Models.Oportunidad;
import org.example.Models.ResultadoSimulacion;
import org.springframework.http.ResponseEntity;


import java.util.*;

public class SimulacionDesarrollo {
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


            double aleatorioDesInfra = random.nextDouble();
            double triangularDESInfra = triangularDistribution(recurso, oportunidad.getInfraestructuraMinDES(),
                    oportunidad.getInfraestructuraMaxDES(),
                    oportunidad.getInfraestructuraMPDES(),
                    aleatorioDesInfra);
            ResultadoSimulacion.setDesarrolloInfra(triangularDESInfra);



            double aleatorioDesPer = random.nextDouble();
            double triangularDESPer = triangularDistribution(recurso, oportunidad.getPerforacionMinDES(),
                    oportunidad.getPerforacionMaxDES(),
                    oportunidad.getPerforacionMPDES(),
                    aleatorioDesPer);
            ResultadoSimulacion.setDesarrolloPerf(triangularDESPer);



            double aleatorioDesTer = random.nextDouble();
            double triangularDESTer = triangularDistribution(recurso, oportunidad.getTerminacionMinDES(),
                    oportunidad.getTerminacionMaxDES(),
                    oportunidad.getTerminacionMPDES(),
                    aleatorioDesTer);
            ResultadoSimulacion.setDesarrolloTer(triangularDESTer);


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


