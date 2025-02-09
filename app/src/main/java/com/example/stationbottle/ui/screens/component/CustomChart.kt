package com.example.stationbottle.ui.screens.component

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
        horizontalExtraSpace = 0.dp
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