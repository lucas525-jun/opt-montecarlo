package org.example.Services;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.Models.EvaluacionEconomica.EvaluacionEconomica;
import org.example.Models.OportunidadExcel;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ExcelService {

    // Método para generar el archivo Excel
    public Workbook generateExcel(List<OportunidadExcel> oportunidades) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Datos");

        // Crear encabezados
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Proyecto");
        header.createCell(1).setCellValue("Año");
        header.createCell(2).setCellValue("Ingresos");
        header.createCell(3).setCellValue("Inversiones");
        header.createCell(4).setCellValue("Total");

        int rowNum = 1;

        // Iterar sobre cada oportunidad y sus evaluaciones económicas
        for (OportunidadExcel oportunidad : oportunidades) {
            for (EvaluacionEconomica evaluacion : oportunidad.getEvaluacionEconomica()) {
                Row row = sheet.createRow(rowNum++);

                // Escribir el nombre del proyecto
                row.createCell(0).setCellValue(oportunidad.getProyecto());

                // Escribir el año de la evaluación
                int year = evaluacion.getAnio();
                int yearColumnIndex = EncuentraOCreaColumna(header, year);

                // Escribir los datos de los ingresos, inversiones y total en la columna correspondiente
                row.createCell(yearColumnIndex).setCellValue(evaluacion.getIngresos() != null ? evaluacion.getIngresos().getTotal() : 0.0);
                row.createCell(yearColumnIndex + 1).setCellValue(evaluacion.getInversiones() != null ? evaluacion.getInversiones().getTotal() : 0.0);
                double total = evaluacion.getIngresos() != null ? evaluacion.getIngresos().getTotal() : 0.0;
                total -= (evaluacion.getInversiones() != null ? evaluacion.getInversiones().getTotal() : 0.0);
                row.createCell(yearColumnIndex + 2).setCellValue(total);
            }
        }

        // Guardar el archivo Excel
        try (FileOutputStream fileOut = new FileOutputStream("Datos.xlsx")) {
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

        return workbook;
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
}
