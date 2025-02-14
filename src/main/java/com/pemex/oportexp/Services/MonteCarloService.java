package com.pemex.oportexp.Services;

import com.pemex.oportexp.impl.MonteCarloDAO;
import com.pemex.oportexp.impl.MonteCarloSimulation;
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

    public MonteCarloSimulation createSimulation(String version, int idOportunidadObjetivo) {
        MonteCarloSimulation monteCarloSimulation = new MonteCarloSimulation(version, idOportunidadObjetivo);
        monteCarloSimulation.setMonteCarloDAO(monteCarloDAO);
        monteCarloSimulation.setEconomicEvaHostAndPort(economicEvaHost, economicEvaPort);
        return monteCarloSimulation;
    }
}
