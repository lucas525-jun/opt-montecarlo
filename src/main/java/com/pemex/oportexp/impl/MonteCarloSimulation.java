package com.pemex.oportexp.impl;

import lombok.Setter;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.pemex.oportexp.Models.Oportunidad;
import com.pemex.oportexp.Models.ResultadoSimulacion;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MonteCarloSimulation {
    @Setter
    private MonteCarloDAO monteCarloDAO;
    private String economicEvaHost;
    private String economicEvaPort;
    private final String version;
    private final int iterations;
    private final int pgValue;
    private final int idOportunidadObjetivo;
    private final String evaluationId;
    private Oportunidad oportunidad;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    private List<Map<String, Object>> pceSheetData = Collections.synchronizedList(new ArrayList<>());
    private List<Map<String, Object>> aceiteSheetData = Collections.synchronizedList(new ArrayList<>());
    private List<Map<String, Object>> gasSheetData = Collections.synchronizedList(new ArrayList<>());
    private List<Map<String, Object>> condensadoSheetData = Collections.synchronizedList(new ArrayList<>());


    private final AtomicReference<Double> grandTotalPCE = new AtomicReference<>(0.0);
    private final AtomicReference<Double> grandTotalAceite = new AtomicReference<>(0.0);
    private final AtomicReference<Double> grandTotalGas = new AtomicReference<>(0.0);
    private final AtomicReference<Double> grandTotalCondensado = new AtomicReference<>(0.0);

    public void setOportunidad(Oportunidad oportunidad) {
        this.oportunidad = oportunidad;
    }

    public MonteCarloSimulation(String version, int idOportunidadObjetivo, int iterations, int pgValue, String evaluationId) {
        this.version = version;
        this.idOportunidadObjetivo = idOportunidadObjetivo;
        this.iterations = iterations;
        this.pgValue = pgValue;
        this.evaluationId = evaluationId;
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

        int cantidadIteraciones = iterations;

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

        int numberOfVariables = 23; 
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
            sheet.setColumnWidth(i, 5000); 
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
                int baseIndex = i * numberOfVariables; 

                ResultadoSimulacion ResultadoSimulacion = new ResultadoSimulacion();
                excelRowData.put(0, i + 1); 
                Object resultado;
                if (pruebaGeologica()) {
                    excelRowData.put(1, "Éxito");
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

                    resultado = simulacionMicros.ejecutarSimulacion();

                    resultadosQueue.add(resultado);

                } else {

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
                    resultado = simulacionMicros.ejecutarSimulacion();

                    resultadosQueue.add(resultado);
                    
                }
                final int iteration = i;
                collectSheetData(resultado, iteration+2);
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

        // Create production excel
        Workbook productionWorkbook = new XSSFWorkbook();
        Sheet pceSheet = productionWorkbook.createSheet("PCE");
        Sheet aceiteSheet = productionWorkbook.createSheet("Aceite");
        Sheet gasSheet = productionWorkbook.createSheet("Gas");
        Sheet condensadoSheet = productionWorkbook.createSheet("Condensado");

        Font font = productionWorkbook.createFont();
        font.setFontHeightInPoints((short) 16); 
        font.setBold(true); 
        CellStyle titleStyle = productionWorkbook.createCellStyle();
        titleStyle.setFont(font);

        String[] years = determineYearRange(resultadosQueue);

        createSheetHeaders(pceSheet, years, productionWorkbook);
        createSheetHeaders(aceiteSheet, years, productionWorkbook);
        createSheetHeaders(gasSheet, years, productionWorkbook);
        createSheetHeaders(condensadoSheet, years, productionWorkbook);

        Row titleRowOfPce = pceSheet.createRow(0);
        titleRowOfPce.setHeight((short) 600);
        Cell titleCellOfPce = titleRowOfPce.createCell(2);
        titleCellOfPce.setCellValue("Producción diaria promedio de Petróleo Crudo Equivalente (mbpced)");
        titleCellOfPce.setCellStyle(titleStyle);

        Row titleRowOfAceite = aceiteSheet.createRow(0);
        titleRowOfAceite.setHeight((short) 600);
        Cell titleCellOfAceite = titleRowOfAceite.createCell(2);
        titleCellOfAceite.setCellValue("Producción diaria promedio de Aceite (mbd)");
        titleCellOfAceite.setCellStyle(titleStyle);

        Row titleRowOfGas = gasSheet.createRow(0);
        titleRowOfGas.setHeight((short) 600);
        Cell titleCellOfGas = titleRowOfGas.createCell(2);
        titleCellOfGas.setCellValue("Producción diaria promedio de Gas (mmpcd)");
        titleCellOfGas.setCellStyle(titleStyle);

        Row titleRowOfCondensado = condensadoSheet.createRow(0);
        titleRowOfCondensado.setHeight((short) 600);
        Cell titleCellOfCondensado = titleRowOfCondensado.createCell(2);
        titleCellOfCondensado.setCellValue("Producción diaria promedio de Condensado (mbd)");
        titleCellOfCondensado.setCellStyle(titleStyle);
        

        writeBatchDataToSheets(pceSheet, aceiteSheet, gasSheet, condensadoSheet, years);



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

        Collections.sort(excelRowBuffer, Comparator.comparingInt(row -> ((Number) row.get(0)).intValue()));

        excelRowBuffer.forEach(row -> writeResultsToExcel(sheet, row));

        // Guardar resultados en el archivo Excel
        try (
                FileOutputStream fileOut = new FileOutputStream(
                    evaluationId + "_SimulacionMonteCarlo_" + oportunidad.getOportunidad() + "_" + oportunidad.getIdOportunidadObjetivo() + ".xlsx")) {
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

        //save production excel
        
        try (FileOutputStream fileOut = new FileOutputStream(
            evaluationId + "_Perfiles de producción_" + oportunidad.getOportunidad()  + "_" + oportunidad.getIdOportunidadObjetivo() + ".xlsx")) {
                    productionWorkbook.write(fileOut);

        } catch (IOException e) {
            System.err.println("Error writing Excel file: " + e.getMessage());
        }
        return ResponseEntity.ok(resultados);

    }

    private void collectSheetData(Object eachResultado, int iterationNumber) {
        if (eachResultado instanceof Map) {
            Map<String, Object> resultMap = (Map<String, Object>) eachResultado;
            List<Map<String, Object>> evaluacionEconomica = (List<Map<String, Object>>) resultMap.get("evaluacionEconomica");
            
            if (evaluacionEconomica == null) {
                return;
            }
            
            Map<String, Object> pceRow = new HashMap<>();
            Map<String, Object> aceiteRow = new HashMap<>();
            Map<String, Object> gasRow = new HashMap<>();
            Map<String, Object> condensadoRow = new HashMap<>();
            
            pceRow.put("iteration", iterationNumber - 1);
            aceiteRow.put("iteration", iterationNumber - 1);
            gasRow.put("iteration", iterationNumber - 1);
            condensadoRow.put("iteration", iterationNumber - 1);
            
            // Use these for accumulating totals
            double pceTotal = 0.0;
            double aceiteTotal = 0.0;
            double gasTotal = 0.0;
            double condensadoTotal = 0.0;
            
            for (Map<String, Object> evaluacion : evaluacionEconomica) {
                String anio = (String) evaluacion.get("anio");
                Map<String, Object> produccionDiariaPromedio = (Map<String, Object>) evaluacion.get("produccionDiariaPromedio");
                
                if (produccionDiariaPromedio != null) {
                    double mbpce = produccionDiariaPromedio.get("mbpce") != null ? 
                        ((Number) produccionDiariaPromedio.get("mbpce")).doubleValue() : 0.0;
                    double aceiteTotalValue = produccionDiariaPromedio.get("aceiteTotal") != null ? 
                        ((Number) produccionDiariaPromedio.get("aceiteTotal")).doubleValue() : 0.0;
                    double gasTotalValue = produccionDiariaPromedio.get("gasTotal") != null ? 
                        ((Number) produccionDiariaPromedio.get("gasTotal")).doubleValue() : 0.0;
                    double condensado = produccionDiariaPromedio.get("condensado") != null ? 
                        ((Number) produccionDiariaPromedio.get("condensado")).doubleValue() : 0.0;
                    
                    // Keep running sum of all values
                    pceTotal += mbpce;
                    aceiteTotal += aceiteTotalValue;
                    gasTotal += gasTotalValue;
                    condensadoTotal += condensado;
    
                    pceRow.put(anio, mbpce);
                    aceiteRow.put(anio, aceiteTotalValue);
                    gasRow.put(anio, gasTotalValue);
                    condensadoRow.put(anio, condensado);
                }
            }
            
            // Store the final totals
            pceRow.put("total", pceTotal);
            aceiteRow.put("total", aceiteTotal);
            gasRow.put("total", gasTotal);
            condensadoRow.put("total", condensadoTotal);
            
            // Update grand totals atomically with the final calculated values
            final double finalPceTotal = pceTotal;
            final double finalAceiteTotal = aceiteTotal;
            final double finalGasTotal = gasTotal;
            final double finalCondensadoTotal = condensadoTotal;

            grandTotalPCE.updateAndGet(currentTotal -> currentTotal + finalPceTotal);
            grandTotalAceite.updateAndGet(currentTotal -> currentTotal + finalAceiteTotal);
            grandTotalGas.updateAndGet(currentTotal -> currentTotal + finalGasTotal);
            grandTotalCondensado.updateAndGet(currentTotal -> currentTotal + finalCondensadoTotal);
            
            // Add collected data to lists
            pceSheetData.add(pceRow);
            aceiteSheetData.add(aceiteRow);
            gasSheetData.add(gasRow);
            condensadoSheetData.add(condensadoRow);
        }
    }

    
    private void writeBatchDataToSheets(Sheet pceSheet, Sheet aceiteSheet, Sheet gasSheet, Sheet condensadoSheet, String[] years) {
        writeSingleSheetData(pceSheet, pceSheetData, years);
        writeSingleSheetData(aceiteSheet, aceiteSheetData, years);
        writeSingleSheetData(gasSheet, gasSheetData, years);
        writeSingleSheetData(condensadoSheet, condensadoSheetData, years);
        
        // Write grand totals to the first row (average values)
        if (iterations > 0) {
            writeToSheet(pceSheet, 0, 1, grandTotalPCE.get() / iterations);
            writeToSheet(aceiteSheet, 0, 1, grandTotalAceite.get() / iterations);
            writeToSheet(gasSheet, 0, 1, grandTotalGas.get() / iterations);
            writeToSheet(condensadoSheet, 0, 1, grandTotalCondensado.get() / iterations);
        }
    }
    
    
    private void writeSingleSheetData(Sheet sheet, List<Map<String, Object>> sheetData, String[] years) {
        for (Map<String, Object> rowData : sheetData) {
            int rowNum = ((Number) rowData.get("iteration")).intValue() + 1; // +1 for header rows
            Row row = sheet.createRow(rowNum);
            
            // Write iteration number
            Cell iterationCell = row.createCell(0);
            iterationCell.setCellValue(((Number) rowData.get("iteration")).doubleValue());
            
            // Write total
            Cell totalCell = row.createCell(1);
            totalCell.setCellValue(((Number) rowData.get("total")).doubleValue());
            
            // Write year data - only for years that exist in our determined year range
            for (int i = 0; i < years.length; i++) {
                String year = years[i];
                Cell cell = row.createCell(i + 2);
                Object value = rowData.get(year);
                if (value != null) {
                    cell.setCellValue(((Number) value).doubleValue());
                } else {
                    cell.setCellValue(0.0);
                }
            }
        }
    }
    
   

    private void createSheetHeaders(Sheet sheet, String[] years, Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        // style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);

        Row headerRow = sheet.createRow(1);
        Cell cellIteration = headerRow.createCell(0);
        cellIteration.setCellStyle(style);
        cellIteration.setCellValue("Iteración");
        Cell cellTotal = headerRow.createCell(1);
        cellTotal.setCellStyle(style);
        cellTotal.setCellValue("Total");
        for (int i = 0; i < years.length; i++) {
            Cell cell = headerRow.createCell(i + 2);
            cell.setCellValue(years[i]);
            cell.setCellStyle(style);
        }
    }

    private String[] determineYearRange(Queue<Object> resultadosQueue) {
        Set<String> yearSet = new HashSet<>();
        
        // Process all results in the queue to find all unique years
        for (Object resultado : resultadosQueue) {
            if (resultado instanceof Map) {
                Map<String, Object> resultMap = (Map<String, Object>) resultado;
                List<Map<String, Object>> evaluacionEconomica = (List<Map<String, Object>>) resultMap.get("evaluacionEconomica");
                
                if (evaluacionEconomica != null) {
                    for (Map<String, Object> evaluacion : evaluacionEconomica) {
                        String anio = (String) evaluacion.get("anio");
                        if (anio != null) {
                            yearSet.add(anio);
                        }
                    }
                }
            }
        }
        
        // Convert to array and sort chronologically
        String[] years = yearSet.toArray(new String[0]);
        Arrays.sort(years);
        
        return years;
    }

    private void writeToSheet(Sheet sheet, int rowCounter, int columnIndex, double value) {
        Row row = sheet.getRow(rowCounter);
        if (row == null) {
            row = sheet.createRow(rowCounter);
        }
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value);
    }

    private boolean pruebaGeologica() {
        if(pgValue == 1) {
            return true;
        } else {
            return random.nextDouble() <= oportunidad.getPg();
        }
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
        String number = planDesarrollo.substring(planDesarrollo.lastIndexOf(' ') + 1);
        
        // Check if the number contains a fraction
        if (number.contains("/")) {
            // Parse as a fraction
            String[] parts = number.split("/");
            if (parts.length == 2) {
                try {
                    double numerator = Double.parseDouble(parts[0]);
                    double denominator = Double.parseDouble(parts[1]);
                    // Use the result of the fraction for your logic
                    double result = numerator / denominator;
                    
                    // Adjust the logic to work with decimal values instead of integers
                    if (result <= 1) {
                        return kilometraje * (1 - pg);
                    } else if (result <= 2) {
                        return kilometraje;
                    } else {
                        return kilometraje * pg;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Error parsing fraction: " + number);
                    return kilometraje; // Default fallback
                }
            }
        }
        
        // Original integer parsing logic
        try {
            int result = Integer.parseInt(number);
            if (result == 1) {
                return kilometraje * (1 - pg);
            } else if (result == 2) {
                return kilometraje;
            } else if (result >= 3) {
                return kilometraje * pg;
            }
        } catch (NumberFormatException e) {
            System.err.println("Error parsing development plan number: " + number);
        }
        
        return kilometraje; // Default fallback
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
