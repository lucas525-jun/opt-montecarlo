package com.pemex.oportexp.Controller;

import com.pemex.oportexp.Services.ExcelService;
import com.pemex.oportexp.Services.MonteCarloService;
import com.pemex.oportexp.impl.MonteCarloSimulation;
import com.pemex.oportexp.impl.MonteCarloSimulationMultiObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import com.pemex.oportexp.impl.MonteCarloDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
public class MonteCarloSimulationController {

    @Autowired
    private MonteCarloService monteCarloService;

    @Autowired
    private MonteCarloDAO monteCarloDAO;

    @Autowired
    RestTemplate restTemplate;

    @Value("${OPORTEXT_GENEXCEL_HOST:localhost}")
    private String genExcelHost;

    @Value("${OPORTEXT_GENEXCEL_POST:3000}")
    private String genExcelPort;

    @GetMapping("/run")
    public ResponseEntity<?> runSimulation(@RequestParam("Version") String version,
            @RequestParam("IdOportunidadObjetivo") int idOportunidadObjetivo,
            @RequestParam("IdOportunidad") int idOportunidad) {

        int num = 1;
        List<Map<String, Object>> multiObjetivos;

        multiObjetivos = monteCarloDAO.getMultiOjbectivo(idOportunidad);
        List<Object> resultados;
        // if (num == 1) {
        if (multiObjetivos.size() == 1) {
            System.err.println("1 : " + multiObjetivos.get(0).get("idoportunidadobjetivo"));
            MonteCarloSimulation monteCarloSimulation = monteCarloService.createSimulation(version,
                    (int) multiObjetivos.get(0).get("idoportunidadobjetivo"));
            resultados = monteCarloSimulation.runSimulation().getBody();

        } else {
            System.err.println("multi object started.");

            int[] idOportunidadObjetivoArray = new int[multiObjetivos.size()];

            for (int i = 0; i < multiObjetivos.size(); i++) {
                idOportunidadObjetivoArray[i] = (int) multiObjetivos.get(i).get("idoportunidadobjetivo");
            }
            MonteCarloSimulationMultiObject monteCarloSimulationMultiObject = monteCarloService
                    .createSimulationForMulti(
                            version,
                            idOportunidadObjetivoArray);
            resultados = monteCarloSimulationMultiObject.runSimulation().getBody();
        }
        try {
            String nodeUrl = "http://" + genExcelHost + ":" + genExcelPort + "/generate-excel";

            // Configura los headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Crea la solicitud HTTP
            HttpEntity<List<Object>> request = new HttpEntity<>(resultados, headers);

            // Llama al servidor Node.js
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    nodeUrl,
                    HttpMethod.POST,
                    request,
                    byte[].class);

            // Verifica si la respuesta es válida
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // Configura las cabeceras para la descarga
                HttpHeaders fileHeaders = new HttpHeaders();
                fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                fileHeaders.setContentDisposition(ContentDisposition
                        .attachment()
                        .filename("Resultados.xlsx")
                        .build());

                return new ResponseEntity<>(response.getBody(), fileHeaders, HttpStatus.OK);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error al generar el archivo Excel.");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error en la simulación: " + e.getMessage());
        }
    }

    // @GetMapping("/runJson")
    // public ResponseEntity<?> runSimulationJson(@RequestParam("Version") String
    // version,
    // @RequestParam("IdOportunidad") int idOportunidadObjetivo) {

    // MonteCarloSimulation monteCarloSimulation = new MonteCarloSimulation(version,
    // idOportunidadObjetivo);

    // try {
    // // Ejecuta la simulación y obtiene los datos
    // List<Object> resultados = monteCarloSimulation.runSimulation().getBody();

    // // Devuelve los resultados directamente
    // return ResponseEntity.ok(resultados);

    // } catch (Exception e) {
    // return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
    // .body("Error en la simulación: " + e.getMessage());
    // }
    // }
}
