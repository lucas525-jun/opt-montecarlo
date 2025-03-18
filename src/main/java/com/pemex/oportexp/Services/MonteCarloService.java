package com.pemex.oportexp.Services;

import com.pemex.oportexp.impl.MonteCarloDAO;
import com.pemex.oportexp.impl.MonteCarloSimulation;
import com.pemex.oportexp.impl.MonteCarloSimulationMultiObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MonteCarloService {
    @Autowired
    private MonteCarloDAO monteCarloDAO;

    @Value("${OPORTEXT_ECONOMIC_EVA_HOST:localhost}")
    private String economicEvaHost;

    @Value("${OPORTEXT_ECONOMIC_EVA_PORT:8082}")
    private String economicEvaPort;

    public MonteCarloSimulation createSimulation(String version, int idOportunidadObjetivo, int iterations, int pgValue) {
        MonteCarloSimulation monteCarloSimulation = new MonteCarloSimulation(version, idOportunidadObjetivo, iterations, pgValue);
        monteCarloSimulation.setMonteCarloDAO(monteCarloDAO);
        monteCarloSimulation.setEconomicEvaHostAndPort(economicEvaHost, economicEvaPort);
        return monteCarloSimulation;
    }

    public MonteCarloSimulationMultiObject createSimulationForMulti(String version, int[] idOportunidadObjetivo, int iterations, int pgValue) {
        MonteCarloSimulationMultiObject monteCarloSimulationMultiObject = new MonteCarloSimulationMultiObject(version,
                idOportunidadObjetivo, economicEvaHost, economicEvaPort, iterations, pgValue);
        monteCarloSimulationMultiObject.setMonteCarloDAO(monteCarloDAO);
        monteCarloSimulationMultiObject.initializeSimulation();

        return monteCarloSimulationMultiObject;
    }
}
