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

public class MonteCarloSimulation {


    String version = "";
    int idOportunidadObjetivo = 0;
    Oportunidad oportunidad;



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
    double triangularInversionCubiertaDeProces, triangularInversionBuqueTanqueCompra, triangularInversionBuqueTanqueRenta;

    private Random random = new Random();

    public ResponseEntity<List<Object>> runSimulation() {

        DatabaseConnection databaseConnection = new DatabaseConnection();
        Oportunidad oportunidad = databaseConnection.executeQuery(version, idOportunidadObjetivo); // Atributos y configuraciónSystem.out.println(aleatorioRecurso);
        setOportunidad(oportunidad);

        int exitos = 0;
        int fracasos = 0;
        int valores = 0;
        List<Object> resultados = new ArrayList<>();

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
                "Linea Descarga", "E.comprension ", "ducto", "Arboles submarinos", "manifols", "risers", "Sistemas de control", "Cubierta Proces", "Buquetaquecompra","buquetaQuerenta"  // Añadido aquí
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

        // Iterar simulaciones
        for (int i = 0; i < 500; i++) {
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(i + 1); // Iteración número
            valores++;
            System.out.println("Valores: " + valores);
            ResultadoSimulacion ResultadoSimulacion = new ResultadoSimulacion();

            if (pruebaGeologica()) {
                exitos++;
                row.createCell(1).setCellValue("Éxito"); // Marcar éxito

                long startRecursoProspectivo = System.nanoTime(); // Tiempo inicial de toda la iteración

                // Valores aleatorios y cálculos para éxito
                double aleatorioRecurso = 0.01 + (0.99 - 0.01) * Math.random();
                double recurso = calcularRecursoProspectivo(aleatorioRecurso, oportunidad.getPceP10(), oportunidad.getPceP90());
                row.createCell(2).setCellValue(aleatorioRecurso);
                row.createCell(3).setCellValue(recurso);
                ResultadoSimulacion.setPce(recurso);

                System.out.println(aleatorioRecurso);

                long endRecursoProspectivo = System.nanoTime(); // Tiempo final para PCE
                System.out.println("Tiempo para cálculos de PCE: " + (endRecursoProspectivo - startRecursoProspectivo) / 1_000_000 + " ms");



                long startCuota = System.nanoTime();


                double aleatorioGasto = random.nextDouble();
                double gastoTriangular = calcularGastoInicial(recurso, aleatorioGasto);
                row.createCell(4).setCellValue(aleatorioGasto);
                row.createCell(5).setCellValue(gastoTriangular);
                ResultadoSimulacion.setGastoInicial(gastoTriangular);

                long endCuota = System.nanoTime();
                System.out.println("Tiempo para cálculos de Cuota: " + (endCuota - startCuota) / 1_000_000 + " ms");


                long startDeclinacion = System.nanoTime();

                double aleatorioDeclinacion = random.nextDouble();
                double declinacion = triangularDistribution(recurso, oportunidad.getPrimeraDeclinacionMin(),
                        oportunidad.getPrimeraDeclinacionMAX(),
                        oportunidad.getPrimeraDeclinacionMP(),
                        aleatorioDeclinacion);
                row.createCell(6).setCellValue(aleatorioDeclinacion);
                row.createCell(7).setCellValue(declinacion);
                ResultadoSimulacion.setDeclinacion(declinacion);

                long endDeclinacion = System.nanoTime();

                System.out.println("Tiempo para cálculos de declinacion: " + (endDeclinacion - startDeclinacion) / 1_000_000 + " ms");


                long startArea = System.nanoTime();

                double aleatorioArea = 0.01 + (0.99 - 0.01) * Math.random();
                double area = calcularRecursoProspectivo(aleatorioArea, oportunidad.getArea10(), oportunidad.getArea90());
                row.createCell(8).setCellValue(aleatorioArea);
                row.createCell(9).setCellValue(area);
                ResultadoSimulacion.setArea(area);

                long endArea = System.nanoTime();
                System.out.println("Tiempo para cálculos de Area: " + (endArea - startArea) / 1_000_000 + " ms");

                long startExploratorio = System.nanoTime();

                double aleatorioExploratorioInfra = random.nextDouble();
                double triangularExploratorioMin = triangularDistributionSinPCE(oportunidad.getInfraestructuraMin(),
                        oportunidad.getInfraestructuraMax(),
                        oportunidad.getInfraestructuraMP(),
                        aleatorioExploratorioInfra);
                row.createCell(10).setCellValue(triangularExploratorioMin);
                ResultadoSimulacion.setExploratoriaInfra(triangularExploratorioMin);

                double aleatorioExploratorioPerforacion = random.nextDouble();
                double triangularExploratorioPer = triangularDistributionSinPCE(oportunidad.getPerforacionMin(),
                        oportunidad.getPerforacionMax(),
                        oportunidad.getPerforacionMP(),
                        aleatorioExploratorioPerforacion);
                row.createCell(11).setCellValue(triangularExploratorioPer);
                ResultadoSimulacion.setExploratoriaPerf(triangularExploratorioPer);

                double aleatorioExploratorioTerminacion = random.nextDouble();
                double triangularExploratorioTer = triangularDistributionSinPCE(oportunidad.getTerminacionMin(),
                        oportunidad.getTerminacionMax(),
                        oportunidad.getTerminacionMP(),
                        aleatorioExploratorioTerminacion);
                row.createCell(12).setCellValue(triangularExploratorioTer);
                ResultadoSimulacion.setExploratoriaTer(triangularExploratorioTer);

                long endExploratorio = System.nanoTime();
                System.out.println("Tiempo para cálculos de Exploratorio: " + (endExploratorio - startExploratorio) / 1_000_000 + " ms");

                long startDesarrollo = System.nanoTime();

                double aleatorioDesInfra = random.nextDouble();
                double triangularDESInfra = triangularDistribution(recurso, oportunidad.getInfraestructuraMinDES(),
                        oportunidad.getInfraestructuraMaxDES(),
                        oportunidad.getInfraestructuraMPDES(),
                        aleatorioDesInfra);
                row.createCell(13).setCellValue(triangularDESInfra);
                ResultadoSimulacion.setDesarrolloInfra(triangularDESInfra);

                double aleatorioDesPer = random.nextDouble();
                double triangularDESPer = triangularDistribution(recurso, oportunidad.getPerforacionMinDES(),
                        oportunidad.getPerforacionMaxDES(),
                        oportunidad.getPerforacionMPDES(),
                        aleatorioDesPer);
                row.createCell(14).setCellValue(triangularDESPer);
                ResultadoSimulacion.setDesarrolloPerf(triangularDESPer);

                double aleatorioDesTer = random.nextDouble();
                double triangularDESTer = triangularDistribution(recurso, oportunidad.getTerminacionMinDES(),
                        oportunidad.getTerminacionMaxDES(),
                        oportunidad.getTerminacionMPDES(),
                        aleatorioDesTer);
                row.createCell(15).setCellValue(triangularDESTer);
                ResultadoSimulacion.setDesarrolloTer(triangularDESTer);

                long endDesarrollo = System.nanoTime();
                System.out.println("Tiempo para cálculos de Desarrollo: " + (endDesarrollo - startDesarrollo) / 1_000_000 + " ms");

                long startInversion = System.nanoTime();

                if (oportunidad.getInversionOportunidad().getBateriaMin() != 0) {
                    double AleatorioInverision = random.nextDouble();
                             triangularInversionBat = triangularDistribution(recurso, oportunidad.getInversionOportunidad().getBateriaMin(),
                                     oportunidad.getInversionOportunidad().getBateriaMax(),
                                     oportunidad.getInversionOportunidad().getBateriaMp(),
                            AleatorioInverision);
                    row.createCell(16).setCellValue(triangularInversionBat);
                    ResultadoSimulacion.setBateria(triangularInversionBat);
                }

                if (oportunidad.getInversionOportunidad().getPlataformadesarrolloMin() != 0) {
                    double AleatorioInverision = random.nextDouble();
                            triangularInversionPlataforma = triangularDistribution(recurso, oportunidad.getInversionOportunidad().getPlataformadesarrolloMin(),
                                    oportunidad.getInversionOportunidad().getPlataformadesarrolloMax(),
                                    oportunidad.getInversionOportunidad().getPlataformadesarrolloMp(),
                            AleatorioInverision);
                    row.createCell(17).setCellValue(triangularInversionPlataforma);
                    ResultadoSimulacion.setPlataformaDesarrollo(triangularInversionPlataforma);
                }

                if (oportunidad.getInversionOportunidad().getLineadedescargaMin() != 0) {
                    double AleatorioInverision = random.nextDouble();
                            triangularInversionLineaDescarga = triangularDistribution(recurso, oportunidad.getInversionOportunidad().getLineadedescargaMin(),
                                    oportunidad.getInversionOportunidad().getLineadedescargaMax(),
                                    oportunidad.getInversionOportunidad().getLineadedescargaMp(),
                            AleatorioInverision);
                    row.createCell(18).setCellValue(triangularInversionLineaDescarga);
                    ResultadoSimulacion.setLineaDescarga(triangularInversionLineaDescarga);
                }

                if (oportunidad.getInversionOportunidad().getEstacioncompresionMin() != 0) {
                    double AleatorioInverision = random.nextDouble();
                            triangularInversionEstacionCompresion = triangularDistribution(recurso, oportunidad.getInversionOportunidad().getEstacioncompresionMin(),
                                    oportunidad.getInversionOportunidad().getEstacioncompresionMax(),
                                    oportunidad.getInversionOportunidad().getEstacioncompresionMp(),
                            AleatorioInverision);
                    row.createCell(19).setCellValue(triangularInversionEstacionCompresion);
                    ResultadoSimulacion.setEComprension(triangularInversionEstacionCompresion);
                }

                if (oportunidad.getInversionOportunidad().getDuctoMin() != 0) {
                    double AleatorioInverision = random.nextDouble();
                            triangularInversionDucto = triangularDistribution(recurso, oportunidad.getInversionOportunidad().getDuctoMin(),
                                    oportunidad.getInversionOportunidad().getDuctoMax(),
                                    oportunidad.getInversionOportunidad().getDuctoMp(),
                            AleatorioInverision);
                    row.createCell(20).setCellValue(triangularInversionDucto);
                    ResultadoSimulacion.setDucto(triangularInversionDucto);
                }

                if (oportunidad.getInversionOportunidad().getArbolessubmarinosMin() != 0) {
                    double AleatorioInversion = random.nextDouble();
                            triangularInversionArbolesSubmarinos = triangularDistribution(recurso, oportunidad.getInversionOportunidad().getArbolessubmarinosMin(),
                                    oportunidad.getInversionOportunidad().getArbolessubmarinosMax(),
                                    oportunidad.getInversionOportunidad().getArbolessubmarinosMp(),
                            AleatorioInversion);
                    row.createCell(21).setCellValue(triangularInversionArbolesSubmarinos);
                    ResultadoSimulacion.setArbolesSubmarinos(triangularInversionArbolesSubmarinos);
                }

                if (oportunidad.getInversionOportunidad().getManifoldsMin() != 0) {
                    double AleatorioInversion = random.nextDouble();
                            triangularInversionManifolds = triangularDistribution(recurso, oportunidad.getInversionOportunidad().getManifoldsMin(),
                                    oportunidad.getInversionOportunidad().getManifoldsMax(),
                                    oportunidad.getInversionOportunidad().getManifoldsMp(),
                            AleatorioInversion);
                    row.createCell(22).setCellValue(triangularInversionManifolds);
                    ResultadoSimulacion.setManifolds(triangularInversionManifolds);
                }

                if (oportunidad.getInversionOportunidad().getRisersMin() != 0) {
                    double AleatorioInversion = random.nextDouble();
                            triangularInversionRisers = triangularDistribution(recurso, oportunidad.getInversionOportunidad().getRisersMin(),
                                    oportunidad.getInversionOportunidad().getRisersMax(),
                                    oportunidad.getInversionOportunidad().getRisersMp(),
                            AleatorioInversion);
                    row.createCell(23).setCellValue(triangularInversionRisers);
                    ResultadoSimulacion.setRisers(triangularInversionRisers);
                }

                if (oportunidad.getInversionOportunidad().getSistemasdecontrolMin() != 0) {
                    double AleatorioInversion = random.nextDouble();
                            triangularInversionSistemasDeControl = triangularDistribution(recurso, oportunidad.getInversionOportunidad().getSistemasdecontrolMin(),
                                    oportunidad.getInversionOportunidad().getSistemasdecontrolMax(),
                                    oportunidad.getInversionOportunidad().getSistemasdecontrolMp(),
                            AleatorioInversion);
                    row.createCell(24).setCellValue(triangularInversionSistemasDeControl);
                    ResultadoSimulacion.setSistemasDeControl(triangularInversionSistemasDeControl);
                }

                if (oportunidad.getInversionOportunidad().getCubiertadeprocesMin() != 0) {
                    double AleatorioInversion = random.nextDouble();
                            triangularInversionCubiertaDeProces = triangularDistribution(recurso, oportunidad.getInversionOportunidad().getCubiertadeprocesMin(),
                                    oportunidad.getInversionOportunidad().getCubiertadeprocesMax(),
                                    oportunidad.getInversionOportunidad().getCubiertadeprocesMp(),
                            AleatorioInversion);
                    row.createCell(25).setCellValue(triangularInversionCubiertaDeProces);
                    ResultadoSimulacion.setCubiertaDeProces(triangularInversionCubiertaDeProces);
                }

                if (oportunidad.getInversionOportunidad().getBuquetanquecompraMin() != 0) {
                    double AleatorioInversion = random.nextDouble();
                            triangularInversionBuqueTanqueCompra = triangularDistribution(recurso, oportunidad.getInversionOportunidad().getBuquetanquecompraMin(),
                                    oportunidad.getInversionOportunidad().getBuquetanquecompraMax(),
                                    oportunidad.getInversionOportunidad().getBuquetanquecompraMp(),
                            AleatorioInversion);
                    row.createCell(26).setCellValue(triangularInversionBuqueTanqueCompra);
                    ResultadoSimulacion.setBuqueTanqueCompra(triangularInversionBuqueTanqueCompra);
                }

                if (oportunidad.getInversionOportunidad().getBuquetanquerentaMin() != 0) {
                    double AleatorioInversion = random.nextDouble();
                            triangularInversionBuqueTanqueRenta = triangularDistribution(recurso, oportunidad.getInversionOportunidad().getBuquetanquerentaMin(),
                                    oportunidad.getInversionOportunidad().getBuquetanquerentaMax(),
                                    oportunidad.getInversionOportunidad().getBuquetanquerentaMp(),
                            AleatorioInversion);
                    row.createCell(27).setCellValue(triangularInversionBuqueTanqueRenta);
                    ResultadoSimulacion.setBuqueTanqueRenta(triangularInversionBuqueTanqueRenta);
                }

                long endInversion = System.nanoTime();
                System.out.println("Tiempo para cálculos de Inversion: " + (endInversion - startInversion) / 1_000_000 + " ms");

                long startProduction = System.nanoTime();
                // Llamada a productionQuery y almacenamiento del resultado
                Map<Integer, Double> produccionAnualMap = databaseConnection.executeProductionQuery(42, 2643, gastoTriangular, declinacion, recurso, area );
                for (Map.Entry<Integer, Double> entry : produccionAnualMap.entrySet()) {
                    int anio = entry.getKey();
                    double ctoAnual = entry.getValue();
                    int columnIndex = EncuentraOCreaColumna(headerRow, anio);
                    row.createCell(columnIndex).setCellValue(ctoAnual);

                    CtoAnualResultado CtoAnualRes = new CtoAnualResultado(anio, ctoAnual);
                    ResultadoSimulacion.getCtoAnualList().add(CtoAnualRes);
                }

                long endProduction = System.nanoTime();
                System.out.println("Tiempo para cálculos de ProductionQuery: " + (endProduction - startProduction) / 1_000_000 + " ms");
                RestTemplate restTemplate = new RestTemplate();

                long startEvaluacionEconomica = System.nanoTime();

                SimulacionMicros simulacionMicros = new SimulacionMicros(idOportunidadObjetivo,oportunidad.getActualIdVersion(),gastoTriangular, declinacion, recurso, area,
                        triangularInversionPlataforma, triangularInversionLineaDescarga, triangularInversionEstacionCompresion, triangularInversionDucto,
                        triangularInversionBat,
                        triangularExploratorioMin, triangularExploratorioPer, triangularExploratorioTer,
                        triangularDESInfra, triangularDESPer, triangularDESTer, triangularInversionArbolesSubmarinos, triangularInversionManifolds, triangularInversionRisers,
                        triangularInversionSistemasDeControl, triangularInversionCubiertaDeProces,triangularInversionBuqueTanqueCompra, triangularInversionBuqueTanqueRenta, restTemplate);


                Object resultado = simulacionMicros.ejecutarSimulacion();

                long endEvaluacionEconomica = System.nanoTime();

                System.out.println("Tiempo para cálculos de EvaluacionEconomica: " + (endEvaluacionEconomica - startEvaluacionEconomica) / 1_000_000 + " ms");
                resultados.add(resultado);
            } else {
                fracasos++;

                row.createCell(1).setCellValue("Fracaso");
                for (int j = 2; j < headers.length; j++) {
                    row.createCell(j).setCellValue(0);

                }

                double aleatorioExploratorioInfra = random.nextDouble();
                double triangularExploratorioMin = triangularDistributionSinPCE(oportunidad.getInfraestructuraMin(),
                        oportunidad.getInfraestructuraMax(),
                        oportunidad.getInfraestructuraMP(),
                        aleatorioExploratorioInfra);
                row.createCell(10).setCellValue(triangularExploratorioMin);
                ResultadoSimulacion.setExploratoriaInfra(triangularExploratorioMin);

                double aleatorioExploratorioPerforacion = random.nextDouble();
                double triangularExploratorioPer = triangularDistributionSinPCE(oportunidad.getPerforacionMin(),
                        oportunidad.getPerforacionMax(),
                        oportunidad.getPerforacionMP(),
                        aleatorioExploratorioPerforacion);
                row.createCell(11).setCellValue(triangularExploratorioPer);
                ResultadoSimulacion.setExploratoriaPerf(triangularExploratorioPer);


                double aleatorioExploratorioTerminacion = random.nextDouble();
                double triangularExploratorioTer = triangularDistributionSinPCE(oportunidad.getTerminacionMin(),
                        oportunidad.getTerminacionMax(),
                        oportunidad.getTerminacionMP(),
                        aleatorioExploratorioTerminacion);
                row.createCell(12).setCellValue(triangularExploratorioTer);
                ResultadoSimulacion.setExploratoriaTer(triangularExploratorioTer);

                    RestTemplate restTemplate = new RestTemplate();

                    long startEvaluacionEconomica = System.nanoTime();
                    SimulacionMicros simulacionMicros = new SimulacionMicros(idOportunidadObjetivo,oportunidad.getActualIdVersion(),0, 0,0 , 0,
                            0, 0, 0, 0,
                            0,  triangularExploratorioMin, triangularExploratorioPer,  triangularExploratorioTer,
                            0, 0, 0, 0,
                            0, 0, 0,
                            0,0,
                            0, restTemplate);
                    Object resultado = simulacionMicros.ejecutarSimulacion();

                long endEvaluacionEconomica = System.nanoTime();
                System.out.println("Tiempo para cálculos de EvaluacionEconomica FRACASO: " + (endEvaluacionEconomica - startEvaluacionEconomica) / 1_000_000 + " ms");

                resultados.add(resultado);
            }
        }


        System.out.println("Éxitos: " + exitos);
        System.out.println("Fracasos: " + fracasos);

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            objectMapper.writeValue(new File("resultados.json"), resultados);
            System.out.println("Resultados guardados en resultados.json");
        } catch (IOException e) {
            e.printStackTrace();
        }


