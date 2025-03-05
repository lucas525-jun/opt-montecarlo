package com.pemex.oportexp.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.pemex.oportexp.Models.Oportunidad;
import com.pemex.oportexp.Models.ResultadoSimulacion;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MonteCarloSimulation {
    @Setter
    private MonteCarloDAO monteCarloDAO;
    private String economicEvaHost;
    private String economicEvaPort;
    private final String version;
    private final int idOportunidadObjetivo;
    private Oportunidad oportunidad;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    private int exitos = 0;
    private int fracasos = 0;
    private int valores = 0;

    public void setOportunidad(Oportunidad oportunidad) {
        this.oportunidad = oportunidad;
    }

    public MonteCarloSimulation(String version, int idOportunidadObjetivo) {
        this.version = version;
        this.idOportunidadObjetivo = idOportunidadObjetivo;
    }

    public void setEconomicEvaHostAndPort(String host, String port) {
        this.economicEvaHost = host;
        this.economicEvaPort = port;
    }

    double triangularInversionBat, triangularInversionPlataforma, triangularInversionLineaDescarga;
    double triangularInversionEstacionCompresion, triangularInversionDucto, triangularInversionArbolesSubmarinos;
    double triangularInversionManifolds, triangularInversionRisers, triangularInversionSistemasDeControl;
    double triangularInversionCubiertaDeProces, triangularInversionBuqueTanqueCompra,
            triangularInversionBuqueTanqueRenta;

    public ResponseEntity<List<Object>> runSimulation() {
        Oportunidad oportunidad = monteCarloDAO.executeQuery(version, idOportunidadObjetivo);
        setOportunidad(oportunidad);

        int cantidadIteraciones = 3000;

        int indexAleatorioRecurso = 0;
        int indexAleatorioGasto = 1;
        int indexAleatorioDeclinacion = 2;
        int indexAleatorioArea = 3;
        int indexAleatorioDesInfra = 4;
        int indexAleatorioDesPer = 5;
        int indexAleatorioDesTer = 6;
        int indexBateriaMin = 7;
        int indexAleatorioInverision = 8;
        int indexAleatorioLineadedescarga = 9;
        int indexAleatorioEstacioncompresion = 10;
        int indexAleatorioDucto = 11;
        int indexAleatorioArbolessubmarinos = 12;
        int indexAleatorioManifolds = 13;
        int indexAleatorioRisers = 14;
        int indexAleatorioSistemasdecontrol = 15;
        int indexAleatorioCubiertadeproces = 16;
        int indexAleatorioBuquetanquecompra = 17;
        int indexAleatorioBuquetanquerenta = 18;

        int numberOfVariables = 23; // Total variables requiring random numbers
        List<Double> randomNumbers = IntStream.range(0, cantidadIteraciones * numberOfVariables)
                .mapToDouble(i -> ThreadLocalRandom.current().nextDouble())
                .boxed()
                .collect(Collectors.toList());

        double mediaTruncada = monteCarloDAO.getMediaTruncada(version, idOportunidadObjetivo);
        double kilometraje = monteCarloDAO.getKilometraje(version, idOportunidadObjetivo);

        List<Object> resultados = Collections.synchronizedList(new ArrayList<>());
        Map<Double, Integer> limitesEconomicosRepetidos = new ConcurrentHashMap<>();

        // Crear libro y hoja en Excel
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Simulación Monte Carlo");

        // Crear encabezado con columnas adicionales para "Años" y "CtoAnuales"
        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "Iteración", "Resultado", "Aleatorio PCE", "PCE",
                "Aleatorio Gasto", "Gasto Inicial (mbpce)",
                "Aleatorio Declinación", "Declinación",
                "Aleatorio Área", "Área", "E.I.", "E.P", "E.T", "D.I", "D.P", "D.T", "Bateria", "Plataforma Desarrollo",
                "Linea Descarga", "E.comprension ", "ducto", "Arboles submarinos", "manifols", "risers",
                "Sistemas de control", "Cubierta Proces", "Buquetaquecompra", "buquetaQuerenta" // Añadido aquí
        };

        IndexedColors[] colors = {
                IndexedColors.LIGHT_BLUE, IndexedColors.GREY_25_PERCENT,
                IndexedColors.LIGHT_GREEN, IndexedColors.LIGHT_YELLOW,
                IndexedColors.LIGHT_ORANGE, IndexedColors.LIGHT_CORNFLOWER_BLUE,
                IndexedColors.LIGHT_TURQUOISE, IndexedColors.BROWN, IndexedColors.GOLD
        };

        for (int i = 0; i < headers.length; i++) {
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(colors[i % colors.length].getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 5000); // Ajusta el ancho de las columnas
        }

        // Calculate exploratory values once, outside the loop
        double aleatorioExploratorioInfra = random.nextDouble();
        double triangularExploratorioMin = triangularDistributionSinPCE(
                oportunidad.getInfraestructuraMin(),
                oportunidad.getInfraestructuraMax(),
                oportunidad.getInfraestructuraMP(),
                aleatorioExploratorioInfra);

        double aleatorioExploratorioPerforacion = random.nextDouble();
        double triangularExploratorioPer = triangularDistributionSinPCE(
                oportunidad.getPerforacionMin(),
                oportunidad.getPerforacionMax(),
                oportunidad.getPerforacionMP(),
                aleatorioExploratorioPerforacion);

        double aleatorioExploratorioTerminacion = random.nextDouble();
        double triangularExploratorioTer = triangularDistributionSinPCE(
                oportunidad.getTerminacionMin(),
                oportunidad.getTerminacionMax(),
                oportunidad.getTerminacionMP(),
                aleatorioExploratorioTerminacion);

        // ForkJoinPool customThreadPool = new ForkJoinPool(24);
        int optimalThreads = Runtime.getRuntime().availableProcessors() * 2;

        ForkJoinPool customThreadPool = new ForkJoinPool(optimalThreads);

        Queue<Object> resultadosQueue = new ConcurrentLinkedQueue<>();

        List<Map<Integer, Object>> excelRowBuffer = Collections.synchronizedList(new ArrayList<>());

        try {
            customThreadPool.submit(() -> IntStream.range(0, cantidadIteraciones).parallel().forEach(i -> {
                Map<Integer, Object> excelRowData = new HashMap<>();
                valores++;
                int baseIndex = i * numberOfVariables; // Start index for this iteration

                // System.out.println("Valores: " + valores);
                ResultadoSimulacion ResultadoSimulacion = new ResultadoSimulacion();
                excelRowData.put(0, i + 1); // Iteration number

                if (pruebaGeologica()) {
                    exitos++;
                    excelRowData.put(1, "Éxito");

                    // Valores aleatorios y cálculos para éxito
                    // Same aleatorio for Recurso, Area and GastoInicial(Cuota)
                    double aleatorioRecurso = 0.01 + (0.99 - 0.01) * randomNumbers.get(baseIndex + indexAleatorioRecurso);

                    double recurso = calcularRecursoProspectivo(aleatorioRecurso, oportunidad.getPceP10(),
                            oportunidad.getPceP90());
                    excelRowData.put(2, aleatorioRecurso);
                    excelRowData.put(3, recurso);
                    ResultadoSimulacion.setPce(recurso);

                    double limiteEconomico = calcularLimiteEconomico(recurso) / 12;
                    limitesEconomicosRepetidos.merge(limiteEconomico, 1, Integer::sum);

                    double gastoTriangular = calcularGastoInicial(recurso, aleatorioRecurso);
                    excelRowData.put(4, aleatorioRecurso);
                    excelRowData.put(5, gastoTriangular);
                    ResultadoSimulacion.setGastoInicial(gastoTriangular);

                    double aleatorioDeclinacion = randomNumbers.get(baseIndex + indexAleatorioDeclinacion);

                    double declinacion = triangularDistribution(recurso, oportunidad.getPrimeraDeclinacionMin(),
                            oportunidad.getPrimeraDeclinacionMAX(),
                            oportunidad.getPrimeraDeclinacionMP(),
                            aleatorioDeclinacion);
                    excelRowData.put(6, aleatorioDeclinacion);
                    excelRowData.put(7, declinacion);

                    ResultadoSimulacion.setDeclinacion(declinacion);

                    double area = calcularRecursoProspectivo(aleatorioRecurso, oportunidad.getArea10(),
                            oportunidad.getArea90());
                    excelRowData.put(8, aleatorioRecurso);
                    excelRowData.put(9, area);

                    ResultadoSimulacion.setArea(area);

                    excelRowData.put(10, triangularExploratorioMin);
                    ResultadoSimulacion.setExploratoriaInfra(triangularExploratorioMin);

                    excelRowData.put(11, triangularExploratorioPer);
                    ResultadoSimulacion.setExploratoriaPerf(triangularExploratorioPer);

                    excelRowData.put(12, triangularExploratorioTer);
                    ResultadoSimulacion.setExploratoriaTer(triangularExploratorioTer);

                    double aleatorioDesInfra = randomNumbers.get(baseIndex + indexAleatorioDesInfra);

                    double triangularDESInfra = triangularDistribution(recurso, oportunidad.getInfraestructuraMinDES(),
                            oportunidad.getInfraestructuraMaxDES(),
                            oportunidad.getInfraestructuraMPDES(),
                            aleatorioDesInfra);
                    excelRowData.put(13, triangularDESInfra);
                    ResultadoSimulacion.setDesarrolloInfra(triangularDESInfra);

                    double aleatorioDesPer = randomNumbers.get(baseIndex + indexAleatorioDesPer);
                    double triangularDESPer = triangularDistribution(recurso, oportunidad.getPerforacionMinDES(),
                            oportunidad.getPerforacionMaxDES(),
                            oportunidad.getPerforacionMPDES(),
                            aleatorioDesPer);
                    excelRowData.put(14, triangularDESPer);
                    ResultadoSimulacion.setDesarrolloPerf(triangularDESPer);

                    double aleatorioDesTer = randomNumbers.get(baseIndex + indexAleatorioDesTer);

                    double triangularDESTer = triangularDistribution(recurso, oportunidad.getTerminacionMinDES(),
                            oportunidad.getTerminacionMaxDES(),
                            oportunidad.getTerminacionMPDES(),
                            aleatorioDesTer);
                    excelRowData.put(15, triangularDESTer);
                    ResultadoSimulacion.setDesarrolloTer(triangularDESTer);

                    if (oportunidad.getInversionOportunidad().getBateriaMin() != 0) {
                        double AleatorioInverision = randomNumbers.get(baseIndex + indexBateriaMin);

                        triangularInversionBat = triangularDistribution(recurso,
                                oportunidad.getInversionOportunidad().getBateriaMin(),
                                oportunidad.getInversionOportunidad().getBateriaMax(),
                                oportunidad.getInversionOportunidad().getBateriaMp(),
                                AleatorioInverision);
                        excelRowData.put(16, triangularInversionBat);
                        ResultadoSimulacion.setBateria(triangularInversionBat);
                    }

                    if (oportunidad.getInversionOportunidad().getPlataformadesarrolloMin() != 0) {
                        double AleatorioInverision = randomNumbers.get(baseIndex + indexAleatorioInverision);

                        triangularInversionPlataforma = triangularDistribution(recurso,
                                oportunidad.getInversionOportunidad().getPlataformadesarrolloMin(),
                                oportunidad.getInversionOportunidad().getPlataformadesarrolloMax(),
                                oportunidad.getInversionOportunidad().getPlataformadesarrolloMp(),
                                AleatorioInverision);
                        excelRowData.put(17, triangularInversionPlataforma);
                        ResultadoSimulacion.setPlataformaDesarrollo(triangularInversionPlataforma);
                    }

                    if (oportunidad.getInversionOportunidad().getLineadedescargaMin() != 0) {
                        double AleatorioInverision = randomNumbers.get(baseIndex + indexAleatorioLineadedescarga);

                        triangularInversionLineaDescarga = triangularDistribution(recurso,
                                oportunidad.getInversionOportunidad().getLineadedescargaMin(),
                                oportunidad.getInversionOportunidad().getLineadedescargaMax(),
                                oportunidad.getInversionOportunidad().getLineadedescargaMp(),
                                AleatorioInverision);
                        excelRowData.put(18, triangularInversionLineaDescarga);
                        ResultadoSimulacion.setLineaDescarga(triangularInversionLineaDescarga);
                    }

                    if (oportunidad.getInversionOportunidad().getEstacioncompresionMin() != 0) {
                        double AleatorioInverision = randomNumbers.get(baseIndex + indexAleatorioEstacioncompresion);

                        triangularInversionEstacionCompresion = triangularDistribution(recurso,
                                oportunidad.getInversionOportunidad().getEstacioncompresionMin(),
                                oportunidad.getInversionOportunidad().getEstacioncompresionMax(),
                                oportunidad.getInversionOportunidad().getEstacioncompresionMp(),
                                AleatorioInverision);
                        excelRowData.put(19, triangularInversionEstacionCompresion);
                        ResultadoSimulacion.setEComprension(triangularInversionEstacionCompresion);
                    }

                    if (oportunidad.getInversionOportunidad().getDuctoMin() != 0) {
                        double AleatorioInverision = randomNumbers.get(baseIndex + indexAleatorioDucto);

                        triangularInversionDucto = triangularDistribution(recurso,
                                oportunidad.getInversionOportunidad().getDuctoMin(),
                                oportunidad.getInversionOportunidad().getDuctoMax(),
                                oportunidad.getInversionOportunidad().getDuctoMp(),
                                AleatorioInverision);
                        excelRowData.put(20, triangularInversionDucto);
                        ResultadoSimulacion.setDucto(triangularInversionDucto);
                    }

                    if (oportunidad.getInversionOportunidad().getArbolessubmarinosMin() != 0) {
                        double AleatorioInversion = randomNumbers.get(baseIndex + indexAleatorioArbolessubmarinos);

                        triangularInversionArbolesSubmarinos = triangularDistribution(recurso,
                                oportunidad.getInversionOportunidad().getArbolessubmarinosMin(),
                                oportunidad.getInversionOportunidad().getArbolessubmarinosMax(),
                                oportunidad.getInversionOportunidad().getArbolessubmarinosMp(),
                                AleatorioInversion);
                        excelRowData.put(21, triangularInversionArbolesSubmarinos);
                        ResultadoSimulacion.setArbolesSubmarinos(triangularInversionArbolesSubmarinos);
                    }

                    if (oportunidad.getInversionOportunidad().getManifoldsMin() != 0) {
                        double AleatorioInversion = randomNumbers.get(baseIndex + indexAleatorioManifolds);

                        triangularInversionManifolds = triangularDistribution(recurso,
                                oportunidad.getInversionOportunidad().getManifoldsMin(),
                                oportunidad.getInversionOportunidad().getManifoldsMax(),
                                oportunidad.getInversionOportunidad().getManifoldsMp(),
                                AleatorioInversion);
                        excelRowData.put(22, triangularInversionManifolds);
                        ResultadoSimulacion.setManifolds(triangularInversionManifolds);
                    }

                    if (oportunidad.getInversionOportunidad().getRisersMin() != 0) {
                        double AleatorioInversion = randomNumbers.get(baseIndex + indexAleatorioRisers);

                        triangularInversionRisers = triangularDistribution(recurso,
                                oportunidad.getInversionOportunidad().getRisersMin(),
                                oportunidad.getInversionOportunidad().getRisersMax(),
                                oportunidad.getInversionOportunidad().getRisersMp(),
                                AleatorioInversion);
                        excelRowData.put(23, triangularInversionRisers);
                        ResultadoSimulacion.setRisers(triangularInversionRisers);
                    }

                    if (oportunidad.getInversionOportunidad().getSistemasdecontrolMin() != 0) {
                        double AleatorioInversion = randomNumbers.get(baseIndex + indexAleatorioSistemasdecontrol);

                        triangularInversionSistemasDeControl = triangularDistribution(recurso,
                                oportunidad.getInversionOportunidad().getSistemasdecontrolMin(),
                                oportunidad.getInversionOportunidad().getSistemasdecontrolMax(),
                                oportunidad.getInversionOportunidad().getSistemasdecontrolMp(),
                                AleatorioInversion);
                        excelRowData.put(24, triangularInversionSistemasDeControl);
                        ResultadoSimulacion.setSistemasDeControl(triangularInversionSistemasDeControl);
                    }

                    if (oportunidad.getInversionOportunidad().getCubiertadeprocesMin() != 0) {
                        double AleatorioInversion = randomNumbers.get(baseIndex + indexAleatorioCubiertadeproces);

                        triangularInversionCubiertaDeProces = triangularDistribution(recurso,
                                oportunidad.getInversionOportunidad().getCubiertadeprocesMin(),
                                oportunidad.getInversionOportunidad().getCubiertadeprocesMax(),
                                oportunidad.getInversionOportunidad().getCubiertadeprocesMp(),
                                AleatorioInversion);
                        excelRowData.put(25, triangularInversionCubiertaDeProces);
                        ResultadoSimulacion.setCubiertaDeProces(triangularInversionCubiertaDeProces);
                    }

                    if (oportunidad.getInversionOportunidad().getBuquetanquecompraMin() != 0) {
                        double AleatorioInversion = randomNumbers.get(baseIndex + indexAleatorioBuquetanquecompra);

                        triangularInversionBuqueTanqueCompra = triangularDistribution(recurso,
                                oportunidad.getInversionOportunidad().getBuquetanquecompraMin(),
                                oportunidad.getInversionOportunidad().getBuquetanquecompraMax(),
                                oportunidad.getInversionOportunidad().getBuquetanquecompraMp(),
                                AleatorioInversion);
                        excelRowData.put(26, triangularInversionBuqueTanqueCompra);
                        ResultadoSimulacion.setBuqueTanqueCompra(triangularInversionBuqueTanqueCompra);
                    }

                    if (oportunidad.getInversionOportunidad().getBuquetanquerentaMin() != 0) {
                        double AleatorioInversion = randomNumbers.get(baseIndex + indexAleatorioBuquetanquerenta);

                        triangularInversionBuqueTanqueRenta = triangularDistribution(recurso,
                                oportunidad.getInversionOportunidad().getBuquetanquerentaMin(),
                                oportunidad.getInversionOportunidad().getBuquetanquerentaMax(),
                                oportunidad.getInversionOportunidad().getBuquetanquerentaMp(),
                                AleatorioInversion);
                        excelRowData.put(27, triangularInversionBuqueTanqueRenta);
                        ResultadoSimulacion.setBuqueTanqueRenta(triangularInversionBuqueTanqueRenta);
                    }

                    RestTemplate restTemplate = new RestTemplate();

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
                            triangularInversionBuqueTanqueCompra, triangularInversionBuqueTanqueRenta, restTemplate);

                    // Set host and port for Economic Eva service
                    simulacionMicros.setEconomicEvaHost(economicEvaHost);
                    simulacionMicros.setEconomicEvaPort(economicEvaPort);

                    Object resultado = simulacionMicros.ejecutarSimulacion();

                    resultadosQueue.add(resultado);

                } else {
                    fracasos++;

                    excelRowData.put(1, "Fracaso");
                    for (int j = 2; j < headers.length; j++) {
                        excelRowData.put(j, 0);

                    }

                    excelRowData.put(10, triangularExploratorioMin);
                    ResultadoSimulacion.setExploratoriaInfra(triangularExploratorioMin);

                    excelRowData.put(11, triangularExploratorioPer);
                    ResultadoSimulacion.setExploratoriaPerf(triangularExploratorioPer);

                    excelRowData.put(12, triangularExploratorioTer);
                    ResultadoSimulacion.setExploratoriaTer(triangularExploratorioTer);

                    limitesEconomicosRepetidos.merge(0.0, 1, Integer::sum);

                    RestTemplate restTemplate = new RestTemplate();

                    SimulacionMicros simulacionMicros = new SimulacionMicros(idOportunidadObjetivo,
                            oportunidad.getActualIdVersion(), 0, 0, 0, 0,

                            0, 0, 0, 0,
                            0,
                            triangularExploratorioMin, triangularExploratorioPer, triangularExploratorioTer,
                            0, 0, 0, 0, 0, 0,
                            0, 0, 0, 0, restTemplate);

                    simulacionMicros.setEconomicEvaHost(economicEvaHost);
                    simulacionMicros.setEconomicEvaPort(economicEvaPort);
                    Object resultado = simulacionMicros.ejecutarSimulacion();

                    resultadosQueue.add(resultado);

                }
                excelRowBuffer.add(excelRowData);

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

        double kilometrajeCalculado = calcularReporte804(kilometraje, oportunidad.getPlanDesarrollo(), oportunidad.getPg());
        List<Double> reporte804 = new ArrayList<>();
        reporte804.add(kilometraje);
        reporte804.add(kilometrajeCalculado);
        List<Double> reporte805 = escaleraLimEconomicos(limitesEconomicosRepetidos, mediaTruncada, cantidadIteraciones);

        resultados = new ArrayList<>(resultadosQueue);
        if (!resultados.isEmpty() && resultados.get(0) instanceof Map) {
            Map<String, Object> primerElemento = (Map<String, Object>) resultados.get(0);
            primerElemento.put("reporte804", reporte804);
            primerElemento.put("reporte805", reporte805);
        }
        excelRowBuffer.forEach(row -> writeResultsToExcel(sheet, row));

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String tempFileName = "resultados_temp_" + idOportunidadObjetivo + ".json";
            String resFileName = "resultados_" + idOportunidadObjetivo + ".json";

            File tempFile = new File(tempFileName);
            File resFile = new File(resFileName);
            objectMapper.writeValue(tempFile, resultados);

            if (resFile.exists()) {
                if (!resFile.delete()) {
                    throw new IOException("No se pudo eliminar el archivo de destino: " + resFileName);
                }
            }

            if (tempFile.renameTo(resFile)) {
                System.out.println("Resultados guardados en " + resFileName);
            } else {
                System.err.println("Error al renombrar el archivo temporal: " + tempFileName);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Guardar resultados en el archivo Excel
        try (
                FileOutputStream fileOut = new FileOutputStream(
                        "SimulacionMonteCarlo_" + idOportunidadObjetivo + ".xlsx")) {
            workbook.write(fileOut);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return ResponseEntity.ok(resultados);

    }

    private boolean pruebaGeologica() {
        return random.nextDouble() <= oportunidad.getPg();
    }

    private double calcularRecursoProspectivo(double aleatorio, double percentil10, double percentil90) {

        if(percentil10 == percentil90) return percentil10;

        NormalDistribution normalStandard = new NormalDistribution(0, 1);
        double z90 = normalStandard.inverseCumulativeProbability(0.9);
        double mediaLog = (Math.log(percentil10) + Math.log(percentil90)) / 2;
        double desviacionLog = (Math.log(percentil10) - mediaLog) / z90;

        NormalDistribution normal = new NormalDistribution(mediaLog, desviacionLog);

        double logValue = normal.inverseCumulativeProbability(aleatorio);
        return Math.exp(logValue);
    }

    private double calcularLimiteEconomico(double pce) {
        double resultado;
        if (pce == 0) {
            resultado = 0.0;
        } else {
            resultado = Math.round((-0.00003 * Math.pow(pce, 2)) + (pce * 0.068) + 4.3824) * 12;
        }

        return resultado;
    }

    public List<Double> escaleraLimEconomicos(Map<Double, Integer> limitesEconomicosRepetidos, double mediaTruncada,
            int cantidadIteraciones) {
        List<Double> reporte805 = new ArrayList<>();

        // Filtrar las claves cuyo valor sea != 0 y encontrar la cantidad máxima de
        // repeticiones
        double maxLimiteEconomico = limitesEconomicosRepetidos.entrySet().stream()
                .filter(entry -> entry.getKey() != 0) // Filtrar límites donde la clave sea != 0
                .map(Map.Entry::getKey) // Obtener las claves (límites económicos)
                .max(Double::compare) // Encontrar el máximo
                .orElse(0.0); // Valor por defecto si no hay límites válidos // Manejo de caso vacío
        // Calcular la última fila
        for (int iteracion = 1; iteracion <= maxLimiteEconomico; iteracion++) {
            double sumaIteracion = 0.0;

            for (Map.Entry<Double, Integer> entry : limitesEconomicosRepetidos.entrySet()) {
                Integer repeticiones = entry.getValue();
                Double limite = entry.getKey();

                // Sumar solo si la iteración está dentro del rango de repeticiones
                if (iteracion <= limite && limite != 0) {
                    sumaIteracion += (repeticiones * mediaTruncada);
                }
            }
            sumaIteracion /= cantidadIteraciones;

            // Agregar el resultado de la suma al final de la lista
            reporte805.add(sumaIteracion);
        }

        return reporte805;
    }

    private double calcularReporte804(double kilometraje, String planDesarrollo, double pg) {

        String number = planDesarrollo.substring(planDesarrollo.lastIndexOf(' ') + 1); // Toma todo después del último espacio
        int result = Integer.parseInt(number);

        if (result == 1) {
            return kilometraje * (1 - pg);
        } else if (result == 2) {
            return kilometraje;
        } else if (result >= 3) {
            return kilometraje * pg;
        }
        return kilometraje;
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
            Cell cell = headerRow.getCell(i);
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

    private void OrdenaEncabezado(Row headerRow) {
        if (headerRow == null) {
            // System.out.println("La fila del encabezado no existe.");
            return;
        }
        // Recopilar años y sus índices originales desde la columna U (índice 21)
        List<Map.Entry<Integer, Integer>> yearIndexPairs = new ArrayList<>();
        for (int i = 20; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                yearIndexPairs.add(new AbstractMap.SimpleEntry<>((int) cell.getNumericCellValue(), i));
            }
        }

        // Ordenar los pares por el valor del año
        yearIndexPairs.sort(Comparator.comparingInt(Map.Entry::getKey));

        // Reorganizar las celdas de encabezado en el orden correcto
        for (int i = 0; i < yearIndexPairs.size(); i++) {
            int year = yearIndexPairs.get(i).getKey();
            headerRow.getCell(20 + i).setCellValue(year); // Reasignar los años ordenados desde la columna K
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

}
