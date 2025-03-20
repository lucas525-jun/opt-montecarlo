package com.pemex.oportexp.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Setter;

import com.pemex.oportexp.Models.Oportunidad;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.concurrent.atomic.AtomicReference;

public class MonteCarloSimulationMultiObject {
    @Setter
    private MonteCarloDAO monteCarloDAO;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final int DEFAULT_NUM_VARIABLES = 19;

    private final AtomicReference<Double> grandTotalPCE = new AtomicReference<>(0.0);
    private final AtomicReference<Double> grandTotalAceite = new AtomicReference<>(0.0);
    private final AtomicReference<Double> grandTotalGas = new AtomicReference<>(0.0);
    private final AtomicReference<Double> grandTotalCondensado = new AtomicReference<>(0.0);


    // Configuration
    private final String version;
    private final int[] idOportunidadObjetivo;
    private final int cantidadIteraciones;
    private final int numOportunidades;
    private final int numberOfVariables;
    private final int pgValue;

    // Core simulation data
    private Oportunidad[] oportunidad;
    private List<Double>[] randomNumbers;
    private double[] mediaTruncada;
    private double[] kilometraje;
    private final AtomicReference<Oportunidad[]> oportunidadRef = new AtomicReference<>();


    private double fcAceite;
    private double fcCondensado;
    private double fcGas;

    // Simulation results
    private int[] exitos;
    private int[] fracasos;
    private final Queue<Object> resultadosQueue = new ConcurrentLinkedQueue<>();
    private final Map<Double, Integer> limitesEconomicosRepetidos = new ConcurrentHashMap<>();
    private AtomicInteger iterationNumber = new AtomicInteger(1);

    private final Map<Integer, Map<Integer, Object>> excelRowBuffers = new ConcurrentHashMap<>();
    private String[][] currentSimulationFecha;
    private Object[] resultados;

    // private final ReentrantLock excelLock = new ReentrantLock();

    List<Object> responseData = Collections.synchronizedList(new ArrayList<>());

    private SimulacionMicrosMulti simulacionMicrosMulti;
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, SimulacionMicrosMulti.SimulationParameters>> simulationParametersMatrix = new ConcurrentHashMap<>();

    // Excel headers
    private final String[] headers = {
            "Iteración", "Resultado", "Aleatorio PCE", "PCE",
            "Aleatorio Gasto", "Gasto Inicial (mbpce)",
            "Aleatorio Declinación", "Declinación",
            "Aleatorio Área", "Área", "E.I.", "E.P", "E.T", "D.I", "D.P", "D.T",
            "Bateria", "Plataforma Desarrollo", "Linea Descarga", "E.comprension",
            "ducto", "Arboles submarinos", "manifols", "risers",
            "Sistemas de control", "Cubierta Proces", "Buquetaquecompra", "buquetaQuerenta"
    };
    private final int HEADERS_SIZE = headers.length;

    private static final class Indices {
        static final int ALEATORIO_RECURSO = 0;
        static final int ALEATORIO_GASTO = 1;
        static final int ALEATORIO_DECLINACION = 2;
        static final int ALEATORIO_AREA = 3;
        static final int ALEATORIO_DES_INFRA = 4;
        static final int ALEATORIO_DES_PER = 5;
        static final int ALEATORIO_DES_TER = 6;
        static final int BATERIA_MIN = 7;
        static final int ALEATORIO_INVERSION = 8;
        static final int ALEATORIO_LINEA_DESCARGA = 9;
        static final int ALEATORIO_ESTACION_COMPRESION = 10;
        static final int ALEATORIO_DUCTO = 11;
        static final int ALEATORIO_ARBOLES_SUBMARINOS = 12;
        static final int ALEATORIO_MANIFOLDS = 13;
        static final int ALEATORIO_RISERS = 14;
        static final int ALEATORIO_SISTEMAS_CONTROL = 15;
        static final int ALEATORIO_CUBIERTA_PROCES = 16;
        static final int ALEATORIO_BUQUE_TANQUE_COMPRA = 17;
        static final int ALEATORIO_BUQUE_TANQUE_RENTA = 18;
    }

    private static class InvestmentVariables {
        double triangularInversionBat;
        double triangularInversionPlataforma;
        double triangularInversionLineaDescarga;
        double triangularInversionEstacionCompresion;
        double triangularInversionDucto;
        double triangularInversionArbolesSubmarinos;
        double triangularInversionManifolds;
        double triangularInversionRisers;
        double triangularInversionSistemasDeControl;
        double triangularInversionCubiertaDeProces;
        double triangularInversionBuqueTanqueCompra;
        double triangularInversionBuqueTanqueRenta;
        double triangularExploratorioMin;
        double triangularExploratorioPer;
        double triangularExploratorioTer;
    }

    private InvestmentVariables[] investments;
    private double[][] precomputedRandomNumbers;


    public MonteCarloSimulationMultiObject(String version, int[] idOportunidadObjetivo, String economicEvaHost,
            String economicEvaPort, int iterations, int pgValue) {
        validateInputs(version, idOportunidadObjetivo);
        this.version = version;
        this.idOportunidadObjetivo = idOportunidadObjetivo.clone();
        this.pgValue = pgValue;
        this.cantidadIteraciones = iterations;
        this.numOportunidades = idOportunidadObjetivo.length;
        this.numberOfVariables = DEFAULT_NUM_VARIABLES;
        this.investments = new InvestmentVariables[numOportunidades];
        this.currentSimulationFecha = new String[numOportunidades][cantidadIteraciones];
        this.resultados = new Object[cantidadIteraciones];
        this.simulacionMicrosMulti = new SimulacionMicrosMulti(restTemplate, economicEvaHost, economicEvaPort);

        for (int i = 0; i < numOportunidades; i++) {
            ConcurrentHashMap<Integer, SimulacionMicrosMulti.SimulationParameters> paramsMap = new ConcurrentHashMap<>();

            investments[i] = new InvestmentVariables();
            for (int j = 0; j < cantidadIteraciones; j++) {
                currentSimulationFecha[i][j] = "unexist";
                int index = i * cantidadIteraciones + j;
                excelRowBuffers.put(index, new ConcurrentHashMap<>());
                SimulacionMicrosMulti.SimulationParameters params = new SimulacionMicrosMulti.SimulationParameters();

                paramsMap.put(j, params);

                simulationParametersMatrix.computeIfAbsent(j, k -> new ConcurrentHashMap<>()).put(i, params);
            }
        }
    }