        // Guardar resultados en el archivo Excel
        try (FileOutputStream fileOut = new FileOutputStream("SimulacionMonteCarlo_" + idOportunidadObjetivo + ".xlsx")) {
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

        System.out.println("Resultados guardados en SimulacionMonteCarlo.xlsx");


        return ResponseEntity.ok(resultados);

    }

    private boolean pruebaGeologica() {
        return random.nextDouble() <= oportunidad.getPg();
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





        if (oportunidad.getIdHidrocarburo() == 1 || oportunidad.getIdHidrocarburo() == 2 || oportunidad.getIdHidrocarburo() == 4 || oportunidad.getIdHidrocarburo() == 6 ) {
            
            gastoInicialMin = oportunidad.getGastoMINAceite() / oportunidad.getFcAceite();
            gastoInicialMP =  oportunidad.getGastoMPAceite() / oportunidad.getFcAceite();
            gastoInicialMax = oportunidad.getGastoMAXAceite() / oportunidad.getFcAceite();

        } else if (oportunidad.getIdHidrocarburo() == 3 || oportunidad.getIdHidrocarburo() == 5 || oportunidad.getIdHidrocarburo() == 7 || oportunidad.getIdHidrocarburo() == 9) {

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
            System.out.println("La fila del encabezado no existe.");
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



}
