package com.pemex.oportexp.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.pemex.oportexp.DatabaseConnection;
import com.pemex.oportexp.Models.CtoAnualResultado;
import com.pemex.oportexp.Models.Oportunidad;
import com.pemex.oportexp.Models.ResultadoSimulacion;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class MonteCarloSimulation {

    private final String version;
    private final int idOportunidadObjetivo;
    private Oportunidad oportunidad;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    public void setOportunidad(Oportunidad oportunidad) {
        this.oportunidad = oportunidad;
    }

    public MonteCarloSimulation(String version, int idOportunidadObjetivo) {

        this.version = version;
        this.idOportunidadObjetivo = idOportunidadObjetivo;

    }

    long startSimulacion = System.nanoTime();

    double triangularInversionBat, triangularInversionPlataforma, triangularInversionLineaDescarga;
    double triangularInversionEstacionCompresion, triangularInversionDucto, triangularInversionArbolesSubmarinos;
    double triangularInversionManifolds, triangularInversionRisers, triangularInversionSistemasDeControl;
    double triangularInversionCubiertaDeProces, triangularInversionBuqueTanqueCompra,
            triangularInversionBuqueTanqueRenta;

    public ResponseEntity<List<Object>> runSimulation() {

        DatabaseConnection databaseConnection = new DatabaseConnection();
        Oportunidad oportunidad = databaseConnection.executeQuery(version, idOportunidadObjetivo); // Atributos y
                                                                                                   // configuraciónSystem.out.println(aleatorioRecurso);
        setOportunidad(oportunidad);

        int valores = 0;
        AtomicInteger exitos = new AtomicInteger(0);
        AtomicInteger fracasos = new AtomicInteger(0);
        List<Object> resultados = Collections.synchronizedList(new ArrayList<>());

        // Crear libro y hoja en Excel
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Simulación Monte Carlo");
        createExcelHeader(sheet);

        // Iterar simulaciones
        int cores = Runtime.getRuntime().availableProcessors();
        // ForkJoinPool customThreadPool = new ForkJoinPool(Math.min(12, cores));
        ForkJoinPool customThreadPool = new ForkJoinPool(24);

        long totalStartTime = System.nanoTime();
        List<Map<Integer, Object>> excelResult = new ArrayList<>(3000);

        try {
            customThreadPool.submit(() -> IntStream.range(0, 3000).parallel().forEach(i -> {
                try {
                    Map<Integer, Object> excelRowData = new HashMap<>();
                    ResultadoSimulacion resultadoSimulacion = new ResultadoSimulacion();

                    excelRowData.put(0, i + 1); // Iteration number

                    if (pruebaGeologica()) {
                        exitos.incrementAndGet();
                        excelRowData.put(1, "Éxito");

                        // Valores aleatorios y cálculos para éxito
                        double aleatorioRecurso = 0.01 + (0.99 - 0.01) * Math.random();
                        double recurso = calcularRecursoProspectivo(aleatorioRecurso, oportunidad.getPceP10(),
                                oportunidad.getPceP90());
                        excelRowData.put(2, aleatorioRecurso);
                        excelRowData.put(3, recurso);
                        resultadoSimulacion.setPce(recurso);

                        System.out.println(aleatorioRecurso);
                        long startRecursoProspectivo = System.nanoTime();

                        long endRecursoProspectivo = System.nanoTime(); // Tiempo final para PCE
                        System.out.println("Tiempo para cálculos de PCE: "
                                + (endRecursoProspectivo - startRecursoProspectivo) / 1_000_000 + " ms");

                        long startCuota = System.nanoTime();

                        double aleatorioGasto = random.nextDouble();
                        double gastoTriangular = calcularGastoInicial(recurso, aleatorioGasto);
                        excelRowData.put(4, aleatorioGasto);
                        excelRowData.put(5, gastoTriangular);
                        resultadoSimulacion.setGastoInicial(gastoTriangular);

                        long endCuota = System.nanoTime();
                        System.out.println(
                                "Tiempo para cálculos de Cuota: " + (endCuota - startCuota) / 1_000_000 + " ms");

                        long startDeclinacion = System.nanoTime();

                        double aleatorioDeclinacion = random.nextDouble();
                        double declinacion = triangularDistribution(recurso, oportunidad.getPrimeraDeclinacionMin(),
                                oportunidad.getPrimeraDeclinacionMAX(),
                                oportunidad.getPrimeraDeclinacionMP(),
                                aleatorioDeclinacion);
                        excelRowData.put(6, aleatorioDeclinacion);
                        excelRowData.put(7, declinacion);
                        resultadoSimulacion.setDeclinacion(declinacion);

                        long endDeclinacion = System.nanoTime();

                        System.out.println("Tiempo para cálculos de declinacion: "
                                + (endDeclinacion - startDeclinacion) / 1_000_000 + " ms");

                        long startArea = System.nanoTime();

                        double aleatorioArea = 0.01 + (0.99 - 0.01) * Math.random();
                        double area = calcularRecursoProspectivo(aleatorioArea, oportunidad.getArea10(),
                                oportunidad.getArea90());
                        excelRowData.put(8, aleatorioArea);
                        excelRowData.put(9, area);
                        resultadoSimulacion.setArea(area);

                        long endArea = System.nanoTime();
                        System.out
                                .println("Tiempo para cálculos de Area: " + (endArea - startArea) / 1_000_000 + " ms");

                        long startExploratorio = System.nanoTime();

                        double aleatorioExploratorioInfra = random.nextDouble();
                        double triangularExploratorioMin = triangularDistributionSinPCE(
                                oportunidad.getInfraestructuraMin(),
                                oportunidad.getInfraestructuraMax(),
                                oportunidad.getInfraestructuraMP(),
                                aleatorioExploratorioInfra);
                        excelRowData.put(10, triangularExploratorioMin);
                        resultadoSimulacion.setExploratoriaInfra(triangularExploratorioMin);

                        double aleatorioExploratorioPerforacion = random.nextDouble();
                        double triangularExploratorioPer = triangularDistributionSinPCE(oportunidad.getPerforacionMin(),
                                oportunidad.getPerforacionMax(),
                                oportunidad.getPerforacionMP(),
                                aleatorioExploratorioPerforacion);
                        excelRowData.put(11, triangularExploratorioPer);
                        resultadoSimulacion.setExploratoriaPerf(triangularExploratorioPer);

                        double aleatorioExploratorioTerminacion = random.nextDouble();
                        double triangularExploratorioTer = triangularDistributionSinPCE(oportunidad.getTerminacionMin(),
                                oportunidad.getTerminacionMax(),
                                oportunidad.getTerminacionMP(),
                                aleatorioExploratorioTerminacion);
                        excelRowData.put(12, triangularExploratorioTer);
                        resultadoSimulacion.setExploratoriaTer(triangularExploratorioTer);

                        long endExploratorio = System.nanoTime();
                        System.out.println("Tiempo para cálculos de Exploratorio: "
                                + (endExploratorio - startExploratorio) / 1_000_000 + " ms");

                        long startDesarrollo = System.nanoTime();

                        double aleatorioDesInfra = random.nextDouble();
                        double triangularDESInfra = triangularDistribution(recurso,
                                oportunidad.getInfraestructuraMinDES(),
                                oportunidad.getInfraestructuraMaxDES(),
                                oportunidad.getInfraestructuraMPDES(),
                                aleatorioDesInfra);
                        excelRowData.put(13, triangularDESInfra);
                        resultadoSimulacion.setDesarrolloInfra(triangularDESInfra);

                        double aleatorioDesPer = random.nextDouble();
                        double triangularDESPer = triangularDistribution(recurso, oportunidad.getPerforacionMinDES(),
                                oportunidad.getPerforacionMaxDES(),
                                oportunidad.getPerforacionMPDES(),
                                aleatorioDesPer);
                        excelRowData.put(14, triangularDESPer);
                        resultadoSimulacion.setDesarrolloPerf(triangularDESPer);

                        double aleatorioDesTer = random.nextDouble();
                        double triangularDESTer = triangularDistribution(recurso, oportunidad.getTerminacionMinDES(),
                                oportunidad.getTerminacionMaxDES(),
                                oportunidad.getTerminacionMPDES(),
                                aleatorioDesTer);
                        excelRowData.put(15, triangularDESTer);
                        resultadoSimulacion.setDesarrolloTer(triangularDESTer);

                        long endDesarrollo = System.nanoTime();
                        System.out.println(
                                "Tiempo para cálculos de Desarrollo: " + (endDesarrollo - startDesarrollo) / 1_000_000
                                        + " ms");

                        long startInversion = System.nanoTime();

                        if (oportunidad.getInversionOportunidad().getBateriaMin() != 0) {
                            double AleatorioInverision = random.nextDouble();
                            triangularInversionBat = triangularDistribution(recurso,
                                    oportunidad.getInversionOportunidad().getBateriaMin(),
                                    oportunidad.getInversionOportunidad().getBateriaMax(),
                                    oportunidad.getInversionOportunidad().getBateriaMp(),
                                    AleatorioInverision);
                            excelRowData.put(16, triangularInversionBat);
                            resultadoSimulacion.setBateria(triangularInversionBat);
                        }

                        if (oportunidad.getInversionOportunidad().getPlataformadesarrolloMin() != 0) {
                            double AleatorioInverision = random.nextDouble();
                            triangularInversionPlataforma = triangularDistribution(recurso,
                                    oportunidad.getInversionOportunidad().getPlataformadesarrolloMin(),
                                    oportunidad.getInversionOportunidad().getPlataformadesarrolloMax(),
                                    oportunidad.getInversionOportunidad().getPlataformadesarrolloMp(),
                                    AleatorioInverision);
                            excelRowData.put(17, triangularInversionPlataforma);
                            resultadoSimulacion.setPlataformaDesarrollo(triangularInversionPlataforma);
                        }

                        if (oportunidad.getInversionOportunidad().getLineadedescargaMin() != 0) {
                            double AleatorioInverision = random.nextDouble();
                            triangularInversionLineaDescarga = triangularDistribution(recurso,
                                    oportunidad.getInversionOportunidad().getLineadedescargaMin(),
                                    oportunidad.getInversionOportunidad().getLineadedescargaMax(),
                                    oportunidad.getInversionOportunidad().getLineadedescargaMp(),
                                    AleatorioInverision);
                            excelRowData.put(18, triangularInversionLineaDescarga);
                            resultadoSimulacion.setLineaDescarga(triangularInversionLineaDescarga);
                        }

                        if (oportunidad.getInversionOportunidad().getEstacioncompresionMin() != 0) {
                            double AleatorioInverision = random.nextDouble();
                            triangularInversionEstacionCompresion = triangularDistribution(recurso,
                                    oportunidad.getInversionOportunidad().getEstacioncompresionMin(),
                                    oportunidad.getInversionOportunidad().getEstacioncompresionMax(),
                                    oportunidad.getInversionOportunidad().getEstacioncompresionMp(),
                                    AleatorioInverision);
                            excelRowData.put(19, triangularInversionEstacionCompresion);
                            resultadoSimulacion.setEComprension(triangularInversionEstacionCompresion);
                        }

                        if (oportunidad.getInversionOportunidad().getDuctoMin() != 0) {
                            double AleatorioInverision = random.nextDouble();
                            triangularInversionDucto = triangularDistribution(recurso,
                                    oportunidad.getInversionOportunidad().getDuctoMin(),
                                    oportunidad.getInversionOportunidad().getDuctoMax(),
                                    oportunidad.getInversionOportunidad().getDuctoMp(),
                                    AleatorioInverision);
                            excelRowData.put(20, triangularInversionDucto);
                            resultadoSimulacion.setDucto(triangularInversionDucto);
                        }

                        if (oportunidad.getInversionOportunidad().getArbolessubmarinosMin() != 0) {
                            double AleatorioInversion = random.nextDouble();
                            triangularInversionArbolesSubmarinos = triangularDistribution(recurso,
                                    oportunidad.getInversionOportunidad().getArbolessubmarinosMin(),
                                    oportunidad.getInversionOportunidad().getArbolessubmarinosMax(),
                                    oportunidad.getInversionOportunidad().getArbolessubmarinosMp(),
                                    AleatorioInversion);
                            excelRowData.put(21, triangularInversionArbolesSubmarinos);
                            resultadoSimulacion.setArbolesSubmarinos(triangularInversionArbolesSubmarinos);
                        }

                        if (oportunidad.getInversionOportunidad().getManifoldsMin() != 0) {
                            double AleatorioInversion = random.nextDouble();
                            triangularInversionManifolds = triangularDistribution(recurso,
                                    oportunidad.getInversionOportunidad().getManifoldsMin(),
                                    oportunidad.getInversionOportunidad().getManifoldsMax(),
                                    oportunidad.getInversionOportunidad().getManifoldsMp(),
                                    AleatorioInversion);
                            excelRowData.put(22, triangularInversionManifolds);
                            resultadoSimulacion.setManifolds(triangularInversionManifolds);
                        }

                        if (oportunidad.getInversionOportunidad().getRisersMin() != 0) {
                            double AleatorioInversion = random.nextDouble();
                            triangularInversionRisers = triangularDistribution(recurso,
                                    oportunidad.getInversionOportunidad().getRisersMin(),
                                    oportunidad.getInversionOportunidad().getRisersMax(),
                                    oportunidad.getInversionOportunidad().getRisersMp(),
                                    AleatorioInversion);
                            excelRowData.put(23, triangularInversionRisers);
                            resultadoSimulacion.setRisers(triangularInversionRisers);
                        }

                        if (oportunidad.getInversionOportunidad().getSistemasdecontrolMin() != 0) {
                            double AleatorioInversion = random.nextDouble();
                            triangularInversionSistemasDeControl = triangularDistribution(recurso,
                                    oportunidad.getInversionOportunidad().getSistemasdecontrolMin(),
                                    oportunidad.getInversionOportunidad().getSistemasdecontrolMax(),
                                    oportunidad.getInversionOportunidad().getSistemasdecontrolMp(),
                                    AleatorioInversion);
                            excelRowData.put(24, triangularInversionSistemasDeControl);
                            resultadoSimulacion.setSistemasDeControl(triangularInversionSistemasDeControl);
                        }

                        if (oportunidad.getInversionOportunidad().getCubiertadeprocesMin() != 0) {
                            double AleatorioInversion = random.nextDouble();
                            triangularInversionCubiertaDeProces = triangularDistribution(recurso,
                                    oportunidad.getInversionOportunidad().getCubiertadeprocesMin(),
                                    oportunidad.getInversionOportunidad().getCubiertadeprocesMax(),
                                    oportunidad.getInversionOportunidad().getCubiertadeprocesMp(),
                                    AleatorioInversion);
                            excelRowData.put(25, triangularInversionCubiertaDeProces);
                            resultadoSimulacion.setCubiertaDeProces(triangularInversionCubiertaDeProces);
                        }

                        if (oportunidad.getInversionOportunidad().getBuquetanquecompraMin() != 0) {
                            double AleatorioInversion = random.nextDouble();
                            triangularInversionBuqueTanqueCompra = triangularDistribution(recurso,
                                    oportunidad.getInversionOportunidad().getBuquetanquecompraMin(),
                                    oportunidad.getInversionOportunidad().getBuquetanquecompraMax(),
                                    oportunidad.getInversionOportunidad().getBuquetanquecompraMp(),
                                    AleatorioInversion);
                            excelRowData.put(26, triangularInversionBuqueTanqueCompra);
                            resultadoSimulacion.setBuqueTanqueCompra(triangularInversionBuqueTanqueCompra);
                        }

                        if (oportunidad.getInversionOportunidad().getBuquetanquerentaMin() != 0) {
                            double AleatorioInversion = random.nextDouble();
                            triangularInversionBuqueTanqueRenta = triangularDistribution(recurso,
                                    oportunidad.getInversionOportunidad().getBuquetanquerentaMin(),
                                    oportunidad.getInversionOportunidad().getBuquetanquerentaMax(),
                                    oportunidad.getInversionOportunidad().getBuquetanquerentaMp(),
                                    AleatorioInversion);
                            excelRowData.put(27, triangularInversionBuqueTanqueRenta);
                            resultadoSimulacion.setBuqueTanqueRenta(triangularInversionBuqueTanqueRenta);
                        }

                        long endInversion = System.nanoTime();
                        System.out.println(
                                "Tiempo para cálculos de Inversion: " + (endInversion - startInversion) / 1_000_000
                                        + " ms");

                        long startProduction = System.nanoTime();
                        // Llamada a productionQuery y almacenamiento del resultado
                        Map<Integer, Double> produccionAnualMap = databaseConnection.executeProductionQuery(42, 2643,
                                gastoTriangular, declinacion, recurso, area);

                        produccionAnualMap.forEach((anio, ctoAnual) -> excelRowData.put(anio, ctoAnual));

                        long endProduction = System.nanoTime();
                        System.out.println("Tiempo para cálculos de ProductionQuery: "
                                + (endProduction - startProduction) / 1_000_000 + " ms");
                        RestTemplate restTemplate = new RestTemplate();

                        long startEvaluacionEconomica = System.nanoTime();

                        SimulacionMicros simulacionMicros = new SimulacionMicros(idOportunidadObjetivo,
                                oportunidad.getActualIdVersion(), gastoTriangular, declinacion, recurso, area,
                                triangularInversionPlataforma, triangularInversionLineaDescarga,
                                triangularInversionEstacionCompresion, triangularInversionDucto,
                                triangularInversionBat,
                                triangularExploratorioMin, triangularExploratorioPer, triangularExploratorioTer,
                                triangularDESInfra, triangularDESPer, triangularDESTer,
                                triangularInversionArbolesSubmarinos,
                                triangularInversionManifolds, triangularInversionRisers,
                                triangularInversionSistemasDeControl, triangularInversionCubiertaDeProces,
                                triangularInversionBuqueTanqueCompra, triangularInversionBuqueTanqueRenta,
                                restTemplate);

                        Object resultado = simulacionMicros.ejecutarSimulacion();

                        long endEvaluacionEconomica = System.nanoTime();

                        System.out.println("Tiempo para cálculos de EvaluacionEconomica: "
                                + (endEvaluacionEconomica - startEvaluacionEconomica) / 1_000_000 + " ms");
                        resultados.add(resultado);
                    } else {
                        fracasos.incrementAndGet();
                        excelRowData.put(1, "Fracaso");
                        IntStream.range(2, 8).forEach(j -> excelRowData.put(j, 0));

                        double aleatorioExploratorioInfra = random.nextDouble();
                        double triangularExploratorioMin = triangularDistributionSinPCE(
                                oportunidad.getInfraestructuraMin(),
                                oportunidad.getInfraestructuraMax(),
                                oportunidad.getInfraestructuraMP(),
                                aleatorioExploratorioInfra);
                        excelRowData.put(10, triangularExploratorioMin);
                        resultadoSimulacion.setExploratoriaInfra(triangularExploratorioMin);

                        double aleatorioExploratorioPerforacion = random.nextDouble();
                        double triangularExploratorioPer = triangularDistributionSinPCE(oportunidad.getPerforacionMin(),
                                oportunidad.getPerforacionMax(),
                                oportunidad.getPerforacionMP(),
                                aleatorioExploratorioPerforacion);
                        excelRowData.put(11, triangularExploratorioPer);
                        resultadoSimulacion.setExploratoriaPerf(triangularExploratorioPer);

                        double aleatorioExploratorioTerminacion = random.nextDouble();
                        double triangularExploratorioTer = triangularDistributionSinPCE(oportunidad.getTerminacionMin(),
                                oportunidad.getTerminacionMax(),
                                oportunidad.getTerminacionMP(),
                                aleatorioExploratorioTerminacion);
                        excelRowData.put(12, triangularExploratorioTer);
                        resultadoSimulacion.setExploratoriaTer(triangularExploratorioTer);

                        RestTemplate restTemplate = new RestTemplate();

                        long startEvaluacionEconomica = System.nanoTime();
                        SimulacionMicros simulacionMicros = new SimulacionMicros(idOportunidadObjetivo,
                                oportunidad.getActualIdVersion(), 0, 0, 0, 0,
                                0, 0, 0, 0,
                                0, triangularExploratorioMin, triangularExploratorioPer, triangularExploratorioTer,
                                0, 0, 0, 0,
                                0, 0, 0,
                                0, 0,
                                0, restTemplate);
                        Object resultado = simulacionMicros.ejecutarSimulacion();

                        long endEvaluacionEconomica = System.nanoTime();
                        System.out.println("Tiempo para cálculos de EvaluacionEconomica FRACASO: "
                                + (endEvaluacionEconomica - startEvaluacionEconomica) / 1_000_000 + " ms");

                    }
                    excelResult.add(excelRowData);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            })).join();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            customThreadPool.shutdown();
            try {
                if (!customThreadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                    customThreadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                customThreadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("Éxitos: " + exitos);
        System.out.println("Fracasos: " + fracasos);

        // System.out.println("Éxitos: " + exitos.get());
        // System.out.println("Fracasos: " + fracasos.get());

        excelResult.forEach(row -> writeResultsToExcel(sheet, row));
        saveJsonFile(resultados, "resultados.json");
        long totalEndTime = System.nanoTime();
        System.out.println("a_time: "
                + (totalEndTime - totalStartTime) / 1000000 / 1000 + " s");

        try (FileOutputStream fos = new FileOutputStream("SimulacionMonteCarlo_" + idOportunidadObjetivo + ".xlsx")) {
            workbook.write(fos);
            workbook.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ResponseEntity.ok(resultados);
    }

    private boolean pruebaGeologica() {
        return random.nextDouble() <= oportunidad.getPg();
    }

    private void createExcelHeader(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        String[] headers = { "Iteración", "Resultado", "Aleatorio PCE", "PCE", "Aleatorio Gasto", "Gasto Inicial",
                "Aleatorio Declinación", "Declinación" };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            sheet.setColumnWidth(i, 5000);
        }
    }

    private void writeResultsToExcel(Sheet sheet, Map<Integer, Object> rowData) {
        Row row = sheet.createRow(sheet.getPhysicalNumberOfRows());
        rowData.forEach((colIndex, value) -> {
            Cell cell = row.createCell(colIndex);
            if (value instanceof Number) {
                cell.setCellValue(((Number) value).doubleValue());
            } else {
                cell.setCellValue(value.toString());
            }
        });
    }

    private void saveJsonFile(List<Object> data, String fileName) {
        ObjectMapper objectMapper = new ObjectMapper();
        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            objectMapper.writeValue(fos, data);
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    public double calcularGastoInicial(double PCE, double aleatorioGasto) {

        double gastoInicialMin = 0.0;
        double gastoInicialMP = 0.0;
        double gastoInicialMax = 0.0;

        if (oportunidad.getIdHidrocarburo() == 1 || oportunidad.getIdHidrocarburo() == 2
                || oportunidad.getIdHidrocarburo() == 4 || oportunidad.getIdHidrocarburo() == 6) {

            gastoInicialMin = oportunidad.getGastoMINAceite() / oportunidad.getFcAceite();
            gastoInicialMP = oportunidad.getGastoMPAceite() / oportunidad.getFcAceite();
            gastoInicialMax = oportunidad.getGastoMAXAceite() / oportunidad.getFcAceite();

        } else if (oportunidad.getIdHidrocarburo() == 3 || oportunidad.getIdHidrocarburo() == 5
                || oportunidad.getIdHidrocarburo() == 7 || oportunidad.getIdHidrocarburo() == 9) {

            gastoInicialMin = oportunidad.getGastoMINAceite() / oportunidad.getFcGas();
            gastoInicialMP = oportunidad.getGastoMPAceite() / oportunidad.getFcGas();
            gastoInicialMax = oportunidad.getGastoMAXAceite() / oportunidad.getFcGas();

        } else {

            gastoInicialMin = oportunidad.getGastoMINAceite() / oportunidad.getFcCondensado();
            gastoInicialMP = oportunidad.getGastoMPAceite() / oportunidad.getFcCondensado();
            gastoInicialMax = oportunidad.getGastoMAXAceite() / oportunidad.getFcCondensado();

        }

        return triangularDistribution(PCE, gastoInicialMin, gastoInicialMax, gastoInicialMP, aleatorioGasto);
    }

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

    private int EncuentraOCreaColumna(Row headerRow, int year) {
        // Buscar si el año ya existe en alguna celda del encabezado
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && cell.getCellType() == CellType.NUMERIC && cell.getNumericCellValue() == year) {
                return i; // Retornar el índice de la columna existente
            }
        }

        // Si no se encuentra, crear una nueva columna
        int newColumnIndex = headerRow.getPhysicalNumberOfCells(); // Usa las celdas físicas ocupadas
        Cell newCell = headerRow.createCell(newColumnIndex);
        newCell.setCellValue(year);
        return newColumnIndex;
    }

}