    public void initializeSimulation() {
        initializeArrays();
        loadOportunidades();
        oportunidadRef.set(oportunidad);
        try {
            org.apache.logging.log4j.LogManager.getLogger(MonteCarloSimulationMultiObject.class);
        } catch (Exception e) {
            System.err.println("Warning: Failed to initialize Log4j: " + e.getMessage());
        }


    }

    private void validateInputs(String version, int[] idOportunidadObjetivo) {
        if (version == null || idOportunidadObjetivo == null) {
            throw new IllegalArgumentException("Version and idOportunidadObjetivo cannot be null");
        }
    }

    private void initializeArrays() {
        this.oportunidad = new Oportunidad[numOportunidades];
        this.exitos = new int[numOportunidades];
        this.fracasos = new int[numOportunidades];
        this.mediaTruncada = new double[numOportunidades];
        this.kilometraje = new double[numOportunidades];
        this.randomNumbers = new CopyOnWriteArrayList[numOportunidades];
        this.precomputedRandomNumbers = new double[numOportunidades][cantidadIteraciones];


        for (int i = 0; i < numOportunidades; i++) {
            this.randomNumbers[i] = new CopyOnWriteArrayList<>(
                IntStream.range(0, cantidadIteraciones * numberOfVariables)
                    .mapToDouble(j -> ThreadLocalRandom.current().nextDouble())
                    .boxed()
                    .collect(Collectors.toList())
            );
            for (int j = 0; j < cantidadIteraciones; j++) {
                precomputedRandomNumbers[i][j] = ThreadLocalRandom.current().nextDouble();
            }
        }
    }

    private void loadOportunidades() {
        for (int i = 0; i < numOportunidades; i++) {
            try {
                Oportunidad eachOportunidad = monteCarloDAO.executeQuery(version, idOportunidadObjetivo[i]);
                // System.err.println("eachOportunidad " + eachOportunidad.getIdOportunidadObjetivo());
                if (eachOportunidad == null) {
                    throw new IllegalStateException("Unable to retrieve opportunity data for index " + i);
                }

                setOportunidad(eachOportunidad, i);


                // Existing validation methods
                mediaTruncada[i] = monteCarloDAO.getMediaTruncada(version, idOportunidadObjetivo[i]);
                validateMediaTruncada(mediaTruncada[i], i);

                kilometraje[i] = monteCarloDAO.getKilometraje(version, idOportunidadObjetivo[i]);
                validateKilometraje(kilometraje[i], i);

                initializeExploratorioValues(i);

            } catch (Exception e) {
                System.err.println("Error loading opportunity at index " + i + ": " + e.getMessage());
                throw new DatabaseException("Failed to load oportunidades", e);
            }
        }

    }

    private void validateMediaTruncada(double value, int index) {
        if (value < 0) {
            throw new IllegalStateException("Invalid mediaTruncada value for index " + index);
        }
    }

    private void validateKilometraje(double value, int index) {
        if (value < 0) {
            throw new IllegalStateException("Invalid kilometraje value for index " + index);
        }
    }

    public class DatabaseException extends RuntimeException {
        public DatabaseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public class ValidationException extends RuntimeException {
        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public void setOportunidad(Oportunidad oportunidad, int opIndex) {
        this.oportunidad[opIndex] = oportunidad;
    }

    private void initializeExploratorioValues(int index) {
        double aleatorioExploratorioInfra = ThreadLocalRandom.current().nextDouble();
        double aleatorioExploratorioPerforacion = ThreadLocalRandom.current().nextDouble();
        double aleatorioExploratorioTerminacion = ThreadLocalRandom.current().nextDouble();

        investments[index].triangularExploratorioMin = calculateExploratorioInfra(index, aleatorioExploratorioInfra);
        investments[index].triangularExploratorioPer = calculateExploratorioPer(index,
                aleatorioExploratorioPerforacion);
        investments[index].triangularExploratorioTer = calculateExploratorioTer(index,
                aleatorioExploratorioTerminacion);
    }

    private double calculateExploratorioInfra(int index, double aleatorio) {
        return triangularDistributionSinPCE(
                oportunidad[index].getInfraestructuraMin(),
                oportunidad[index].getInfraestructuraMax(),
                oportunidad[index].getInfraestructuraMP(),
                aleatorio);
    }

    private double calculateExploratorioPer(int index, double aleatorio) {
        return triangularDistributionSinPCE(
                oportunidad[index].getPerforacionMin(),
                oportunidad[index].getPerforacionMax(),
                oportunidad[index].getPerforacionMP(),
                aleatorio);
    }

    private double calculateExploratorioTer(int index, double aleatorio) {
        return triangularDistributionSinPCE(
                oportunidad[index].getTerminacionMin(),
                oportunidad[index].getTerminacionMax(),
                oportunidad[index].getTerminacionMP(),
                aleatorio);
    }

    public ResponseEntity<List<Object>> runSimulation() {
       
        iterationNumber.set(1);
        
        // Create workbook and sheet ONCE before starting threads
        Workbook workbook = null;
        Workbook productionWorkbook = null;
        Sheet sheet = null;
        
        try {

            productionWorkbook = new XSSFWorkbook();
            Sheet pceSheet = productionWorkbook.createSheet("PCE");
            Sheet aceiteSheet = productionWorkbook.createSheet("Aceite");
            Sheet gasSheet = productionWorkbook.createSheet("Gas");
            Sheet condensadoSheet = productionWorkbook.createSheet("Condensado");

            Font font = productionWorkbook.createFont();
            font.setFontHeightInPoints((short) 16); 
            font.setBold(true); 
            CellStyle titleStyle = productionWorkbook.createCellStyle();
            titleStyle.setFont(font);

            String[] years = IntStream.rangeClosed(2025, 2120).mapToObj(String::valueOf).toArray(String[]::new);

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

            workbook = createWorkbook();
            sheet = workbook.createSheet("Simulación Monte Carlo");
            createHeaderRow(sheet, workbook);
            
            int threadPoolSize = Runtime.getRuntime().availableProcessors(); 
            ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize); 
            List<Future<Void>> futures = new ArrayList<>();
            
            for (int i = 0; i < cantidadIteraciones; i++) {
                int finalI = i;

                futures.add(executor.submit(() -> {
                    Map<Integer, Object> excelRowData = new ConcurrentHashMap<>();
                    excelRowBuffers.put(finalI, excelRowData);
                   
                    ConcurrentHashMap<Integer, SimulacionMicrosMulti.SimulationParameters> paramsArray = 
                        simulationParametersMatrix.get(finalI);
                    
                    simulateOpportunities(finalI, excelRowData);
                    if (paramsArray != null) {
                        resultados[finalI] = simulacionMicrosMulti.ejecutarSimulacionMulti(paramsArray);
                        synchronized (resultadosQueue) {
                            resultadosQueue.add(resultados[finalI]);
                        }
                        writeDataToSheets(resultados[finalI], pceSheet, aceiteSheet, gasSheet, condensadoSheet, years, finalI+2);
                    } else {
                        System.err.println("No simulation parameters found for iteration " + finalI);
                    }
                    return null;
                }));
            }
            
            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    System.err.println("Simulation task failed: " + e.getMessage());
                }
            }
            
