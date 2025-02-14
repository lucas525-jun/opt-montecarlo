package com.pemex.oportexp.Controller;

import com.pemex.oportexp.Services.ExcelService;
import com.pemex.oportexp.Services.MonteCarloService;
import com.pemex.oportexp.impl.MonteCarloSimulation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@RestController
@RequestMapping("/api/simulation")
public class MonteCarloSimulationController {
    @Autowired
    private ExcelService excelService;

    @Autowired
    private MonteCarloService monteCarloService;

    @Autowired
    RestTemplate restTemplate;

    @Value("${OPORTEXT_GENEXCEL_HOST:localhost}")
    private String genExcelHost;

    @Value("${OPORTEXT_GENEXCEL_POST:3000}")
    private String genExcelPort;

    @GetMapping("/run")
    public ResponseEntity<?> runSimulation(@RequestParam("Version") String version,
            @RequestParam("IdOportunidad") int idOportunidadObjetivo) {
        MonteCarloSimulation monteCarloSimulation = monteCarloService.createSimulation(version, idOportunidadObjetivo);

        try {
            // Ejecuta la simulación y obtiene los datos
            List<Object> resultados = monteCarloSimulation.runSimulation().getBody();

            // System.err.println("Data sent to generate-excel: {}" + resultados);

            // URL del servidor Node.js
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

    @GetMapping("/runJson")
    public ResponseEntity<?> runSimulationJson(@RequestParam("Version") String version,
            @RequestParam("IdOportunidad") int idOportunidadObjetivo) {

        MonteCarloSimulation monteCarloSimulation = new MonteCarloSimulation(version, idOportunidadObjetivo);

        try {
            // Ejecuta la simulación y obtiene los datos
            List<Object> resultados = monteCarloSimulation.runSimulation().getBody();

            // Devuelve los resultados directamente
            return ResponseEntity.ok(resultados);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error en la simulación: " + e.getMessage());
        }
    }
}
