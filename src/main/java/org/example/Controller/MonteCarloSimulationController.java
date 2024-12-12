package org.example.Controller;


import org.apache.poi.ss.usermodel.Workbook;

import org.example.Models.OportunidadExcel;
import org.example.Models.ResultadoSimulacion;
import org.example.Services.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;



import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;



import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:5173"})



@RestController
@RequestMapping("/api/simulation")
public class MonteCarloSimulationController {


    @Autowired
    private ExcelService excelService;

    @Autowired
    RestTemplate restTemplate;

    private SimulacionExploratoria exploratoria = new SimulacionExploratoria() ;
    private SimulacionDesarrollo desarrollo = new SimulacionDesarrollo() ;
    private InformacionDeInversion informacionDeInversion = new InformacionDeInversion() ;


    @GetMapping("/run")
    public ResponseEntity<?> runSimulation(@RequestParam("IdVersion") int idVersion, @RequestParam("IdOportunidad") int idOportunidadObjetivo) {





         MonteCarloSimulation monteCarloSimulation = new MonteCarloSimulation(idVersion, idOportunidadObjetivo) ;

        try {
            // Ejecuta la simulación y obtiene los datos
            List<Object> resultados = monteCarloSimulation.runSimulation().getBody();

            // URL del servidor Node.js
            String nodeUrl = "http://host.docker.internal:3000/generate-excel";

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
                    byte[].class
            );

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
                    .body("Error en la simulación o generación del Excel: " + e.getMessage());
        }
    }


    @GetMapping("/runExploratoria")
    public ResponseEntity<List<ResultadoSimulacion>> runSimulationExploratoria( ){
        List<ResultadoSimulacion> resultados = exploratoria.runSimulation().getBody();
        return ResponseEntity.ok(resultados);

    }

    @GetMapping("/runDesarrollo")
    public ResponseEntity<List<ResultadoSimulacion>> runSimulationDesarrollo( ){
        List<ResultadoSimulacion> resultados = desarrollo.runSimulation().getBody();
        return ResponseEntity.ok(resultados);

    }


    @GetMapping("/infoInversion")
    public ResponseEntity<List<ResultadoSimulacion>> runSimulationInfoInversion( ){
        List<ResultadoSimulacion> resultados = informacionDeInversion.runSimulation().getBody();

        return ResponseEntity.ok(resultados);

    }


    @PostMapping("/generate-excel")
    public ResponseEntity<Resource> generateExcel(@RequestBody List<OportunidadExcel> oportunidades) throws IOException {
        Workbook workbook = excelService.generateExcel(oportunidades);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ByteArrayResource resource = new ByteArrayResource(out.toByteArray());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=datos.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }







}