            try {
                executor.shutdown();
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            writeToSheet(pceSheet, 0, 1, grandTotalPCE.get() / cantidadIteraciones);
            writeToSheet(aceiteSheet, 0, 1, grandTotalAceite.get() / cantidadIteraciones);
            writeToSheet(gasSheet, 0, 1, grandTotalGas.get() / cantidadIteraciones);
            writeToSheet(condensadoSheet, 0, 1, grandTotalCondensado.get() / cantidadIteraciones);
            
            
            // Process results after all simulations are complete
            List<Double>[] reporteArray805 = new List[numOportunidades];
            List<Double> reporte804 = new ArrayList<>();
            int deepestObjectIndex = 0;
            double kilometrajeOfLastObject = kilometraje[deepestObjectIndex];
            double kilometrajeCalculado = calcularReporte804(
                kilometrajeOfLastObject, 
                oportunidad[deepestObjectIndex].getPlanDesarrollo(), 
                oportunidad[deepestObjectIndex].getPg()
            );
            
            // Calculate for each opportunity
            for (int i = 0; i < numOportunidades; i++) {
                reporteArray805[i] = escaleraLimEconomicos(
                    limitesEconomicosRepetidos, 
                    mediaTruncada[i],
                    cantidadIteraciones
                );
            }
            reporte804.add(kilometrajeOfLastObject);
            reporte804.add(kilometrajeCalculado);
            
            List<Double> reporte805 = mergeReporte805(reporteArray805);
            
            responseData = new ArrayList<>(resultadosQueue);
            
            if (!responseData.isEmpty() && responseData.get(0) instanceof Map) {
                Map<String, Object> primerElemento = (Map<String, Object>) responseData.get(0);
                primerElemento.put("reporte804", reporte804);
                primerElemento.put("reporte805", reporte805);
            }
            
            writeResultsToExcel(sheet);
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMdd_HHmm");
            
            // Save montecarlo workbook to file
            try (FileOutputStream fileOut = new FileOutputStream(
                    "MonteCarloSimulationMultiObject_" + oportunidad[0].getOportunidad() + ".xlsx")) {
                workbook.write(fileOut);

            } catch (IOException e) {
                System.err.println("Error writing Excel file: " + e.getMessage());
            }
            
            try (FileOutputStream fileOut = new FileOutputStream(
                    "Production_" + oportunidad[0].getOportunidad() + ".xlsx")) {
                        productionWorkbook.write(fileOut);

            } catch (IOException e) {
                System.err.println("Error writing Excel file: " + e.getMessage());
            }
            
            // Save JSON results
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
                
                String tempFileName = "resultadosMultiObjectivo_temp_merged" + 
                    oportunidad[0].getIdOportunidadObjetivo() + ".json";
                String resFileName = "resultadosMultiObjectivo_merged" + 
                    oportunidad[0].getIdOportunidadObjetivo() + ".json";
                
                File tempFile = new File(tempFileName);
                File resFile = new File(resFileName);
                
                objectMapper.writeValue(tempFile, responseData);
                
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
                System.err.println("Error saving JSON results: " + e.getMessage());
            }
            
            
            return ResponseEntity.ok(responseData);
            
        } catch (Exception e) {
            System.err.println("Critical error in simulation: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        } finally {
            // Close workbook in finally block
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    System.err.println("Error closing workbook: " + e.getMessage());
                }
            }
        }
    }

    private void writeToSheet(Sheet sheet, int rowCounter, int columnIndex, double value) {
        Row row = sheet.getRow(rowCounter);
        if (row == null) {
            row = sheet.createRow(rowCounter);
        }
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value);
    }

    private void writeDataToSheets(Object resultado, Sheet pceSheet, Sheet aceiteSheet, Sheet gasSheet, Sheet condensadoSheet, String[] years, int iterationNumber) {
        double totalPCEVal = 0.0;
        double totalAceiteVal = 0.0;
        double totalGasVal = 0.0;
        double totalCondensadoVal = 0.0;
        if (resultado instanceof Map) {
            Map<String, Object> resultMap = (Map<String, Object>) resultado;
            List<Map<String, Object>> evaluacionEconomica = (List<Map<String, Object>>) resultMap.get("evaluacionEconomica");
            synchronized (pceSheet) {
                writeToSheet(pceSheet, iterationNumber, 0, iterationNumber - 1); 
            }

            synchronized (aceiteSheet) {
                writeToSheet(aceiteSheet, iterationNumber, 0, iterationNumber - 1); 
            }

            synchronized (gasSheet) {
                writeToSheet(gasSheet, iterationNumber, 0, iterationNumber - 1); 
            }

            synchronized (condensadoSheet) {
                writeToSheet(condensadoSheet, iterationNumber, 0, iterationNumber - 1); 
            }
            for (Map<String, Object> evaluacion : evaluacionEconomica) {
                String anio = (String) evaluacion.get("anio");
                Map<String, Object> produccionDiariaPromedio = (Map<String, Object>) evaluacion.get("produccionDiariaPromedio");
                
                if (produccionDiariaPromedio != null) {
                    double mbpce = produccionDiariaPromedio.get("mbpce") != null ? ((Number) produccionDiariaPromedio.get("mbpce")).doubleValue() : 0.0;
                    double aceiteTotal = produccionDiariaPromedio.get("aceiteTotal") != null ? ((Number) produccionDiariaPromedio.get("aceiteTotal")).doubleValue() : 0.0;
                    double gasTotal = produccionDiariaPromedio.get("gasTotal") != null ? ((Number) produccionDiariaPromedio.get("gasTotal")).doubleValue() : 0.0;
                    double condensado = produccionDiariaPromedio.get("condensado") != null ? ((Number) produccionDiariaPromedio.get("condensado")).doubleValue() : 0.0;

                    totalPCEVal+= mbpce;
                    totalAceiteVal+= aceiteTotal;
                    totalGasVal+= gasTotal;
                    totalCondensadoVal+= condensado;

                    int yearIndex = Arrays.asList(years).indexOf(anio);
                    if (yearIndex != -1) {
                        
                        synchronized (pceSheet) {
                            writeToSheet(pceSheet, iterationNumber, yearIndex + 2, mbpce); 
                        }

                        synchronized (aceiteSheet) {
                            writeToSheet(aceiteSheet, iterationNumber, yearIndex + 2, aceiteTotal);
                        }

                        synchronized (gasSheet) {
                            writeToSheet(gasSheet, iterationNumber, yearIndex + 2, gasTotal);
                        }

                        synchronized (condensadoSheet) {
                            writeToSheet(condensadoSheet, iterationNumber, yearIndex + 2, condensado);
                        }
                    }
                }
            }
            final double finalTotalPCEVal = totalPCEVal;
            final double finalTotalAceiteVal = totalAceiteVal;
            final double finalTotalGasVal = totalGasVal;
            final double finalTotalCondensadoVal = totalCondensadoVal;

            grandTotalPCE.updateAndGet(currentTotal -> currentTotal + finalTotalPCEVal);
            grandTotalAceite.updateAndGet(currentTotal -> currentTotal + finalTotalAceiteVal);
            grandTotalGas.updateAndGet(currentTotal -> currentTotal + finalTotalGasVal);
            grandTotalCondensado.updateAndGet(currentTotal -> currentTotal + finalTotalCondensadoVal);

            synchronized (pceSheet) {
                writeToSheet(pceSheet, iterationNumber, 1, totalPCEVal); 
            }
            
            synchronized (aceiteSheet) {
                writeToSheet(aceiteSheet, iterationNumber, 1, totalAceiteVal); 
            }
            synchronized (gasSheet) {
                writeToSheet(gasSheet, iterationNumber, 1, totalGasVal); 
            }
            synchronized (condensadoSheet) {
                writeToSheet(condensadoSheet, iterationNumber, 1, totalCondensadoVal); 
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

    private void writeResultsToExcel(Sheet sheet) {
        Map<Integer, Map<Integer, Object>> buffersCopy = new HashMap<>(excelRowBuffers);
        
        int rowIndex = 1;
        
        for (Map.Entry<Integer, Map<Integer, Object>> entry : buffersCopy.entrySet()) {
            Map<Integer, Object> buffer = entry.getValue();
            if (buffer != null) {
                Row row = sheet.createRow(rowIndex++);
                // Create a copy of the buffer to avoid concurrent modification
                Map<Integer, Object> bufferData = new HashMap<>(buffer);
                for (Map.Entry<Integer, Object> cellEntry : bufferData.entrySet()) {
                    int columnIndex = cellEntry.getKey();
                    Object cellData = cellEntry.getValue();
                    
                    Cell cell = row.createCell(columnIndex);
                    
                    if (cellData instanceof Number) {
                        cell.setCellValue(((Number) cellData).doubleValue());
                    } else if (cellData instanceof String) {
                        cell.setCellValue((String) cellData);
                    } else if (cellData != null) {
                        cell.setCellValue(cellData.toString());
                    }
                }
            }
        }
    }

    public static List<Double> mergeReporte805(List<Double>[] reporteArray805) {
        List<Double> mergedList = new ArrayList<>();
        for (List<Double> list : reporteArray805) {
            mergedList.addAll(list);
        }
        return mergedList;
    }

    private void simulateOpportunities(int iterationNumber, Map<Integer, Object> excelRowData) {
        for (int objectivoIndex = 0; objectivoIndex < numOportunidades; objectivoIndex++) {
            try {
                int excelNum = iterationNumber + 1;
                excelRowData.put(HEADERS_SIZE * objectivoIndex, excelNum);
                if (pruebaGeologica(objectivoIndex, iterationNumber)) {
                    handleSuccessfulSimulation(objectivoIndex, excelRowData, iterationNumber);
                } else {
                    handleFailedSimulation(objectivoIndex, excelRowData, iterationNumber);
                }
            } catch (Exception e) {
                System.err.println("Error in simulation iteration for objective " + objectivoIndex + 
                                  " (iteration " + iterationNumber + "): " + e.getMessage());
                e.printStackTrace();
                excelRowData.put(HEADERS_SIZE * objectivoIndex + 1, "Error: " + e.getMessage());
            }
        }
        
        // Always store the row data, even if some objectives failed
        excelRowBuffers.put(iterationNumber, excelRowData);
    }

    private Oportunidad getOportunidad(int index) {
        Oportunidad[] ops = oportunidadRef.get();
        if (ops == null || index < 0 || index >= ops.length) {
            return null;
        }
        return ops[index];
    }
    

    private void handleSuccessfulSimulation(int objectivoIndex, Map<Integer, Object> excelRowData,
            int iterationNumber) {
        exitos[objectivoIndex]++;

        excelRowData.put(HEADERS_SIZE * objectivoIndex + 1, "Éxito");

        int baseIndex = iterationNumber * numberOfVariables;

        double aleatorioRecurso = 0.01
                + (0.99 - 0.01) * randomNumbers[objectivoIndex].get(baseIndex + Indices.ALEATORIO_RECURSO);
        // aleatorioRecurso = (objectivoIndex == 1) ? 0.533433260355486 : 0;
        double recurso = calcularRecursoProspectivo(
                aleatorioRecurso,
                oportunidad[objectivoIndex].getPceP10(),
                oportunidad[objectivoIndex].getPceP90());
        updateExcelData(excelRowData, HEADERS_SIZE * objectivoIndex + 2, aleatorioRecurso,
                HEADERS_SIZE * objectivoIndex + 3, recurso);
        double limiteEconomico = calcularLimiteEconomico(recurso) / 12;
        limitesEconomicosRepetidos.merge(limiteEconomico, 1, Integer::sum);
        calculateAndStoreSimulationResults(objectivoIndex, baseIndex, recurso, aleatorioRecurso, excelRowData);

        executeSimulationMicroservices(objectivoIndex, excelRowData, iterationNumber);
    }

    private void handleFailedSimulation(int objectivoIndex, Map<Integer, Object> excelRowData, int iterationNumber) {
        fracasos[objectivoIndex]++;
        excelRowData.put(HEADERS_SIZE * objectivoIndex + 1, "Fracaso");

        for (int j = 2; j < headers.length; j++) {
            excelRowData.put(HEADERS_SIZE * objectivoIndex + j, 0);
        }
        limitesEconomicosRepetidos.merge(0.0, 1, Integer::sum);
        updateExploratoryValues(objectivoIndex, excelRowData);
        executeSimulationMicroservices(objectivoIndex, excelRowData, iterationNumber);

    }

    private void updateExploratoryValues(int objectivoIndex, Map<Integer, Object> excelRowData) {
        excelRowData.put(HEADERS_SIZE * objectivoIndex + 10, investments[objectivoIndex].triangularExploratorioMin);

        excelRowData.put(HEADERS_SIZE * objectivoIndex + 11, investments[objectivoIndex].triangularExploratorioPer);

        excelRowData.put(HEADERS_SIZE * objectivoIndex + 12, investments[objectivoIndex].triangularExploratorioTer);

    }

    private void executeSimulationMicroservices(int objectivoIndex, Map<Integer, Object> excelRowData,
            int iterationNumber) {
        SimulacionMicrosMulti.SimulationParameters params = new SimulacionMicrosMulti.SimulationParameters();

        // Set basic identification parameters
        params.setIdOportunidad(Integer.valueOf(idOportunidadObjetivo[objectivoIndex]));
        params.setVersion(Integer.valueOf(oportunidad[objectivoIndex].getActualIdVersion()));

        if (excelRowData.get(HEADERS_SIZE * objectivoIndex + 1).equals("Éxito")) {

            params.setCuota((Double) excelRowData.get(HEADERS_SIZE * objectivoIndex + 5)); // gastoTriangular
            params.setDeclinada((Double) excelRowData.get(HEADERS_SIZE * objectivoIndex + 7)); // declinacion
            params.setPce((Double) excelRowData.get(HEADERS_SIZE * objectivoIndex + 3)); // recurso
            params.setArea((Double) excelRowData.get(HEADERS_SIZE * objectivoIndex + 9)); // area

            // Set investment-related parameters
            params.setPlataformaDesarrollo(investments[objectivoIndex].triangularInversionPlataforma);
            params.setLineaDeDescarga(investments[objectivoIndex].triangularInversionLineaDescarga);
            params.setEstacionCompresion(investments[objectivoIndex].triangularInversionEstacionCompresion);
            params.setDucto(investments[objectivoIndex].triangularInversionDucto);
            params.setBateria(investments[objectivoIndex].triangularInversionBat);

            // Exploratory parameters
            params.setTriangularExploratorioMin(investments[objectivoIndex].triangularExploratorioMin);
            params.setTriangularExploratorioPer(investments[objectivoIndex].triangularExploratorioPer);
            params.setTriangularExploratorioTer(investments[objectivoIndex].triangularExploratorioTer);

            // Development infrastructure parameters
            params.setTriangularDESInfra((Double) excelRowData.get(HEADERS_SIZE * objectivoIndex + 13));
            params.setTriangularDESPer((Double) excelRowData.get(HEADERS_SIZE * objectivoIndex + 14));
            params.setTriangularDESTer((Double) excelRowData.get(HEADERS_SIZE * objectivoIndex + 15));

            // Other investment parameters
            params.setTriangularInversionArbolesSubmarinos(
                    investments[objectivoIndex].triangularInversionArbolesSubmarinos);
            params.setTriangularInversionManifolds(investments[objectivoIndex].triangularInversionManifolds);
            params.setTriangularInversionRisers(investments[objectivoIndex].triangularInversionRisers);
            params.setTriangularInversionSistemasDeControl(
                    investments[objectivoIndex].triangularInversionSistemasDeControl);
            params.setTriangularInversionCubiertaDeProces(
                    investments[objectivoIndex].triangularInversionCubiertaDeProces);
            params.setTriangularInversionBuqueTanqueCompra(
                    investments[objectivoIndex].triangularInversionBuqueTanqueCompra);
            params.setTriangularInversionBuqueTanqueRenta(
                    investments[objectivoIndex].triangularInversionBuqueTanqueRenta);
        } else {
            params.setCuota(0);
            params.setDeclinada(0);
            params.setPce(0);
            params.setArea(0);
            params.setPlataformaDesarrollo(0);
            params.setLineaDeDescarga(0);
            params.setEstacionCompresion(0);
            params.setDucto(0);
            params.setBateria(0);
            params.setTriangularExploratorioMin(investments[objectivoIndex].triangularExploratorioMin);
            params.setTriangularExploratorioPer(investments[objectivoIndex].triangularExploratorioPer);
            params.setTriangularExploratorioTer(investments[objectivoIndex].triangularExploratorioTer);
            params.setTriangularDESInfra(0);
            params.setTriangularDESPer(0);
            params.setTriangularDESTer(0);
            params.setTriangularInversionArbolesSubmarinos(0);
            params.setTriangularInversionManifolds(0);
            params.setTriangularInversionRisers(0);
            params.setTriangularInversionSistemasDeControl(0);
            params.setTriangularInversionCubiertaDeProces(0);
            params.setTriangularInversionBuqueTanqueCompra(0);
            params.setTriangularInversionBuqueTanqueRenta(0);
        }

        ConcurrentHashMap<Integer, SimulacionMicrosMulti.SimulationParameters> paramsMap;
        synchronized (simulationParametersMatrix) {
            paramsMap = simulationParametersMatrix.computeIfAbsent(iterationNumber, k -> new ConcurrentHashMap<>());
            paramsMap.put(objectivoIndex, params);
        }

    }

    public static void inspectResponse(Object resultado) {
        try {

            if (resultado == null) {
                System.err.println("Response is null");
                return;
            }
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);

            // Print basic object information
            System.err.println("Response Type: " + resultado.getClass().getName());

            // Convert to JSON for detailed inspection
            String jsonOutput = mapper.writeValueAsString(resultado);
            System.err.println("\nResponse Content (JSON):");
            System.err.println(jsonOutput);

        } catch (Exception e) {
            System.err.println("Error during response inspection:");
            System.err.println("Error type: " + e.getClass().getName());
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void calculateAndStoreSimulationResults(int objectivoIndex, int baseIndex, double recurso, double aleatorioRecurso, Map<Integer, Object> excelRowData) {
        if (oportunidad[objectivoIndex] == null) {
            throw new IllegalStateException("Oportunidad not initialized for index: " + objectivoIndex);
        }

        
        double gastoTriangular = calcularGastoInicial(recurso, aleatorioRecurso, objectivoIndex);
        updateExcelData(excelRowData, HEADERS_SIZE * objectivoIndex + 4, aleatorioRecurso,
                HEADERS_SIZE * objectivoIndex + 5, gastoTriangular);

        double aleatorioDeclinacion = randomNumbers[objectivoIndex].get(baseIndex + Indices.ALEATORIO_DECLINACION);
        // aleatorioDeclinacion = objectivoIndex == 1 ? 0.555771600060453 : 0;
        double declinacion = triangularDistribution(
                recurso,
                oportunidad[objectivoIndex].getPrimeraDeclinacionMin(),
                oportunidad[objectivoIndex].getPrimeraDeclinacionMAX(),
                oportunidad[objectivoIndex].getPrimeraDeclinacionMP(),
                aleatorioDeclinacion);

        updateExcelData(excelRowData, HEADERS_SIZE * objectivoIndex + 6, aleatorioDeclinacion,
                HEADERS_SIZE * objectivoIndex + 7, declinacion);

        double area = calcularRecursoProspectivo(
                aleatorioRecurso,
                oportunidad[objectivoIndex].getArea10(),
                oportunidad[objectivoIndex].getArea90());

        updateExcelData(excelRowData, HEADERS_SIZE * objectivoIndex + 8, aleatorioRecurso,
                HEADERS_SIZE * objectivoIndex + 9, area);

        // Store exploratory values
        updateExploratoryValues(objectivoIndex, excelRowData);

        // Calculate and store development infrastructure
        double aleatorioDesInfra = randomNumbers[objectivoIndex].get(baseIndex + Indices.ALEATORIO_DES_INFRA);
        double triangularDESInfra = triangularDistribution(
                recurso,
                oportunidad[objectivoIndex].getInfraestructuraMinDES(),
                oportunidad[objectivoIndex].getInfraestructuraMaxDES(),
                oportunidad[objectivoIndex].getInfraestructuraMPDES(),
                aleatorioDesInfra);
        excelRowData.put(HEADERS_SIZE * objectivoIndex + 13, triangularDESInfra);

        // Calculate and store development perforation
        double aleatorioDesPer = randomNumbers[objectivoIndex].get(baseIndex + Indices.ALEATORIO_DES_PER);
        double triangularDESPer = triangularDistribution(
                recurso,
                oportunidad[objectivoIndex].getPerforacionMinDES(),
                oportunidad[objectivoIndex].getPerforacionMaxDES(),
                oportunidad[objectivoIndex].getPerforacionMPDES(),
                aleatorioDesPer);
        excelRowData.put(HEADERS_SIZE * objectivoIndex + 14, triangularDESPer);

        // Calculate and store development termination
        double aleatorioDesTer = randomNumbers[objectivoIndex].get(baseIndex + Indices.ALEATORIO_DES_TER);
        double triangularDESTer = triangularDistribution(
                recurso,
                oportunidad[objectivoIndex].getTerminacionMinDES(),
                oportunidad[objectivoIndex].getTerminacionMaxDES(),
                oportunidad[objectivoIndex].getTerminacionMPDES(),
                aleatorioDesTer);
        excelRowData.put(HEADERS_SIZE * objectivoIndex + 15, triangularDESTer);

        if (oportunidad[objectivoIndex].getInversionOportunidad().getBateriaMin() != 0) {
            double aleatorioInversion = randomNumbers[objectivoIndex].get(baseIndex + Indices.BATERIA_MIN);
            investments[objectivoIndex].triangularInversionBat = triangularDistribution(
                    recurso,
                    oportunidad[objectivoIndex].getInversionOportunidad().getBateriaMin(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getBateriaMax(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getBateriaMp(),
                    aleatorioInversion);
            excelRowData.put(HEADERS_SIZE * objectivoIndex + 16, investments[objectivoIndex].triangularInversionBat);
        }

        if (oportunidad[objectivoIndex].getInversionOportunidad().getPlataformadesarrolloMin() != 0) {
            double aleatorioInversion = randomNumbers[objectivoIndex].get(baseIndex + Indices.ALEATORIO_INVERSION);
            investments[objectivoIndex].triangularInversionPlataforma = triangularDistribution(
                    recurso,
                    oportunidad[objectivoIndex].getInversionOportunidad().getPlataformadesarrolloMin(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getPlataformadesarrolloMax(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getPlataformadesarrolloMp(),
                    aleatorioInversion);
            excelRowData.put(HEADERS_SIZE * objectivoIndex + 17,
                    investments[objectivoIndex].triangularInversionPlataforma);
        }

        if (oportunidad[objectivoIndex].getInversionOportunidad().getLineadedescargaMin() != 0) {
            double aleatorioInversion = randomNumbers[objectivoIndex].get(baseIndex + Indices.ALEATORIO_LINEA_DESCARGA);
            investments[objectivoIndex].triangularInversionLineaDescarga = triangularDistribution(
                    recurso,
                    oportunidad[objectivoIndex].getInversionOportunidad().getLineadedescargaMin(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getLineadedescargaMax(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getLineadedescargaMp(),
                    aleatorioInversion);
            excelRowData.put(HEADERS_SIZE * objectivoIndex + 18,
                    investments[objectivoIndex].triangularInversionLineaDescarga);
        }

        // Calculate and store compression station investment
        if (oportunidad[objectivoIndex].getInversionOportunidad().getEstacioncompresionMin() != 0) {
            double aleatorioInversion = randomNumbers[objectivoIndex]
                    .get(baseIndex + Indices.ALEATORIO_ESTACION_COMPRESION);
            investments[objectivoIndex].triangularInversionEstacionCompresion = triangularDistribution(
                    recurso,
                    oportunidad[objectivoIndex].getInversionOportunidad().getEstacioncompresionMin(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getEstacioncompresionMax(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getEstacioncompresionMp(),
                    aleatorioInversion);
            excelRowData.put(HEADERS_SIZE * objectivoIndex + 19,
                    investments[objectivoIndex].triangularInversionEstacionCompresion);
        }

        // Calculate and store pipeline investment
        if (oportunidad[objectivoIndex].getInversionOportunidad().getDuctoMin() != 0) {
            double aleatorioInversion = randomNumbers[objectivoIndex].get(baseIndex + Indices.ALEATORIO_DUCTO);
            investments[objectivoIndex].triangularInversionDucto = triangularDistribution(
                    recurso,
                    oportunidad[objectivoIndex].getInversionOportunidad().getDuctoMin(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getDuctoMax(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getDuctoMp(),
                    aleatorioInversion);
            excelRowData.put(HEADERS_SIZE * objectivoIndex + 20, investments[objectivoIndex].triangularInversionDucto);
        }

        // Calculate and store submarine trees investment
        if (oportunidad[objectivoIndex].getInversionOportunidad().getArbolessubmarinosMin() != 0) {
            double aleatorioInversion = randomNumbers[objectivoIndex]
                    .get(baseIndex + Indices.ALEATORIO_ARBOLES_SUBMARINOS);
            investments[objectivoIndex].triangularInversionArbolesSubmarinos = triangularDistribution(
                    recurso,
                    oportunidad[objectivoIndex].getInversionOportunidad().getArbolessubmarinosMin(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getArbolessubmarinosMax(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getArbolessubmarinosMp(),
                    aleatorioInversion);
            excelRowData.put(HEADERS_SIZE * objectivoIndex + 21,
                    investments[objectivoIndex].triangularInversionArbolesSubmarinos);
        }

        // Calculate and store manifolds investment
        if (oportunidad[objectivoIndex].getInversionOportunidad().getManifoldsMin() != 0) {
            double aleatorioInversion = randomNumbers[objectivoIndex].get(baseIndex + Indices.ALEATORIO_MANIFOLDS);
            investments[objectivoIndex].triangularInversionManifolds = triangularDistribution(
                    recurso,
                    oportunidad[objectivoIndex].getInversionOportunidad().getManifoldsMin(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getManifoldsMax(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getManifoldsMp(),
                    aleatorioInversion);
            excelRowData.put(HEADERS_SIZE * objectivoIndex + 22,
                    investments[objectivoIndex].triangularInversionManifolds);
        }

        // Calculate and store risers investment
        if (oportunidad[objectivoIndex].getInversionOportunidad().getRisersMin() != 0) {
            double aleatorioInversion = randomNumbers[objectivoIndex].get(baseIndex + Indices.ALEATORIO_RISERS);
            investments[objectivoIndex].triangularInversionRisers = triangularDistribution(
                    recurso,
                    oportunidad[objectivoIndex].getInversionOportunidad().getRisersMin(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getRisersMax(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getRisersMp(),
                    aleatorioInversion);
            excelRowData.put(HEADERS_SIZE * objectivoIndex + 23, investments[objectivoIndex].triangularInversionRisers);
        }

        // Calculate and store control systems investment
        if (oportunidad[objectivoIndex].getInversionOportunidad().getSistemasdecontrolMin() != 0) {
            double aleatorioInversion = randomNumbers[objectivoIndex]
                    .get(baseIndex + Indices.ALEATORIO_SISTEMAS_CONTROL);
            investments[objectivoIndex].triangularInversionSistemasDeControl = triangularDistribution(
                    recurso,
                    oportunidad[objectivoIndex].getInversionOportunidad().getSistemasdecontrolMin(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getSistemasdecontrolMax(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getSistemasdecontrolMp(),
                    aleatorioInversion);
            excelRowData.put(HEADERS_SIZE * objectivoIndex + 24,
                    investments[objectivoIndex].triangularInversionSistemasDeControl);
        }

        // Calculate and store process deck investment
        if (oportunidad[objectivoIndex].getInversionOportunidad().getCubiertadeprocesMin() != 0) {
            double aleatorioInversion = randomNumbers[objectivoIndex]
                    .get(baseIndex + Indices.ALEATORIO_CUBIERTA_PROCES);
            investments[objectivoIndex].triangularInversionCubiertaDeProces = triangularDistribution(
                    recurso,
                    oportunidad[objectivoIndex].getInversionOportunidad().getCubiertadeprocesMin(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getCubiertadeprocesMax(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getCubiertadeprocesMp(),
                    aleatorioInversion);
            excelRowData.put(HEADERS_SIZE * objectivoIndex + 25,
                    investments[objectivoIndex].triangularInversionCubiertaDeProces);
        }

        // Calculate and store tanker ship purchase investment
        if (oportunidad[objectivoIndex].getInversionOportunidad().getBuquetanquecompraMin() != 0) {
            double aleatorioInversion = randomNumbers[objectivoIndex]
                    .get(baseIndex + Indices.ALEATORIO_BUQUE_TANQUE_COMPRA);
            investments[objectivoIndex].triangularInversionBuqueTanqueCompra = triangularDistribution(
                    recurso,
                    oportunidad[objectivoIndex].getInversionOportunidad().getBuquetanquecompraMin(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getBuquetanquecompraMax(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getBuquetanquecompraMp(),
                    aleatorioInversion);
            excelRowData.put(HEADERS_SIZE * objectivoIndex + 26,
                    investments[objectivoIndex].triangularInversionBuqueTanqueCompra);
        }

        // Calculate and store tanker ship rental investment
        if (oportunidad[objectivoIndex].getInversionOportunidad().getBuquetanquerentaMin() != 0) {
            double aleatorioInversion = randomNumbers[objectivoIndex]
                    .get(baseIndex + Indices.ALEATORIO_BUQUE_TANQUE_RENTA);
            investments[objectivoIndex].triangularInversionBuqueTanqueRenta = triangularDistribution(
                    recurso,
                    oportunidad[objectivoIndex].getInversionOportunidad().getBuquetanquerentaMin(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getBuquetanquerentaMax(),
                    oportunidad[objectivoIndex].getInversionOportunidad().getBuquetanquerentaMp(),
                    aleatorioInversion);
            excelRowData.put(HEADERS_SIZE * objectivoIndex + 27,
                    investments[objectivoIndex].triangularInversionBuqueTanqueRenta);
        }
    }

    private void updateExcelData(Map<Integer, Object> excelRowData, int index1, double value1, int index2,
            double value2) {
        excelRowData.put(index1, value1);
        excelRowData.put(index2, value2);
    }

    private double calcularRecursoProspectivo(double aleatorio, double percentil10, double percentil90) {
        if(percentil10 == percentil90) return percentil10;
        NormalDistribution normalStandard = new NormalDistribution(0, 1);
        double z90 = normalStandard.inverseCumulativeProbability(0.9);
        double mediaLog = (Math.log(percentil10) + Math.log(percentil90)) / 2;
        double desviacionLog = (Math.log(percentil10) - mediaLog) / z90;

        NormalDistribution normal = new NormalDistribution(mediaLog, desviacionLog);

        double logValue = normal.inverseCumulativeProbability(aleatorio);
        double result = Math.exp(logValue);

        if (Double.isNaN(result)) {
            result = 0.0;
        }
        return result;
    }

    private double calcularLimiteEconomico(double pce) {
        if (pce == 0) {
            return 0.0;
        }
        double pceSquared = pce * pce;
        return Math.round((-0.00003 * pceSquared) + (pce * 0.068) + 4.3824) * 12;
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


    public double calcularGastoInicial(double PCE, double aleatorioGasto, int objectivoIndex) {
        if (objectivoIndex < 0 || objectivoIndex >= numOportunidades) {
            throw new IllegalArgumentException("Invalid objectivoIndex: " + objectivoIndex);

        }

        double gastoInicialMin = 0.0;
        double gastoInicialMP = 0.0;
        double gastoInicialMax = 0.0;

        if (oportunidad[objectivoIndex].getIdHidrocarburo() == 1 ||
                oportunidad[objectivoIndex].getIdHidrocarburo() == 2 ||
                oportunidad[objectivoIndex].getIdHidrocarburo() == 4 ||
                oportunidad[objectivoIndex].getIdHidrocarburo() == 6) {
            fcAceite = oportunidad[objectivoIndex].getFcAceite();
            gastoInicialMin = (fcAceite != 0)
                    ? oportunidad[objectivoIndex].getGastoMINAceite()
                            / fcAceite
                    : 0;
            gastoInicialMP = (fcAceite != 0)
                    ? oportunidad[objectivoIndex].getGastoMPAceite() / fcAceite
                    : 0;
            gastoInicialMax = (fcAceite != 0)
                    ? oportunidad[objectivoIndex].getGastoMAXAceite()
                            / fcAceite
                    : 0;

        } else if (oportunidad[objectivoIndex].getIdHidrocarburo() == 3
                || oportunidad[objectivoIndex].getIdHidrocarburo() == 5
                || oportunidad[objectivoIndex].getIdHidrocarburo() == 7
                || oportunidad[objectivoIndex].getIdHidrocarburo() == 9) {
            fcGas = oportunidad[objectivoIndex].getFcGas();

            gastoInicialMin = (fcGas != 0)
                    ? oportunidad[objectivoIndex].getGastoMINAceite() / fcGas
                    : 0;
            gastoInicialMP = (fcGas != 0)
                    ? oportunidad[objectivoIndex].getGastoMPAceite() / fcGas
                    : 0;
            gastoInicialMax = (fcGas != 0)
                    ? oportunidad[objectivoIndex].getGastoMAXAceite() / fcGas
                    : 0;

        } else {
            fcCondensado = oportunidad[objectivoIndex].getFcCondensado();
            gastoInicialMin = (fcCondensado != 0)
                    ? oportunidad[objectivoIndex].getGastoMINAceite()
                            / fcCondensado
                    : 0;
            gastoInicialMP = (fcCondensado != 0)
                    ? oportunidad[objectivoIndex].getGastoMPAceite()
                            / fcCondensado
                    : 0;
            gastoInicialMax = (fcCondensado != 0)
                    ? oportunidad[objectivoIndex].getGastoMAXAceite()
                            / fcCondensado
                    : 0;

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
                return gmax - Math.sqrt((1 - aleatorio) * (gmax - gmp) * (gmax - gmin));
            }
        }
        return gmp;

    }

    private boolean pruebaGeologica(int objectivoIndex, int iterationNumber) {
        if (pgValue == 1) {
            return true;
        } else {
            Oportunidad oportunidad = getOportunidad(objectivoIndex);
            if (oportunidad == null) {
                System.err.println("Warning: oportunidad[" + objectivoIndex + "] is null.");
                return false;
            }
            return precomputedRandomNumbers[objectivoIndex][iterationNumber] <= oportunidad.getPg();
        }
    }


    private Workbook createWorkbook() {
        // Store and clear interrupted status
        boolean wasInterrupted = Thread.interrupted();
        
        try {
            try {
                org.apache.logging.log4j.LogManager.getLogger(MonteCarloSimulationMultiObject.class);
            } catch (Throwable t) {
                System.err.println("Log4j initialization warning: " + t.getMessage());
            }
            
            return new XSSFWorkbook();
        } finally {
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void createHeaderRow(Sheet sheet, Workbook workbook) {
        Row headerRow = sheet.createRow(0);

        IndexedColors[] colors = {
                IndexedColors.LIGHT_BLUE, IndexedColors.GREY_25_PERCENT,
                IndexedColors.LIGHT_GREEN, IndexedColors.LIGHT_YELLOW,
                IndexedColors.LIGHT_ORANGE, IndexedColors.LIGHT_CORNFLOWER_BLUE,
                IndexedColors.LIGHT_TURQUOISE, IndexedColors.BROWN, IndexedColors.GOLD
        };
        for (int oportunidadNum = 0; oportunidadNum < numOportunidades; oportunidadNum++) {
            for (int i = 0; i < headers.length; i++) {
                int columnIndex = headers.length * oportunidadNum + i;
                Cell cell = headerRow.createCell(columnIndex);
                formatHeaderCell(workbook, cell, headers[i], colors[i % colors.length]);
                sheet.setColumnWidth(columnIndex, 5000);
            }
        }
    }

    private void formatHeaderCell(Workbook workbook, Cell cell, String value, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);

        cell.setCellValue(value);
        cell.setCellStyle(style);
    }
    

}
