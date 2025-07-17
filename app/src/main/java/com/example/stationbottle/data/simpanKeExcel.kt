import android.content.Context
import com.example.stationbottle.data.EvaluationMetrics
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.*

fun simpanKeExcel(
    name: String,
    context: Context,
    depth: Int,
    gamma: Double,
    lambda: Double,
    learningRate: Double,
    user: Int,
    metrics: EvaluationMetrics,
    fitur: String,
    jenis: String
) {
    val file = File(context.getExternalFilesDir(null), "$name.xlsx")
    val workbook = if (file.exists()) {
        FileInputStream(file).use { fis -> WorkbookFactory.create(fis) as XSSFWorkbook }
    } else {
        XSSFWorkbook()
    }

    val sheet = workbook.getSheet(name) ?: workbook.createSheet(name)

    if (sheet.physicalNumberOfRows == 0) {
        val header = sheet.createRow(0)
        val headers = listOf("Depth", "Gamma", "Lambda", "Learning Rate", "User", "MAE", "RMSE", "SMAPE", "R2", "Fitur", "Jenis")
        headers.forEachIndexed { index, title ->
            header.createCell(index).setCellValue(title)
        }
    }

    val rowNum = sheet.lastRowNum + 1
    val row = sheet.createRow(rowNum)

    val depthValue: Any = if (depth == 0) "∞" else depth.toDouble()

    val data = listOf(
        depthValue,
        gamma,
        lambda,
        learningRate,
        user.toDouble(),
        metrics.mae,
        metrics.rmse,
        metrics.smape,
        metrics.r2,
        fitur,
        jenis
    )

    data.forEachIndexed { index, value ->
        when (value) {
            is Double -> row.createCell(index).setCellValue(value)
            is String -> row.createCell(index).setCellValue(value)
            else -> row.createCell(index).setCellValue(value.toString())
        }
    }

    FileOutputStream(file).use { out ->
        workbook.write(out)
    }
    workbook.close()
}

