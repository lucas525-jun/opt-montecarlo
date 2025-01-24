package com.pemex.oportexp.Controller;

import com.pemex.oportexp.Services.ExcelService;
import com.pemex.oportexp.Services.MonteCarloSimulation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

@CrossOrigin(origins = { "http://localhost:4200", "http://localhost:5173" })

@RestController
@RequestMapping("/api/simulation")
public class MonteCarloSimulationController {
    @Autowired
    private ExcelService excelService;

    @Autowired
    RestTemplate restTemplate;

    @GetMapping("/run")
    public ResponseEntity<?> runSimulation(@RequestParam("Version") String version,
            @RequestParam("IdOportunidad") int idOportunidadObjetivo) {
        MonteCarloSimulation monteCarloSimulation = new MonteCarloSimulation(version, idOportunidadObjetivo);

        try {
            // Ejecuta la simulación y obtiene los datos
            List<Object> resultados = monteCarloSimulation.runSimulation().getBody();
            // System.out.println("Resultados: " + resultados);

            String nodeUrl = "http://localhost:3000/generate-excel";

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
                String resFileName = "Resultados_" + idOportunidadObjetivo + ".xlsx";
                fileHeaders.setContentDisposition(ContentDisposition
                        .attachment()
                        .filename(resFileName)
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
            List<Object> resultadosJson = monteCarloSimulation.runSimulation().getBody();

            // Devuelve los resultados directamente
            return ResponseEntity.ok(resultadosJson);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error en la simulación: " + e.getMessage());
        }
    }
}
