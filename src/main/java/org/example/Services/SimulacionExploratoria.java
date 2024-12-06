package org.example.Services;
import org.example.DatabaseConnection;
import org.example.Models.InversionOportunidad;
import org.example.Models.Oportunidad;
import org.example.Models.ResultadoSimulacion;
import org.springframework.http.ResponseEntity;


import java.util.*;

public class SimulacionExploratoria {
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
            double aleatorioExploratorioInfra = random.nextDouble();
            double triangularExploratorioMin = triangularDistributionSinPCE(oportunidad.getInfraestructuraMin(),
                    oportunidad.getInfraestructuraMax(),
                    oportunidad.getInfraestructuraMP(),
                    aleatorioExploratorioInfra);
            ResultadoSimulacion.setExploratoriaInfra(triangularExploratorioMin);

            double aleatorioExploratorioPerforacion = random.nextDouble();
            double triangularExploratorioPer = triangularDistributionSinPCE( oportunidad.getPerforacionMin(),
                    oportunidad.getPerforacionMax(),
                    oportunidad.getPerforacionMP(),
                    aleatorioExploratorioPerforacion);
            ResultadoSimulacion.setExploratoriaPerf(triangularExploratorioPer);


            double aleatorioExploratorioTerminacion = random.nextDouble();
            double triangularExploratorioTer = triangularDistributionSinPCE(oportunidad.getTerminacionMin(),
                    oportunidad.getTerminacionMax(),
                    oportunidad.getTerminacionMP(),
                    aleatorioExploratorioTerminacion);
            ResultadoSimulacion.setExploratoriaTer(triangularExploratorioTer);


            results.add(ResultadoSimulacion);




        }


            System.out.println("Simulation completed.");


        return ResponseEntity.ok(results);    }

    private double triangularDistributionSinPCE(double gmin, double gmax, double gmp, double aleatorio) {
        if (gmax - gmin != 0) {
            double fraction = (gmp - gmin) / (gmax - gmin);

            if (aleatorio < fraction) {
                return gmin + Math.sqrt(aleatorio * (gmp - gmin) * (gmax - gmin));
            } else {
                return gmax - Math.sqrt((1 - aleatorio) * (gmp - gmin) * (gmax - gmin));
            }
        }
        return gmp;

    }




}


