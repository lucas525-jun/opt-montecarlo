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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MonteCarloSimulationMultiObject {
    @Setter
    private MonteCarloDAO monteCarloDAO;

    private final RestTemplate restTemplate = new RestTemplate();
    // Constants
    private static final int DEFAULT_ITERATIONS = 3000;
    private static final int DEFAULT_NUM_VARIABLES = 19;

    // Configuration
    private final String version;
    private final int[] idOportunidadObjetivo;
    private final int cantidadIteraciones;
    private final int numOportunidades;
    private final int numberOfVariables;

    // Core simulation data
    private Oportunidad[] oportunidad;
    private List<Double>[] randomNumbers;
    private double[] mediaTruncada;
    private double[] kilometraje;

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

    private final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, SimulacionMicrosMulti.SimulationParameters>> simulationParametersMatrix = new ConcurrentHashMap<>();
    private SimulacionMicrosMulti simulacionMicrosMulti;

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

    public MonteCarloSimulationMultiObject(String version, int[] idOportunidadObjetivo, String economicEvaHost,
            String economicEvaPort) {
        validateInputs(version, idOportunidadObjetivo);
        this.version = version;
        this.idOportunidadObjetivo = idOportunidadObjetivo.clone();

        this.cantidadIteraciones = DEFAULT_ITERATIONS;
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
        this.randomNumbers = new ArrayList[numOportunidades];
        for (int i = 0; i < numOportunidades; i++) {
            this.randomNumbers[i] = IntStream.range(0, cantidadIteraciones * numberOfVariables)
                    .mapToDouble(j -> ThreadLocalRandom.current().nextDouble())
                    .boxed()
                    .collect(Collectors.toList());
        }
    }

    private void loadOportunidades() {
        // System.err.println("idOportunidadObjetivo 0 :" + idOportunidadObjetivo[0]);
        // System.err.println("idOportunidadObjetivo 1 :" + idOportunidadObjetivo[1]);
        for (int i = 0; i < numOportunidades; i++) {
            try {
                Oportunidad eachOportunidad = monteCarloDAO.executeQuery(version, idOportunidadObjetivo[i]);

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
        long startTime = System.nanoTime();
        iterationNumber.set(1);
        Workbook workbook = createWorkbook();
        Sheet sheet = workbook.createSheet("Simulación Monte Carlo");
        createHeaderRow(sheet, workbook);

        int optimalThreads = Runtime.getRuntime().availableProcessors();
        ForkJoinPool customThreadPool = new ForkJoinPool(optimalThreads);

        try {
            customThreadPool.submit(() -> IntStream.range(0,
                    cantidadIteraciones).parallel().forEach(i -> {
                        Map<Integer, Object> excelRowData = new ConcurrentHashMap<>();
                        excelRowBuffers.put(i, excelRowData);

                        ConcurrentHashMap<Integer, SimulacionMicrosMulti.SimulationParameters> paramsArray = simulationParametersMatrix
                                .get(i);

                        simulateOpportunityRecursively(0, i, excelRowData);

                        if (paramsArray != null) {
                            resultados[i] = simulacionMicrosMulti.ejecutarSimulacionMulti(paramsArray);
                            synchronized (resultadosQueue) {
                                resultadosQueue.add(resultados[i]);
                            }
                        } else {
                            System.err.println("No simulation parameters found for iteration " + i);
                        }
                    })).join();
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

        double[] reporteArray804 = new double[numOportunidades];
        List<Double>[] reporteArray805 = new List[numOportunidades];

        // Calculate for each opportunity
        for (int i = 0; i < numOportunidades; i++) {
            reporteArray804[i] = calcularReporte804(kilometraje[i], exitos[i], cantidadIteraciones);
            reporteArray805[i] = escaleraLimEconomicos(limitesEconomicosRepetidos, mediaTruncada[i],
                    cantidadIteraciones);
        }

        double reporte804 = mergeReporte804(reporteArray804);
        List<Double> reporte805 = mergeReporte805(reporteArray805);

        responseData = new ArrayList<>(resultadosQueue);

        if (!responseData.isEmpty() && responseData.get(0) instanceof Map) {
            Map<String, Object> primerElemento = (Map<String, Object>) responseData.get(0);
            primerElemento.put("reporte804", reporte804);
            primerElemento.put("reporte805", reporte805);
        }

        writeResultsToExcel(sheet);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String tempFileName = "resultadosMultiObjectivo_temp_merged" + oportunidad[0].getIdOportunidadObjetivo()
                    + ".json";
            String resFileName = "resultadosMultiObjectivo_merged" + oportunidad[0].getIdOportunidadObjetivo()
                    + ".json";

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
            e.printStackTrace();
        }

        try (FileOutputStream fileOut = new FileOutputStream(
                "MonteCarloSimulationMultiObject" + oportunidad[0].getIdOportunidadObjetivo() + ".xlsx")) {
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
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        System.err.println("Execution time " + duration / 1000000 + " s");

        return ResponseEntity.ok(responseData);
    }

    private void writeResultsToExcel(Sheet sheet) {
        int rowIndex = 1; // Start from row 1 because row 0 is reserved for headers

        for (Map.Entry<Integer, Map<Integer, Object>> entry : excelRowBuffers.entrySet()) {
            Map<Integer, Object> buffer = entry.getValue();
            if (buffer != null) {
                Row row = sheet.createRow(rowIndex++);
                for (Map.Entry<Integer, Object> cellEntry : buffer.entrySet()) {
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

    public static double mergeReporte804(double[] reporteArray804) {
        double sum = 0.0;
        for (double value : reporteArray804) {
            sum += value;
        }
        return sum / reporteArray804.length; // Average (change to sum if needed)
    }

    // Flatten the List<Double>[] into a single List<Double>
    public static List<Double> mergeReporte805(List<Double>[] reporteArray805) {
        List<Double> mergedList = new ArrayList<>();
        for (List<Double> list : reporteArray805) {
            mergedList.addAll(list);
        }
        return mergedList;
    }

    // New method to encapsulate single opportunity simulation
    private void simulateOpportunityRecursively(int objectivoIndex, int iterationNumber,
            Map<Integer, Object> excelRowData) {
        if (objectivoIndex >= numOportunidades) {
            return;
        }

        try {
            String excelNum = oportunidad[objectivoIndex].getOportunidad()
                    + oportunidad[objectivoIndex].getIdOportunidadObjetivo() + "_" + (iterationNumber + 1);

            excelRowData.put(HEADERS_SIZE * objectivoIndex, excelNum);

            if (pruebaGeologica(objectivoIndex)) {
                handleSuccessfulSimulation(objectivoIndex, excelRowData, iterationNumber);
            } else {
                handleFailedSimulation(objectivoIndex, excelRowData, iterationNumber);
            }
            excelRowBuffers.put(iterationNumber, excelRowData);

        } catch (Exception e) {
            System.err.println("Error in simulation iteration : " + e.getMessage());
            throw e;
        } finally {

        }
    }

    private void handleSuccessfulSimulation(int objectivoIndex, Map<Integer, Object> excelRowData,
            int iterationNumber) {
        exitos[objectivoIndex]++;

        excelRowData.put(HEADERS_SIZE * objectivoIndex + 1, "Éxito");

        int baseIndex = iterationNumber * numberOfVariables;

        double aleatorioRecurso = 0.01
                + (0.99 - 0.01) * randomNumbers[objectivoIndex].get(baseIndex + Indices.ALEATORIO_RECURSO);
        // aleatorioRecurso = (objectivoIndex == 0) ? 0.647631766999635 :
        // 0.921227551506061;
        double recurso = calcularRecursoProspectivo(
                aleatorioRecurso,
                oportunidad[objectivoIndex].getPceP10(),
                oportunidad[objectivoIndex].getPceP90());
        updateExcelData(excelRowData, HEADERS_SIZE * objectivoIndex + 2, aleatorioRecurso,
                HEADERS_SIZE * objectivoIndex + 3, recurso);
        double limiteEconomico = calcularLimiteEconomico(recurso) / 12;
        limitesEconomicosRepetidos.merge(limiteEconomico, 1, Integer::sum);
        calculateAndStoreSimulationResults(objectivoIndex, baseIndex, recurso, excelRowData);

        executeSimulationMicroservices(objectivoIndex, excelRowData, iterationNumber);
        simulateOpportunityRecursively(objectivoIndex + 1, iterationNumber, excelRowData);
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
        simulateOpportunityRecursively(objectivoIndex + 1, iterationNumber, excelRowData);

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

        synchronized (simulationParametersMatrix) {
            simulationParametersMatrix
                    .computeIfAbsent(iterationNumber, k -> new ConcurrentHashMap<>())
                    .put(objectivoIndex, params);
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

    private void calculateAndStoreSimulationResults(int objectivoIndex, int baseIndex, double recurso,
            Map<Integer, Object> excelRowData) {
        if (oportunidad[objectivoIndex] == null) {
            throw new IllegalStateException("Oportunidad not initialized for index: " + objectivoIndex);
        }

        double aleatorioGasto = randomNumbers[objectivoIndex].get(baseIndex + Indices.ALEATORIO_GASTO);
        // aleatorioGasto = objectivoIndex == 0 ? 0.386928163472988 : 0.856955524053262;
        double gastoTriangular = calcularGastoInicial(recurso, aleatorioGasto, objectivoIndex);
        updateExcelData(excelRowData, HEADERS_SIZE * objectivoIndex + 4, aleatorioGasto,
                HEADERS_SIZE * objectivoIndex + 5, gastoTriangular);

        double aleatorioDeclinacion = randomNumbers[objectivoIndex].get(baseIndex + Indices.ALEATORIO_DECLINACION);
        // aleatorioDeclinacion = objectivoIndex == 0 ? 0.659034740341855 :
        // 0.951330768695008;
        double declinacion = triangularDistribution(
                recurso,
                oportunidad[objectivoIndex].getPrimeraDeclinacionMin(),
                oportunidad[objectivoIndex].getPrimeraDeclinacionMAX(),
                oportunidad[objectivoIndex].getPrimeraDeclinacionMP(),
                aleatorioDeclinacion);

        updateExcelData(excelRowData, HEADERS_SIZE * objectivoIndex + 6, aleatorioDeclinacion,
                HEADERS_SIZE * objectivoIndex + 7, declinacion);

        double aleatorioArea = randomNumbers[objectivoIndex].get(baseIndex + Indices.ALEATORIO_AREA);
        // aleatorioArea = (objectivoIndex == 0) ? 0.406607107070359 :
        // 0.593453108800162;

        double area = calcularRecursoProspectivo(
                aleatorioArea,
                oportunidad[objectivoIndex].getArea10(),
                oportunidad[objectivoIndex].getArea90());

        updateExcelData(excelRowData, HEADERS_SIZE * objectivoIndex + 8, aleatorioArea,
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
        NormalDistribution normalStandard = new NormalDistribution(0, 1);
        double z90 = normalStandard.inverseCumulativeProbability(0.9);
        double mediaLog = (Math.log(percentil10) + Math.log(percentil90)) / 2;
        double desviacionLog = (Math.log(percentil10) - mediaLog) / z90;

        NormalDistribution normal = new NormalDistribution(mediaLog, desviacionLog);

        double logValue = normal.inverseCumulativeProbability(aleatorio);
        return Math.exp(logValue);
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

    private double calcularReporte804(double kilometraje, int cantExitos, int cantidadIteraciones) {
        return kilometraje - ((cantExitos * kilometraje) / cantidadIteraciones);
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

    private boolean pruebaGeologica(int objectivoIndex) {
        // return true;
        // if (objectivoIndex == 0)
        // return true;
        // else
        // return true;

        return ThreadLocalRandom.current().nextDouble() <= oportunidad[objectivoIndex].getPg();
    }

    private Workbook createWorkbook() {
        return new XSSFWorkbook();
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

    private void writeResultsToFile(Sheet sheet, Map<Integer, Object> rowData) {
        Row row = sheet.createRow(sheet.getPhysicalNumberOfRows());
        for (Map.Entry<Integer, Object> entry : rowData.entrySet()) {
            Cell cell = row.createCell(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Number) {
                cell.setCellValue(((Number) value).doubleValue());
            } else if (value != null) {
                cell.setCellValue(value.toString());
            }
        }
    }

}
