import android.content.Context
import com.example.stationbottle.data.EvaluationMetrics
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.*

fun simpanKeExcel(
    context: Context,
    depth: Int,
    gamma: Double,
    lambda: Double,
    learningRate: Double,
    user: Int,
    metrics: EvaluationMetrics
) {
    val file = File(context.getExternalFilesDir(null), "evaluasi.xlsx")
    val workbook = if (file.exists()) {
        FileInputStream(file).use { fis -> WorkbookFactory.create(fis) as XSSFWorkbook }
    } else {
        XSSFWorkbook()
    }

    val sheet = workbook.getSheet("Evaluasi") ?: workbook.createSheet("Evaluasi")

    if (sheet.physicalNumberOfRows == 0) {
        val header = sheet.createRow(0)
        val headers = listOf("Depth", "Gamma", "Lambda", "Learning Rate", "User", "MAE", "RMSE", "SMAPE", "R2")
        headers.forEachIndexed { index, title ->
            header.createCell(index).setCellValue(title)
        }
    }

    val rowNum = sheet.lastRowNum + 1
    val row = sheet.createRow(rowNum)
    val data = listOf(
        depth.toDouble(),
        gamma,
        lambda,
        learningRate,
        user.toDouble(),
        metrics.mae,
        metrics.rmse,
        metrics.smape,
        metrics.r2
    )
    data.forEachIndexed { index, value ->
        row.createCell(index).setCellValue(value as Double)
    }

    FileOutputStream(file).use { out ->
        workbook.write(out)
    }
    workbook.close()
}

