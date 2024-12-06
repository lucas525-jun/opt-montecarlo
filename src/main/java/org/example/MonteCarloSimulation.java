package org.example;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.Models.CtoAnualResultado;
import org.example.Models.InversionOportunidad;
import org.example.Models.Oportunidad;
import org.example.Models.ResultadoSimulacion;
import org.springframework.http.ResponseEntity;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class MonteCarloSimulation {
    DatabaseConnection databaseConnection = new DatabaseConnection();
    Oportunidad oportunidad = databaseConnection.executeQuery(42, 2643); // Atributos y configuración

    private final double Pg = oportunidad.getPg(); // Probabilidad geológica
    private final double P10 = oportunidad.getPceP10();
    private final double P90 = oportunidad.getPceP90();
    private final double P10Area = oportunidad.getArea10();
    private final double P90Area = oportunidad.getArea90();
    private final double factorConversionAceite = oportunidad.getFcAceite();
    private final double factorConversionGas = oportunidad.getFcGas();
    private final double factorCondensado = oportunidad.getFcCondensado();
    private final double GastoInicialAceiteMin = oportunidad.getGastoMINAceite();
    private final double GastoInicialAceiteMP = oportunidad.getGastoMPAceite();
    private final double GastoInicialAceiteMax = oportunidad.getGastoMAXAceite();
    private final double PrimeraDeclinacionMin = oportunidad.getPrimeraDeclinacionMin();
    private final double PrimeraDeclinacionMP = oportunidad.getPrimeraDeclinacionMP();
    private final double PrimeraDeclinacionMax = oportunidad.getPrimeraDeclinacionMAX();
    private final InversionOportunidad inversionOportunidad = oportunidad.getInversionOportunidad();






    private Random random = new Random();

    public ResponseEntity<List<ResultadoSimulacion>> runSimulation() {

        int exitos = 0;
        int fracasos = 0;
        int valores = 0;
        List<ResultadoSimulacion> resultados = new ArrayList<>();

        // Crear libro y hoja en Excel
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Simulación Monte Carlo");

        // Crear encabezado con columnas adicionales para "Años" y "CtoAnuales"
        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "Iteración", "Resultado", "Aleatorio PCE", "PCE",
                "Aleatorio Gasto", "Gasto Inicial (mbpce)",
                "Aleatorio Declinación", "Declinación",
                "Aleatorio Área", "Área", "E.I.", "E.P", "E.T", "D.I", "D.P", "D.T", "Bateria", "Plataforma Desarrollo","Linea Descarga", "E.comprension ","ducto"    // Añadido aquí
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
        for (int i = 0; i < 200; i++) {
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(i + 1); // Iteración número
            valores++;
            System.out.println("Valores: " + valores);
            ResultadoSimulacion ResultadoSimulacion = new ResultadoSimulacion();

            if (pruebaGeologica()) {
                exitos++;
                row.createCell(1).setCellValue("Éxito"); // Marcar éxito

                // Valores aleatorios y cálculos para éxito
                double aleatorioRecurso = random.nextDouble();
                double recurso = calcularRecursoProspectivo(aleatorioRecurso, P10, P90);
                row.createCell(2).setCellValue(aleatorioRecurso);
                row.createCell(3).setCellValue(recurso);
                ResultadoSimulacion.setPce(recurso);

                double aleatorioGasto = random.nextDouble();
                double gastoTriangular = calcularGastoInicial(recurso, aleatorioGasto);
                row.createCell(4).setCellValue(aleatorioGasto);
                row.createCell(5).setCellValue(gastoTriangular);
                ResultadoSimulacion.setGastoInicial(gastoTriangular);

                double aleatorioDeclinacion = random.nextDouble();
                double declinacion = triangularDistribution(recurso, oportunidad.getPrimeraDeclinacionMin(),
                        oportunidad.getPrimeraDeclinacionMAX(),
                        oportunidad.getPrimeraDeclinacionMP(),
                        aleatorioDeclinacion);
                row.createCell(6).setCellValue(aleatorioDeclinacion);
                row.createCell(7).setCellValue(declinacion);
                ResultadoSimulacion.setDeclinacion(declinacion);

                double aleatorioArea = random.nextDouble();
                double area = calcularRecursoProspectivo(aleatorioArea, P10Area, P90Area);
                row.createCell(8).setCellValue(aleatorioArea);
                row.createCell(9).setCellValue(area);
                ResultadoSimulacion.setArea(area);

                double aleatorioExploratorioInfra = random.nextDouble();
                double triangularExploratorioMin = triangularDistributionSinPCE(oportunidad.getInfraestructuraMin(),
                        oportunidad.getInfraestructuraMax(),
                        oportunidad.getInfraestructuraMP(),
                        aleatorioExploratorioInfra);
                row.createCell(10).setCellValue(triangularExploratorioMin);
                ResultadoSimulacion.setExploratoriaInfra(triangularExploratorioMin);

                double aleatorioExploratorioPerforacion = random.nextDouble();
                double triangularExploratorioPer = triangularDistributionSinPCE( oportunidad.getPerforacionMin(),
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


                if (inversionOportunidad.getBateriaMin() != 0) {
                    double AleatorioInverision = random.nextDouble();
                    double triangularInversionBat = triangularDistribution(recurso, inversionOportunidad.getBateriaMin(),
                            inversionOportunidad.getBateriaMax(),
                            inversionOportunidad.getBateriaMp(),
                            AleatorioInverision);
                    row.createCell(16).setCellValue(triangularInversionBat);
                    ResultadoSimulacion.setBateria(triangularInversionBat);
                }

                if (inversionOportunidad.getPlataformadesarrolloMin() != 0) {
                    double AleatorioInverision = random.nextDouble();
                    double triangularInversionPlataforma = triangularDistribution(recurso, inversionOportunidad.getPlataformadesarrolloMin(),
                            inversionOportunidad.getPlataformadesarrolloMax(),
                            inversionOportunidad.getPlataformadesarrolloMp(),
                            AleatorioInverision);
                    row.createCell(17).setCellValue(triangularInversionPlataforma);
                    ResultadoSimulacion.setPlataformaDesarrollo(triangularInversionPlataforma);
                }

                if (inversionOportunidad.getLineadedescargaMin() != 0) {
                    double AleatorioInverision = random.nextDouble();
                    double triangularInversionLineaDescarga = triangularDistribution(recurso, inversionOportunidad.getLineadedescargaMin(),
                            inversionOportunidad.getLineadedescargaMax(),
                            inversionOportunidad.getLineadedescargaMp(),
                            AleatorioInverision);
                    row.createCell(18).setCellValue(triangularInversionLineaDescarga);
                    ResultadoSimulacion.setLineaDescarga(triangularInversionLineaDescarga);
                }

                if (inversionOportunidad.getEstacioncompresionMin() != 0) {
                    double AleatorioInverision = random.nextDouble();
                    double triangularInversionEstacionCompresion = triangularDistribution(recurso, inversionOportunidad.getEstacioncompresionMin(),
                            inversionOportunidad.getEstacioncompresionMax(),
                            inversionOportunidad.getEstacioncompresionMp(),
                            AleatorioInverision);
                    row.createCell(19).setCellValue(triangularInversionEstacionCompresion);
                    ResultadoSimulacion.setEComprension(triangularInversionEstacionCompresion);
                }

                if (inversionOportunidad.getDuctoMin() != 0) {
                    double AleatorioInverision = random.nextDouble();
                    double triangularInversionDucto = triangularDistribution(recurso, inversionOportunidad.getDuctoMin(),
                            inversionOportunidad.getDuctoMax(),
                            inversionOportunidad.getDuctoMp(),
                            AleatorioInverision);
                    row.createCell(20).setCellValue(triangularInversionDucto);
                    ResultadoSimulacion.setDucto(triangularInversionDucto);
                }



                // Llamada a productionQuery y almacenamiento del resultado
                Map<Integer, Double> produccionAnualMap = databaseConnection.executeProductionQuery(42, 2643, gastoTriangular, declinacion, recurso, area);
                for (Map.Entry<Integer, Double> entry : produccionAnualMap.entrySet()) {
                    int anio = entry.getKey();
                    double ctoAnual = entry.getValue();
                    int columnIndex = EncuentraOCreaColumna(headerRow, anio);
                    row.createCell(columnIndex).setCellValue(ctoAnual);

                    CtoAnualResultado CtoAnualRes = new CtoAnualResultado(anio,ctoAnual);
                    ResultadoSimulacion.getCtoAnualList().add(CtoAnualRes);



                }
                resultados.add(ResultadoSimulacion);

            } else {
                fracasos++;
                row.createCell(1).setCellValue("Fracaso");
                for (int j = 2; j < headers.length; j++) {
                    row.createCell(j).setCellValue(0);
                }
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
        try (FileOutputStream fileOut = new FileOutputStream("SimulacionMonteCarlo.xlsx")) {
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
        return random.nextDouble() <= Pg;
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
        double gastoInicialMin = GastoInicialAceiteMin / factorConversionAceite;
        double gastoInicialMP = GastoInicialAceiteMP / factorConversionAceite;
        double gastoInicialMax = GastoInicialAceiteMax / factorConversionAceite;

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
