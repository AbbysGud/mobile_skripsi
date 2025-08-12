package com.example.stationbottle.ui.screens.component

import android.graphics.Color
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.yml.charts.axis.AxisData
import co.yml.charts.axis.DataCategoryOptions
import co.yml.charts.axis.Gravity
import co.yml.charts.common.model.Point
import co.yml.charts.ui.barchart.BarChart
import co.yml.charts.ui.barchart.models.BarChartData
import co.yml.charts.ui.barchart.models.BarData
import co.yml.charts.ui.barchart.models.BarStyle

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BarchartWithSolidBars(todayList: Map<String, Double>) {
    val barData = getCustomBarChartData(todayList, DataCategoryOptions())
    val maxRange = todayList.values.maxOrNull()?.toInt() ?: 50
    val yStepSize = 10

    val xAxisData = AxisData.Builder()
        .axisLineColor(MaterialTheme.colorScheme.onSurface)
        .axisLabelColor(MaterialTheme.colorScheme.onSurface)
        .backgroundColor(MaterialTheme.colorScheme.surfaceContainer)
        .axisStepSize(30.dp)
        .steps(barData.size - 1)
        .bottomPadding(40.dp)
        .axisLabelAngle(20f)
        .startDrawPadding(48.dp)
        .shouldDrawAxisLineTillEnd(true)
        .labelData { index -> barData[index].label }
        .axisLabelDescription { "Waktu Minum" }
        .axisPosition(Gravity.BOTTOM)
        .build()

    val yAxisData = AxisData.Builder()
        .axisLineColor(MaterialTheme.colorScheme.onSurface)
        .axisLabelColor(MaterialTheme.colorScheme.onSurface)
        .backgroundColor(MaterialTheme.colorScheme.surfaceContainer)
        .steps(yStepSize)
        .labelAndAxisLinePadding(20.dp)
        .axisOffset(20.dp)
        .topPadding(40.dp)
        .labelData { index -> (index * (maxRange / yStepSize)).toString() }
        .axisLabelDescription { "Jumlah Minum (mL)" }
        .axisPosition(Gravity.LEFT)
        .build()

    val barChartData = BarChartData(
        chartData = barData,
        xAxisData = xAxisData,
        yAxisData = yAxisData,
        backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
        barStyle = BarStyle(
            paddingBetweenBars = 20.dp,
            barWidth = 25.dp
        ),
        showYAxis = true,
        showXAxis = true,
        horizontalExtraSpace = 40.dp
    )

    BarChart(
        modifier = Modifier.Companion
            .height(350.dp),
        barChartData = barChartData
    )
}

@Composable
fun getCustomBarChartData(
    drinkData: Map<String, Double>,
    dataCategoryOptions: DataCategoryOptions
): List<BarData> {
    val list = arrayListOf<BarData>()
    drinkData.entries.forEachIndexed { index, entry ->
        val date = entry.key
        val drinkValue = entry.value

        val point = Point(
            x = index.toFloat(),
            y = drinkValue.toFloat()
        )
        list.add(
            BarData(
                point = point,
                color = MaterialTheme.colorScheme.primary,
                dataCategoryOptions = dataCategoryOptions,
                label = date,
            )
        )
    }
    return list
}

@Composable
private fun TextUnit.toPx(): Float {
    val density = LocalDensity.current.density
    return (this.value * density)
}

@Composable
fun MPLineChartForDailyIntake(
    dailyIntakeData: Map<String, Double>,
    colorInput: Int,
    globalMaxYAxisValue: Float,
    isDaily: Boolean = true
) {
    // Convert sp to px for text size
    val valueTextSizePx = 4.sp.toPx()
    val axisLabelTextSizePx = 4.sp.toPx() // Ukuran label axis

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)

                // Konfigurasi X-Axis (Axis Horizontal)
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawAxisLine(true)
                xAxis.setDrawLabels(true)
                xAxis.granularity = 1f
                xAxis.labelRotationAngle = -45f
                xAxis.axisLineColor = Color.DKGRAY
                xAxis.textColor = Color.DKGRAY
                xAxis.axisLineWidth = 1.5f
                xAxis.textSize = axisLabelTextSizePx

                // Mengatur Gridlines X-axis
                xAxis.setDrawGridLines(true)
                xAxis.gridColor = Color.LTGRAY
                xAxis.gridLineWidth = 0.8f

                // Konfigurasi Y-Axis Kiri (Axis Vertikal Kiri)
                axisLeft.setDrawAxisLine(true)
                val yAxisLabelCount = (globalMaxYAxisValue / 500f).toInt() + 1
                axisLeft.setLabelCount(yAxisLabelCount.coerceAtLeast(2), true)
                axisLeft.axisMinimum = 0f
                axisLeft.axisLineColor = Color.DKGRAY
                axisLeft.textColor = Color.DKGRAY
                axisLeft.axisLineWidth = 1.5f
                axisLeft.textSize = axisLabelTextSizePx
                axisLeft.axisMaximum = globalMaxYAxisValue

                // Mengatur Gridlines Y-axis
                axisLeft.setDrawGridLines(true)
                axisLeft.gridColor = Color.LTGRAY
                axisLeft.gridLineWidth = 0.8f

                axisRight.isEnabled = false

                animateX(1000)
            }
        },
        update = { chart ->
            val sortedEntries = dailyIntakeData.entries.sortedBy { it.key }

            val entries = mutableListOf<Entry>()
            val xAxisLabels = mutableListOf<String>()

            sortedEntries.forEachIndexed { index, entry ->
                entries.add(Entry(index.toFloat(), entry.value.toFloat()))
                if (isDaily) {
                    val date = LocalDate.parse(entry.key, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    xAxisLabels.add(date.format(DateTimeFormatter.ofPattern("dd/MM", Locale.getDefault())))
                } else {
                    xAxisLabels.add(entry.key)
                }
            }

            if (entries.isNotEmpty()) {
                val dataSet = LineDataSet(entries, "Jumlah Minum (mL)").apply {
                    color = colorInput
                    valueTextColor = Color.BLACK
                    valueTextSize = valueTextSizePx
                    setDrawValues(true)
                    setDrawCircles(true)
                    setCircleColor(colorInput)
                    circleRadius = 5f
                    circleHoleRadius = 2f

                    lineWidth = 3f
                    setDrawFilled(true)
                    fillColor = colorInput
                    fillAlpha = 120

                    mode = LineDataSet.Mode.CUBIC_BEZIER

                    setDrawHighlightIndicators(true)
                    setHighlightEnabled(true)
                    highLightColor = Color.GRAY
                }

                val lineData = LineData(dataSet)
                chart.data = lineData

                chart.xAxis.valueFormatter = IndexAxisValueFormatter(xAxisLabels)
                chart.xAxis.labelCount = xAxisLabels.size
                chart.setVisibleXRangeMinimum(1f)

                chart.invalidate()
            } else {
                chart.clear()
            }
        }
    )
}