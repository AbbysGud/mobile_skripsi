package com.example.stationbottle.data

import android.content.Context
import simpanKeExcel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEachIndexed
import kotlin.collections.set
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.text.get
import kotlin.text.substring

data class DataPoint(
    val tanggal: String,
    val air: Double,
    val waktuDetik: Double,
    val durasi: Double,
    val residual: Double = 0.0,
    val dayOfWeek: Int,
    val sinDayOfWeek: Double,
    val cosDayOfWeek: Double,
    val timeBucket: Int,
    val sinTimeBucket: Double,
    val cosTimeBucket: Double,
//    val ordinalDate: Int,
    val sinTimeDayInteraction: Double,
    val cosTimeDayInteraction: Double,
    val isPuasa: Boolean,
//    val jamCategory: Int,
    val jamFreqZscore: Double,
    val jamFreqZscoreDecay: Double,
    val puasaTimeInteraction: Double,
    val puasaDecayInteraction: Double
)

data class TreeNode(
    val residuals: List<Double>,
    val similarity: Double,
    val isLeaf: Boolean = false,
    val feature: String? = null,
    val thresholdNumeric: Double? = null,
    val gain: Double? = null,
    val left: TreeNode? = null,
    val right: TreeNode? = null
)

data class EvaluationMetrics(
    val smape: Double,
    val mae: Double,
    val rmse: Double,
    val r2: Double
)

class XGBoost(
    private val maxDepth: Int,
    private val gamma: Double,
    private val lambda: Double,
    private val learningRate: Double,
    private val idUser: Int
) {
    private var basePredictionAir = 0.0
    private var basePredictionWaktu = 0.0
    private val treesAir = mutableListOf<TreeNode>()
    private val treesWaktu = mutableListOf<TreeNode>()
    private val decimalFormat = DecimalFormat("#.##", DecimalFormatSymbols(Locale.US))
    private var globalMinumPerJam = mutableMapOf<Int, MutableList<Double>>()
    private val RAMADAN_START = LocalDate.of(2025, 2, 28)
    private val RAMADAN_END = LocalDate.of(2025, 3, 31)
    private lateinit var analysis: RawDataAnalysis

    // Data class untuk menyimpan hasil analisis
    data class RawDataAnalysis(
        val last7DaysStats: Stats,
        val last14DaysStats: Stats,
        val suggestedDecayPeriod: Int,
        val anomalyFlags: List<Boolean>,
        val puasaEffect: Double
    )

    data class Stats(
        val meanAir: Double,
        val medianAir: Double,
        val stdAir: Double,
        val meanWaktu: Double,
        val medianWaktu: Double,
        val stdWaktu: Double,
        val totalAir: Double,
        val totalWaktu: Double
    )

    fun analyzeRawData(
        tanggal: Array<String>,
        jumlahAir: DoubleArray,
        jumlahWaktu: DoubleArray
    ): RawDataAnalysis {

        val dailyData = mutableMapOf<LocalDate, Pair<MutableList<Double>, MutableList<Double>>>()

        tanggal.forEachIndexed { index, tgl ->
            val date = LocalDate.parse(tgl.substring(0, 10))
            val air = jumlahAir[index]
            val waktu = jumlahWaktu[index]

            dailyData.getOrPut(date) { mutableListOf<Double>() to mutableListOf() }.apply {
                first.add(air)
                second.add(waktu)
            }
        }

        val sortedDates = dailyData.keys.sorted()

        val last7Days = sortedDates.takeLast(7)
        val (air7, waktu7) = extractStats(last7Days, dailyData)

        val last14Days = sortedDates.takeLast(14)
        val (air14, waktu14) = extractStats(last14Days, dailyData)

        val anomalies = detectAnomalies(jumlahAir, jumlahWaktu)

        val puasaEffect = calculatePuasaEffect(tanggal, jumlahAir, jumlahWaktu)

        val trendSlope7 = calculateTrendSlope(
            last7Days.flatMap { dailyData[it]?.first ?: emptyList() }
        )

        val last7DaysSet = last7Days.toSet()
        val last14DaysSet = last14Days.toSet()

        val anomalyCount7 = anomalies.withIndex().count { (i, _) ->
            LocalDate.parse(tanggal[i].substring(0, 10)) in last7DaysSet
        }

        val anomalyCount14 = anomalies.withIndex().count { (i, _) ->
            LocalDate.parse(tanggal[i].substring(0, 10)) in last14DaysSet
        }

        val decayPeriod = determineDecayPeriod(
            stats7 = air7,
            stats14 = air14,
            anomalyCount7 = anomalyCount7,
            anomalyCount14 = anomalyCount14,
            trendSlope7 = trendSlope7
        )

        return RawDataAnalysis(
            last7DaysStats = Stats(
                air7.mean, air7.median, air7.std,
                waktu7.mean, waktu7.median, waktu7.std,
                air7.total, waktu7.total
            ),
            last14DaysStats = Stats(
                air14.mean, air14.median, air14.std,
                waktu14.mean, waktu14.median, waktu14.std,
                air14.total, waktu14.total
            ),
            suggestedDecayPeriod = decayPeriod,
            anomalyFlags = anomalies,
            puasaEffect = puasaEffect
        )
    }

    private fun calculateTrendSlope(values: List<Double>): Double {
        if (values.size < 2) return 0.0

        val x = values.indices.map { it.toDouble() }
        val y = values
        val n = x.size.toDouble()

        val sumX = x.sum()
        val sumY = y.sum()
        val sumXY = x.zip(y).sumOf { (xi, yi) -> xi * yi }
        val sumX2 = x.sumOf { it.pow(2) }

        val denominator = n * sumX2 - sumX.pow(2)
        return if (denominator == 0.0) 0.0 else (n * sumXY - sumX * sumY) / denominator
    }

    private fun extractStats(
        dates: List<LocalDate>,
        data: Map<LocalDate, Pair<List<Double>, List<Double>>>
    ): Pair<StatsData, StatsData> {
        val airValues = dates.flatMap { data[it]?.first ?: emptyList() }
        val waktuValues = dates.flatMap { data[it]?.second ?: emptyList() }

        return Pair(
            calculateBasicStats(airValues),
            calculateBasicStats(waktuValues)
        )
    }

    private data class StatsData(
        val mean: Double,
        val median: Double,
        val std: Double,
        val total: Double
    )

    private fun calculateBasicStats(values: List<Double>): StatsData {
        if (values.isEmpty()) return StatsData(0.0, 0.0, 0.0, 0.0)

        val sorted = values.sorted()
        val mean = sorted.average()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size/2 - 1] + sorted[sorted.size/2]) / 2
        } else {
            sorted[sorted.size/2]
        }
        val std = sqrt(sorted.sumOf { (it - mean).pow(2) } / sorted.size)
        val total = sorted.sum()

        return StatsData(mean, median, std, total)
    }

    private fun determineDecayPeriod(
        stats7: StatsData,
        stats14: StatsData,
        anomalyCount7: Int,
        anomalyCount14: Int,
        trendSlope7: Double
    ): Int {
        return if (anomalyCount7 < anomalyCount14 / 2) 7 else 14
//        // 1. Tren Kuat: Perubahan >5% dari rata-rata 7 hari
//        val isStrongTrend = abs(trendSlope7) > (0.05 * stats7.mean)
//
//        // 2. Rasio Anomali: Perbandingan kepadatan anomali 7 vs 14 hari
//        val anomalyRatio = anomalyCount7.toDouble() / (anomalyCount14 + 1)
//
//        println("7: $anomalyCount7 | 14: $anomalyCount14")
//
//        // 3. Variabilitas: Std deviasi & median tidak konsisten
//        val isVolatile = (stats7.std > 1.2*stats14.std) &&
//                (abs(stats7.median - stats14.median) > 0.1*stats14.median)
//
//        // Hierarki keputusan
//        return when {
//            // Prioritas 1: Tren kuat + perubahan mean >15%
//            isStrongTrend && abs(stats7.mean - stats14.mean)/stats14.mean > 0.15 -> 7
//
//            // Prioritas 2: Anomali terkonsentrasi di 7 hari
//            anomalyRatio > 2.0 -> 14
//
//            // Prioritas 3: Variabilitas tinggi
//            isVolatile -> 7
//
//            // Default: Bandingkan total konsumsi
//            else -> if (stats7.total > stats14.total * 0.6) 7 else 14
//        }
    }

    private fun detectAnomalies(
        air: DoubleArray,
        waktu: DoubleArray
    ): List<Boolean> {

        fun robustZScore(values: DoubleArray): DoubleArray {
            val median = values.sorted().let {
                if (it.isEmpty()) 0.0
                else if (it.size % 2 == 0) (it[it.size/2 - 1] + it[it.size/2])/2
                else it[it.size/2]
            }

            val mad = values.map { abs(it - median) }.sorted().let {
                if (it.isEmpty()) 0.0
                else if (it.size % 2 == 0) (it[it.size/2 - 1] + it[it.size/2])/2
                else it[it.size/2]
            }

            val scale = if (mad == 0.0) 1.0 else 1.4826 * mad
            return DoubleArray(values.size) { i -> (values[i] - median) / scale }
        }

        val zAir = robustZScore(air)
        val zWaktu = robustZScore(waktu)

        return air.indices.map { i ->
            abs(zAir[i]) > 3.5 || abs(zWaktu[i]) > 3.5
        }
    }

    // Hitung efek puasa berdasarkan penurunan konsumsi
    private fun calculatePuasaEffect(
        tanggal: Array<String>,
        jumlahAir: DoubleArray,
        jumlahWaktu: DoubleArray
    ): Double {
        val (puasa, nonPuasa) = tanggal.indices.partition { i ->
            jumlahWaktu[i] >= 12 * 3600
        }

        if (nonPuasa.isEmpty() || puasa.isEmpty()) return 0.0

        val avgNonPuasa = nonPuasa.map { jumlahAir[it] }.average()
        val avgPuasa = puasa.map { jumlahAir[it] }.average()

        return if (avgNonPuasa == 0.0) 0.0 else (avgNonPuasa - avgPuasa) / avgNonPuasa
    }

    fun Collection<Double>.std(): Double {
        val mean = this.average()
        return sqrt(this.map { (it - mean).pow(2) }.average())
    }

    fun latihModel(
        tanggal: Array<String>,
        waktu: Array<String>,
        jumlahAir: DoubleArray,
        minumPerJam: MutableMap<Int, MutableList<Double>>,
        maxIterasi: Int
    ) {

        val waktuDetik = waktu.map {
            LocalTime.parse(it).toSecondOfDay().toDouble()
        }
        val jumlahWaktu = waktuDetik.mapIndexed { i, w ->
            if (i == 0 || w - waktuDetik[i - 1] < 0) {
                0.0
            } else {
                w - waktuDetik[i - 1]
            }
        }

//        analysis = analyzeRawData(
//            tanggal = tanggal,
//            jumlahAir = jumlahAir,
//            jumlahWaktu = jumlahWaktu.toDoubleArray()
//        )

//        println("""
//            Analisis Data Mentah:
//            - Periode Dekay Disarankan: ${analysis.suggestedDecayPeriod} hari
//            - Rata-rata 7 Hari Terakhir:
//                Air: ${analysis.last7DaysStats.meanAir} ml | ${analysis.last7DaysStats.stdAir}
//                Waktu: ${analysis.last7DaysStats.meanWaktu} detik | ${analysis.last7DaysStats.stdWaktu}
//            - Rata-rata 14 Hari Terakhir:
//                Air: ${analysis.last14DaysStats.meanAir} ml | ${analysis.last14DaysStats.stdAir}
//                Waktu: ${analysis.last14DaysStats.meanWaktu} detik | ${analysis.last14DaysStats.stdWaktu}
//            - Total Air 14 Hari: ${analysis.last14DaysStats.totalAir} ml
//            - Efek Puasa: ${analysis.puasaEffect * 100}% penurunan
//            - Anomali Terdeteksi: ${analysis.anomalyFlags.count { it }} titik data
//        """)

        globalMinumPerJam = minumPerJam

        val entryCounts = minumPerJam.mapValues { it.value.size }
//
//        val countsSorted = entryCounts.values.sorted()
//        val n = countsSorted.size
//
//        val idxQ70 = ceil(0.70 * n).toInt().coerceAtMost(n - 1) - 1
//        val idxQ90 = ceil(0.90 * n).toInt().coerceAtMost(n - 1) - 1
//
//        val q70 = countsSorted.getOrElse(idxQ70) { countsSorted.last() }
//        val q90 = countsSorted.getOrElse(idxQ90) { countsSorted.last() }
//
//        val jamCategories = entryCounts.mapValues { (_, count) ->
//            when {
//                count >= q90       -> 2
//                count >= q70       -> 1
//                else               -> 0
//            }
//        }
//
//        val totalEntries = entryCounts.values.sum().toDouble()
        val meanFreq = entryCounts.values.average()
        val stdFreq = entryCounts.values.map { (it - meanFreq).pow(2) }.average().let { sqrt(it) }

        val jamFreqZscore = entryCounts.mapValues { (it.value - meanFreq) / (stdFreq + 1e-6) }

//        val weights = analysis.suggestedDecayPeriod
//        val divider = if(weights == 7) 1 else 2
        val weights = 7
        val divider = 1
        val decayWeights = List(weights) { exp(-it.toDouble() / divider) }

        val jamFreqWithDecay = mutableMapOf<Int, Double>().apply {
            tanggal.forEachIndexed { i, tgl ->
                val hariKe = ChronoUnit.DAYS.between(
                    LocalDate.parse(tgl.substring(0, 10)),
                    LocalDate.now()
                ).toInt()

                val weight = decayWeights.getOrElse(hariKe.coerceIn(0, weights - 1)) { 0.0 }
                val jam = LocalTime.parse(waktu[i]).hour
                this[jam] = this.getOrDefault(jam, 0.0) + weight
            }
        }

//        val sorted = jamFreqWithDecay.values.sorted()
//        val trimmed = sorted.drop(5).take(sorted.size - 10)
//        val meanDecay = trimmed.average()
//        val stdDecay = trimmed.std()

        val meanDecay = jamFreqWithDecay.values.average()
        val stdDecay = jamFreqWithDecay.values.run {
            if (isEmpty()) 1.0 else std()
        }
        val jamFreqZscoreDecay = jamFreqWithDecay.mapValues {
            (it.value - meanDecay) / (stdDecay + 1e-6)
        }

        val dataPoints = tanggal.indices.map {
            val localDate = LocalDate.parse(tanggal[it].substring(0, 10))
            val hour = LocalTime.ofSecondOfDay(waktuDetik[it].toLong()).hour
            val dayOfWeek = localDate.dayOfWeek.value

            val timeBucket = when (hour) {
                in 0..4 -> 0
                in 5..8 -> 1
                in 9..11 -> 2
                in 12..15 -> 3
                in 16..19 -> 4
                in 20..23 -> 5
                else -> -1
            }

            val timeDayInteraction = timeBucket * dayOfWeek
            val sinTimeDayInteraction = sin(2 * Math.PI * timeDayInteraction / (6*7))
            val cosTimeDayInteraction = cos(2 * Math.PI * timeDayInteraction / (6*7))

            val isPuasa: Boolean = jumlahWaktu[it] > 12 * 3600 && jumlahWaktu[it] != 0.0

//            val isPuasa = (
//                    !localDate.isBefore(RAMADAN_START) && !localDate.isAfter(RAMADAN_END)
//                ) || (
//                    jumlahWaktu[it] > 12 * 3600 && jumlahWaktu[it] != 0.0
//                )

            val puasaTimeInteraction: Double = if (isPuasa) {
                when (timeBucket) {
                    0 -> 0.7  // Sahur
                    4 -> 1.2  // Buka puasa
                    5 -> 0.9  // Tarawih
                    else -> 0.3
                }
            } else {
                0.0
            }

            val puasaDecayInteraction: Double = jamFreqZscoreDecay[hour]?.times((if (isPuasa) 1.5 else 0.8)) ?: 0.0

//            val jamCategory: Int = jamCategories[hour] ?: -1

            val freqZscore: Double = jamFreqZscore[hour] ?: 0.0

            val freqZscoreDecay = jamFreqZscoreDecay[hour] ?: 0.0

            DataPoint(
                tanggal = tanggal[it],
                air = jumlahAir[it],
                waktuDetik = waktuDetik[it],
                durasi = jumlahWaktu[it],
                dayOfWeek = dayOfWeek,
                sinDayOfWeek = sin(2 * Math.PI * dayOfWeek / 7),
                cosDayOfWeek = cos(2 * Math.PI * dayOfWeek / 7),
                timeBucket = timeBucket,
                sinTimeBucket = sin(2 * Math.PI * timeBucket / 6),
                cosTimeBucket = cos(2 * Math.PI * timeBucket / 6),
//                ordinalDate = localDate.toEpochDay().toInt(),
                sinTimeDayInteraction = sinTimeDayInteraction,
                cosTimeDayInteraction = cosTimeDayInteraction,
                isPuasa = isPuasa,
//                jamCategory = jamCategory,
                jamFreqZscore = freqZscore,
                jamFreqZscoreDecay = freqZscoreDecay,
                puasaTimeInteraction = puasaTimeInteraction,
                puasaDecayInteraction = puasaDecayInteraction
            )
        }

        val actualAir = jumlahAir.toList()
        val actualWaktu = jumlahWaktu

        basePredictionAir = jumlahAir.average()
        basePredictionWaktu = jumlahWaktu.average()

        var currentPredAir = DoubleArray(jumlahAir.size) { basePredictionAir }
        var currentPredWaktu = DoubleArray(jumlahWaktu.size) { basePredictionWaktu }

        val epsilon = 1e-8
        repeat(maxIterasi) { iter ->

            val residualsAir = jumlahAir.mapIndexed { i, actual ->
                actual - (basePredictionAir + treesAir.sumOf { predictTree(it, dataPoints[i]) })
            }
            val residualsWaktu = jumlahWaktu.mapIndexed { i, actual ->
                actual - (basePredictionWaktu + treesWaktu.sumOf { predictTree(it, dataPoints[i]) })
            }

            val dataAir = dataPoints.zip(residualsAir).map { (dp, r) ->
                dp.copy(residual = r)
            }
            val dataWaktu = dataPoints.zip(residualsWaktu).map { (dp, r) ->
                dp.copy(residual = r)
            }

            val treeAir = buildTree(dataAir)
            val treeWaktu = buildTree(dataWaktu)

            treesAir.add(treeAir)
            treesWaktu.add(treeWaktu)

            dataPoints.forEachIndexed { i, dp ->
                currentPredAir[i] += learningRate * predictTree(treeAir, dp)
                currentPredWaktu[i] += learningRate * predictTree(treeWaktu, dp)
            }

//            if (iter % 10 == 0 || iter == maxIterasi - 1) {
//                val predictedAir = dataPoints.map { dp ->
//                    basePredictionAir + treesAir.sumOf { tree ->
//                        learningRate * predictTree(tree, dp)
//                    }
//                }
//
//                val predictedWaktu = dataPoints.map { dp ->
//                    basePredictionWaktu + treesWaktu.sumOf { tree ->
//                        learningRate * predictTree(tree, dp)
//                    }
//                }
//
//                val airEval = calculateMetrics(predictedAir, actualAir)
//                val waktuEval = calculateMetrics(predictedWaktu, actualWaktu)

//                println("Iterasi $iter - Training Metrics | Air: R²=${airEval.r2} | Waktu: R²=${waktuEval.r2}")
//            }

            val deltaAir = delta(residualsAir, dataPoints.map { dp -> predictTree(treeAir, dp) })
            val deltaWaktu = delta(residualsWaktu, dataPoints.map { dp -> predictTree(treeWaktu, dp) })

            if (deltaAir < epsilon && deltaWaktu < epsilon) {
                println("Konvergen pada iterasi $iter")
                return
            }
        }
    }

    private fun calculateMetrics(actual: List<Double>, predicted: List<Double>): EvaluationMetrics {
        require(actual.size == predicted.size) { "Actual dan predicted harus memiliki ukuran yang sama" }

        val mae = actual.zip(predicted).sumOf { abs(it.first - it.second) } / actual.size
        val rmse = sqrt(actual.zip(predicted).sumOf { (a, p) -> (a - p).pow(2) } / actual.size)
        val smape = actual.zip(predicted).sumOf { (a, p) -> if ((abs(a) + abs(p)) != 0.0) 2 * abs(a - p) / (abs(a) + abs(p)) else 0.0 } / actual.size

        val mean = actual.average()
        val ssTotal = actual.sumOf { (it - mean).pow(2) }
        val ssRes = actual.zip(predicted).sumOf { (a, p) -> (a - p).pow(2) }
        val r2 = if (ssTotal != 0.0) 1 - (ssRes / ssTotal) else 0.0

        return EvaluationMetrics(mae = mae, rmse = rmse, smape = smape, r2 = r2)
    }

    private fun buildTree(data: List<DataPoint>, depth: Int = 0): TreeNode {
        val simRoot = similarity(data.map { it.residual })
        if (depth >= maxDepth && maxDepth != 0) return TreeNode(data.map { it.residual }, simRoot, isLeaf = true)

        val best = cariSplitTerbaik(data, simRoot)
        if (best == null || best.gain - gamma < 0) return TreeNode(data.map { it.residual }, simRoot, isLeaf = true)

        val left = data.filter { getFitur(it, best.feature) < best.threshold }
        val right = data.filter { getFitur(it, best.feature) >= best.threshold }

        if (left.isEmpty() || right.isEmpty()) return TreeNode(data.map { it.residual }, simRoot, isLeaf = true)

        return TreeNode(
            residuals = data.map { it.residual },
            similarity = simRoot,
            isLeaf = false,
            feature = best.feature,
            thresholdNumeric = best.threshold,
            gain = best.gain,
            left = buildTree(left, depth + 1),
            right = buildTree(right, depth + 1)
        )
    }

    private data class SplitResult(
        val feature: String,
        val threshold: Double,
        val gain: Double
    )

    private fun cariSplitTerbaik(
        data: List<DataPoint>,
        simRoot: Double
    ): SplitResult? {
        val features = listOf(
//            "ordinalDate",
            "sinDayOfWeek", "cosDayOfWeek",
//            "sinTimeBucket", "cosTimeBucket",
//            "sinTimeDayInteraction", "cosTimeDayInteraction",
//            "isPuasa",
//            "jamCategory",
//            "jamFreqZscore",
            "jamFreqZscoreDecay",
//            "puasaTimeInteraction",
//            "puasaDecayInteraction"
        )
        var best: SplitResult? = null

        for (feature in features) {
            val sorted = data.sortedBy { getFitur(it, feature) }
            for (i in 1 until sorted.size) {
                val threshold = (getFitur(sorted[i - 1], feature) + getFitur(sorted[i], feature)) / 2
                val left = sorted.filter { getFitur(it, feature) < threshold }.map { it.residual }
                val right = sorted.filter { getFitur(it, feature) >= threshold }.map { it.residual }

                if (left.isEmpty() || right.isEmpty()) continue

                val gain = similarity(left) + similarity(right) - simRoot
                if (best == null || gain > best.gain) {
                    best = SplitResult(feature, threshold, gain)
                }
            }
        }

        return best
    }

    private fun similarity(residuals: List<Double>): Double {
        val sum = residuals.sum()
        return (sum * sum) / (residuals.size + lambda)
    }

    private fun getFitur(
        dp: DataPoint,
        feature: String
    ): Double {
        return when (feature) {
//            "ordinalDate" -> dp.ordinalDate.toDouble()
            "sinTimeBucket" -> dp.sinTimeBucket
            "cosTimeBucket" -> dp.cosTimeBucket
            "sinDayOfWeek" -> dp.sinDayOfWeek
            "cosDayOfWeek" -> dp.cosDayOfWeek
            "sinTimeDayInteraction" -> dp.sinTimeDayInteraction
            "cosTimeDayInteraction" -> dp.cosTimeDayInteraction
            "isPuasa" -> if (dp.isPuasa) 1.0 else 0.0
//            "jamCategory" -> dp.jamCategory.toDouble()
            "jamFreqZscore" -> dp.jamFreqZscore
            "jamFreqZscoreDecay" -> dp.jamFreqZscoreDecay
            "puasaTimeInteraction" -> dp.puasaTimeInteraction
            "puasaDecayInteraction" -> dp.puasaDecayInteraction
            else -> error("Fitur tidak dikenali: $feature")
        }
    }

    private fun predictTree(tree: TreeNode, dp: DataPoint): Double {
        return if (tree.isLeaf) {
            tree.residuals.sum() / (tree.residuals.size + lambda)
        } else {
            val value = getFitur(dp, tree.feature!!)
            if (value < tree.thresholdNumeric!!) {
                predictTree(tree.left!!, dp)
            } else {
                predictTree(tree.right!!, dp)
            }
        }
    }

    fun prediksi(
        waktuTerakhir: String,
        batasWaktu: String,
        tanggal: String,
        bedaHari: Boolean
    ): Pair<DoubleArray, Array<Double>>? {
        if (treesAir.isEmpty() || treesWaktu.isEmpty()) {
            return null
        }

        val start = LocalTime.parse(waktuTerakhir).toSecondOfDay().toDouble()
        var end = LocalTime.parse(batasWaktu).toSecondOfDay().toDouble()
        if (bedaHari) end += 86400

        val localDate = LocalDate.parse(tanggal.substring(0,10))
        val hour = LocalTime.ofSecondOfDay(start.toLong()).hour
        val dayOfWeek = localDate.dayOfWeek.value

        val timeBucket = when (hour) {
            in 0..4 -> 0
            in 5..8 -> 1
            in 9..11 -> 2
            in 12..15 -> 3
            in 16..19 -> 4
            in 20..23 -> 5
            else -> -1
        }

        val timeDayInteraction = timeBucket * dayOfWeek
        val sinTimeDayInteraction = sin(2 * Math.PI * timeDayInteraction / (6*7))
        val cosTimeDayInteraction = cos(2 * Math.PI * timeDayInteraction / (6*7))

        val entryCounts = globalMinumPerJam.mapValues { it.value.size }
//
//        val countsSorted = entryCounts.values.sorted()
//        val n = countsSorted.size
//
//        val idxQ70 = ceil(0.70 * n).toInt().coerceAtMost(n - 1) - 1
//        val idxQ90 = ceil(0.90 * n).toInt().coerceAtMost(n - 1) - 1
//
//        val q70 = countsSorted.getOrElse(idxQ70) { countsSorted.last() }
//        val q90 = countsSorted.getOrElse(idxQ90) { countsSorted.last() }
//
//        val jamCategories = entryCounts.mapValues { (_, count) ->
//            when {
//                count >= q90       -> 2
//                count >= q70       -> 1
//                else               -> 0
//            }
//        }
//
//        val jamCategory: Int = jamCategories[hour] ?: -1
//
//        val totalEntries = entryCounts.values.sum().toDouble()
        val meanFreq = entryCounts.values.average()
        val stdFreq = entryCounts.values.map { (it - meanFreq).pow(2) }.average().let { sqrt(it) }

        val jamFreqZscore = entryCounts.mapValues { (it.value - meanFreq) / (stdFreq + 1e-6) }

        val freqZscore = jamFreqZscore[hour] ?: 0.0

//        val weights = analysis.suggestedDecayPeriod
//        val divider = if(weights == 7) 1 else 2
        val weights = 7
        val divider = 1
        val decayWeights = List(weights) { exp(-it.toDouble() / divider) }
        val jamFreqWithDecay = mutableMapOf<Int, Double>().apply {
            globalMinumPerJam.forEach { (jam, records) ->
                val totalDecay = records.mapIndexed { i, _ ->
                    decayWeights.getOrElse(i.coerceAtMost(weights - 1)) { 0.0 }
                }.sum()
                this[jam] = totalDecay
            }
        }

//        val sorted = jamFreqWithDecay.values.sorted()
//        val trimmed = sorted.drop(5).take(sorted.size - 10)
//        val meanDecay = trimmed.average()
//        val stdDecay = trimmed.std()

        val meanDecay = jamFreqWithDecay.values.average()
        val stdDecay = jamFreqWithDecay.values.std()
        val jamFreqZscoreDecay = jamFreqWithDecay[hour]?.let {
            (it - meanDecay) / (stdDecay + 1e-6)
        } ?: 0.0

        val isPuasa = false

        val puasaTimeInteraction: Double = if (isPuasa) {
            when (timeBucket) {
                0 -> 0.7  // Sahur
                4 -> 1.2  // Buka puasa
                5 -> 0.9  // Tarawih
                else -> 0.3
            }
        } else {
            0.0
        }

        val puasaDecayInteraction: Double = jamFreqZscoreDecay.times((if (isPuasa) 1.5 else 0.8)) ?: 0.0

        val dp = DataPoint(
            tanggal = tanggal,
            air = 0.0,
            waktuDetik = start,
            durasi = 0.0,
            dayOfWeek = dayOfWeek,
            sinDayOfWeek = sin(2 * Math.PI * dayOfWeek / 7),
            cosDayOfWeek = cos(2 * Math.PI * dayOfWeek / 7),
            timeBucket = timeBucket,
            sinTimeBucket = sin(2 * Math.PI * timeBucket / 6),
            cosTimeBucket = cos(2 * Math.PI * timeBucket / 6),
//            ordinalDate = localDate.toEpochDay().toInt(),
            sinTimeDayInteraction = sinTimeDayInteraction,
            cosTimeDayInteraction = cosTimeDayInteraction,
            isPuasa = isPuasa,
//            jamCategory = jamCategory,
            jamFreqZscore = freqZscore,
            jamFreqZscoreDecay = jamFreqZscoreDecay,
            puasaTimeInteraction = puasaTimeInteraction,
            puasaDecayInteraction = puasaDecayInteraction
        )

        var predAir = basePredictionAir + treesAir.sumOf { tree ->
            learningRate * predictTree(tree, dp)
        }

        var predWaktu = basePredictionWaktu + treesWaktu.sumOf { tree ->
            learningRate * predictTree(tree, dp)
        }

        if (predWaktu <= 0) {
            predWaktu = basePredictionWaktu
        }

        val steps = ((end - start) / predWaktu).toInt()
        return DoubleArray(steps) { predAir } to Array(steps) { predWaktu }
    }

    private fun delta(old: List<Double>, predicted: List<Double>): Double {
        return old.zip(predicted).map { abs(it.first - it.second) }.average()
    }

    fun evaluasi(
        actual: LinkedHashMap<String, Double>,
        predicted: LinkedHashMap<String, Double>,
        context: Context): EvaluationMetrics {
        if (actual.isEmpty()) return EvaluationMetrics(0.0, 0.0, 0.0, 0.0)

        val actualVals = actual.values.toList()
        val predVals = predicted.values.toList()

        val mae = actualVals.zip(predVals).sumOf { abs(it.first - it.second) } / actualVals.size
        val rmse = sqrt(actualVals.zip(predVals).sumOf { (a, p) -> (a - p).pow(2) } / actualVals.size)
        val smape = actualVals.zip(predVals).sumOf { (a, p) -> if ((abs(a) + abs(p)) != 0.0) 2 * abs(a - p) / (abs(a) + abs(p)) else 0.0 } / actualVals.size

        val mean = actualVals.average()
        val ssTotal = actualVals.sumOf { (it - mean).pow(2) }
        val ssRes = actualVals.zip(predVals).sumOf { (a, p) -> (a - p).pow(2) }
        val r2 = if (ssTotal != 0.0) 1 - (ssRes / ssTotal) else 0.0

        println("max_depth: $maxDepth | gamma: $gamma | lambda: $lambda | learning_rate: $learningRate | user: $idUser")
        println("MAE: $mae | RMSE: $rmse | SMAPE: $smape | R2: $r2")

        return EvaluationMetrics(smape, mae, rmse, r2)
    }
}

// -- VERSI FINAL --
//class XGBoost(
//    private val maxDepth: Int,
//    private val gamma: Double,
//    private val lambda: Double,
//    private val learningRate: Double,
//    private val idUser: Int
//) {
//    private var basePredictionAir = 0.0
//    private var basePredictionWaktu = 0.0
//    private val treesAir = mutableListOf<TreeNode>()
//    private val treesWaktu = mutableListOf<TreeNode>()
//    private val decimalFormat = DecimalFormat("#.##", DecimalFormatSymbols(Locale.US))
//
//    fun latihModel(tanggal: Array<String>, waktu: Array<String>, jumlahAir: DoubleArray, maxIterasi: Int) {
//        val waktuDetik = waktu.map { LocalTime.parse(it).toSecondOfDay().toDouble() }
//        val jumlahWaktu = waktuDetik.mapIndexed { i, w -> if (i == 0 || w - waktuDetik[i - 1] < 0) 0.0 else w - waktuDetik[i - 1] }
//
//        val dataPoints = tanggal.indices.map {
//            val localDate = LocalDate.parse(tanggal[it].substring(0, 10))
//            val hour = LocalTime.ofSecondOfDay(waktuDetik[it].toLong()).hour
//
//            val dayOfWeek = localDate.dayOfWeek.value
//            val sinDayOfWeek = sin(2 * Math.PI * dayOfWeek / 7)
//            val cosDayOfWeek = cos(2 * Math.PI * dayOfWeek / 7)
//
//            val timeBucket = when (hour) {
//                in 0..4 -> 0
//                in 5..8 -> 1
//                in 9..11 -> 2
//                in 12..15 -> 3
//                in 16..19 -> 4
//                in 20..23 -> 5
//                else -> -1
//            }
//            val sinTime = sin(2 * Math.PI * timeBucket / 6)
//            val cosTime = cos(2 * Math.PI * timeBucket / 6)
//
//            DataPoint(
//                tanggal = tanggal[it],
//                air = jumlahAir[it],
//                waktuDetik = waktuDetik[it],
//                durasi = jumlahWaktu[it],
//                dayOfWeek = dayOfWeek,
//                sinDayOfWeek = sinDayOfWeek,
//                cosDayOfWeek = cosDayOfWeek,
//                timeBucket = timeBucket,
//                sinTimeBucket = sinTime,
//                cosTimeBucket = cosTime
//            )
//        }
//
//        basePredictionAir = jumlahAir.average()
//        basePredictionWaktu = jumlahWaktu.average()
//
//        var residualsAir = jumlahAir.map { it - basePredictionAir }
//        var residualsWaktu = jumlahWaktu.map { it - basePredictionWaktu }
//
//        val epsilon = 1e-8
//        repeat(maxIterasi) {
//            val dataAir = dataPoints.zip(residualsAir).map { (dp, r) -> dp.copy(residual = r) }
//            val dataWaktu = dataPoints.zip(residualsWaktu).map { (dp, r) -> dp.copy(residual = r) }
//
//            val treeAir = buildTree(dataAir)
//            val treeWaktu = buildTree(dataWaktu)
//
//            treesAir.add(treeAir)
//            treesWaktu.add(treeWaktu)
//
//            residualsAir = dataPoints.map { dp ->
//                val pred = predictTree(treeAir, dp)
//                (residualsAir[dataPoints.indexOf(dp)] - learningRate * pred)
//            }
//
//            residualsWaktu = dataPoints.map { dp ->
//                val pred = predictTree(treeWaktu, dp)
//                (residualsWaktu[dataPoints.indexOf(dp)] - learningRate * pred)
//            }
//
//            val deltaAir = delta(residualsAir, dataPoints.map { dp -> predictTree(treeAir, dp) })
//            val deltaWaktu = delta(residualsWaktu, dataPoints.map { dp -> predictTree(treeWaktu, dp) })
//
//            if (deltaAir < epsilon && deltaWaktu < epsilon) return
//        }
//    }
//
//    private fun buildTree(data: List<DataPoint>, depth: Int = 0): TreeNode {
//        val simRoot = similarity(data.map { it.residual })
//        if (depth >= maxDepth && maxDepth != 0) return TreeNode(data.map { it.residual }, simRoot, isLeaf = true)
//
//        val best = cariSplitTerbaik(data, simRoot)
//        if (best == null || best.gain - gamma < 0) return TreeNode(data.map { it.residual }, simRoot, isLeaf = true)
//
//        val left = data.filter { getFitur(it, best.feature) < best.threshold }
//        val right = data.filter { getFitur(it, best.feature) >= best.threshold }
//
//        if (left.isEmpty() || right.isEmpty()) return TreeNode(data.map { it.residual }, simRoot, isLeaf = true)
//
//        return TreeNode(
//            residuals = data.map { it.residual },
//            similarity = simRoot,
//            isLeaf = false,
//            feature = best.feature,
//            thresholdNumeric = best.threshold,
//            gain = best.gain,
//            left = buildTree(left, depth + 1),
//            right = buildTree(right, depth + 1)
//        )
//    }
//
//    private data class SplitResult(val feature: String, val threshold: Double, val gain: Double)
//
//    private fun cariSplitTerbaik(data: List<DataPoint>, simRoot: Double): SplitResult? {
//        val features = listOf(
//            "sinDayOfWeek", "cosDayOfWeek",
//            "sinTimeBucket", "cosTimeBucket",
//        )
//        var best: SplitResult? = null
//
//        for (feature in features) {
//            val sorted = data.sortedBy { getFitur(it, feature) }
//            for (i in 1 until sorted.size) {
//                val threshold = (getFitur(sorted[i - 1], feature) + getFitur(sorted[i], feature)) / 2
//                val left = sorted.filter { getFitur(it, feature) < threshold }.map { it.residual }
//                val right = sorted.filter { getFitur(it, feature) >= threshold }.map { it.residual }
//
//                if (left.isEmpty() || right.isEmpty()) continue
//
//                val gain = similarity(left) + similarity(right) - simRoot
//                if (best == null || gain > best.gain) {
//                    best = SplitResult(feature, threshold, gain)
//                }
//            }
//        }
//
//        return best
//    }
//
//    private fun similarity(residuals: List<Double>): Double {
//        val sum = residuals.sum()
//        return (sum * sum) / (residuals.size + lambda)
//    }
//
//    private fun getFitur(dp: DataPoint, feature: String): Double {
//        return when (feature) {
//            "sinTimeBucket" -> dp.sinTimeBucket
//            "cosTimeBucket" -> dp.cosTimeBucket
//            "sinDayOfWeek" -> dp.sinDayOfWeek
//            "cosDayOfWeek" -> dp.cosDayOfWeek
//            else -> error("Fitur tidak dikenali: $feature")
//        }
//    }
//
//    private fun predictTree(tree: TreeNode, dp: DataPoint): Double {
//        return if (tree.isLeaf) {
//            tree.residuals.sum() / (tree.residuals.size + lambda)
//        } else {
//            val value = getFitur(dp, tree.feature!!)
//            if (value < tree.thresholdNumeric!!) {
//                predictTree(tree.left!!, dp)
//            } else {
//                predictTree(tree.right!!, dp)
//            }
//        }
//    }
//
//    fun prediksi(waktuTerakhir: String, batasWaktu: String, tanggal: String, bedaHari: Boolean): Pair<DoubleArray, Array<Double>>? {
//        if (treesAir.isEmpty() || treesWaktu.isEmpty()) return null
//
//        val start = LocalTime.parse(waktuTerakhir).toSecondOfDay().toDouble()
//        var end = LocalTime.parse(batasWaktu).toSecondOfDay().toDouble()
//        if (bedaHari) end += 86400
//
//        val localDate = LocalDate.parse(tanggal.substring(0,10))
//        val hour = LocalTime.ofSecondOfDay(start.toLong()).hour
//
//        val dayOfWeek = localDate.dayOfWeek.value
//        val sinDayOfWeek = sin(2 * Math.PI * dayOfWeek / 7)
//        val cosDayOfWeek = cos(2 * Math.PI * dayOfWeek / 7)
//
//        val timeBucket = when (hour) {
//            in 0..4 -> 0
//            in 5..8 -> 1
//            in 9..11 -> 2
//            in 12..15 -> 3
//            in 16..19 -> 4
//            in 20..23 -> 5
//            else -> -1
//        }
//        val sinTime = sin(2 * Math.PI * timeBucket / 6)
//        val cosTime = cos(2 * Math.PI * timeBucket / 6)
//
//        val dp = DataPoint(
//            tanggal = tanggal,
//            air = 0.0,
//            waktuDetik = start,
//            durasi = 0.0,
//            dayOfWeek = dayOfWeek,
//            sinDayOfWeek = sinDayOfWeek,
//            cosDayOfWeek = cosDayOfWeek,
//            timeBucket = timeBucket,
//            sinTimeBucket = sinTime,
//            cosTimeBucket = cosTime,
//        )
//
//        val predAir = basePredictionAir + treesAir.sumOf { tree ->
//            learningRate * predictTree(tree, dp)
//        }
//
//        val predWaktu = basePredictionWaktu + treesWaktu.sumOf { tree ->
//            learningRate * predictTree(tree, dp)
//        }
//
////        val predAir = basePredictionAir + learningRate * predictTree(treesAir.last(), dp)
////        val predWaktu = basePredictionWaktu + learningRate * predictTree(treesWaktu.last(), dp)
//
//        if (predWaktu <= 0) return null
//
//        val steps = ((end - start) / predWaktu).toInt()
//        return DoubleArray(steps) { predAir } to Array(steps) { predWaktu }
//    }
//
//    private fun delta(old: List<Double>, predicted: List<Double>): Double {
//        return old.zip(predicted).map { abs(it.first - it.second) }.average()
//    }
//
//    fun evaluasi(
//        actual: LinkedHashMap<String, Double>,
//        predicted: LinkedHashMap<String, Double>,
//        context: Context): EvaluationMetrics {
//        if (actual.isEmpty()) return EvaluationMetrics(0.0, 0.0, 0.0, 0.0)
//
//        val actualVals = actual.values.toList()
//        val predVals = predicted.values.toList()
//
//        val mae = actualVals.zip(predVals).sumOf { abs(it.first - it.second) } / actualVals.size
//        val rmse = sqrt(actualVals.zip(predVals).sumOf { (a, p) -> (a - p).pow(2) } / actualVals.size)
//        val smape = actualVals.zip(predVals).sumOf { (a, p) -> if ((abs(a) + abs(p)) != 0.0) 2 * abs(a - p) / (abs(a) + abs(p)) else 0.0 } / actualVals.size
//
//        val mean = actualVals.average()
//        val ssTotal = actualVals.sumOf { (it - mean).pow(2) }
//        val ssRes = actualVals.zip(predVals).sumOf { (a, p) -> (a - p).pow(2) }
//        val r2 = if (ssTotal != 0.0) 1 - (ssRes / ssTotal) else 0.0
//
//        println("max_depth: $maxDepth | gamma: $gamma | lambda: $lambda | learning_rate: $learningRate | user: $idUser")
//        println("MAE: $mae | RMSE: $rmse | SMAPE: $smape | R2: $r2")
//
//        return EvaluationMetrics(smape, mae, rmse, r2)
//    }
//}
//
//data class DataPoint(
//    val tanggal: String,
//    val air: Double,
//    val waktuDetik: Double,
//    val durasi: Double,
//    val dayOfWeek: Int,
//    val sinDayOfWeek: Double,
//    val cosDayOfWeek: Double,
//    val timeBucket: Int,
//    val sinTimeBucket: Double,
//    val cosTimeBucket: Double,
//    val residual: Double = 0.0,
//)
//
//
//data class TreeNode(
//    val residuals: List<Double>,
//    val similarity: Double,
//    val isLeaf: Boolean = false,
//    val feature: String? = null,
//    val thresholdNumeric: Double? = null,
//    val gain: Double? = null,
//    val left: TreeNode? = null,
//    val right: TreeNode? = null
//)


//package com.example.stationbottle.data
//
//import androidx.compose.material3.MaterialTheme
//import co.yml.charts.common.model.Point
//import co.yml.charts.ui.barchart.models.BarData
//import java.text.DecimalFormat
//import java.text.DecimalFormatSymbols
//import java.time.LocalTime
//import java.util.Locale
//import kotlin.math.abs
//import kotlin.math.pow
//import kotlin.math.sqrt
//
//class XGBoost(private val learningRate: Double, private val lambda: Double, private val gamma: Double) {
//    //    private val lambda = 100.0
////    private val gamma = 0.0
////    private val learningRate = 0.3
//    private val maxDepth = 10
//    private var prediksiAir = 0.0
//    private var prediksiWaktu = 0.0
//    private val treesAir = mutableListOf<TreeNode>()
//    private val treesWaktu = mutableListOf<TreeNode>()
//    val symbols = DecimalFormatSymbols(Locale.US)
//    val decimalFormat = DecimalFormat("#.##", symbols)
//
//    fun latihModel(tanggal: Array<String>, waktu: Array<String>, jumlahAir: DoubleArray, maxIterasi: Int) {
//
//        val waktuDalamDetik = waktu.map { LocalTime.parse(it).toSecondOfDay().toDouble() }
//        val jumlahWaktu = DoubleArray(waktuDalamDetik.size)
//
//        val dataPoints = waktuDalamDetik.mapIndexed { index, waktu ->
//            val perbedaanWaktu = if (index == 0 || waktu - waktuDalamDetik[index - 1] < 0) {
//                0.0
//            } else {
//                waktu - waktuDalamDetik[index - 1]
//            }
//            jumlahWaktu[index] = perbedaanWaktu
//            DataPoint(tanggal[index], jumlahAir[index], waktu, perbedaanWaktu)
//        }
//
//        prediksiAir = jumlahAir.average()
//        var residualsAir = jumlahAir.map {
//            decimalFormat.parse(decimalFormat.format(it - prediksiAir))!!.toDouble()
//        }
//
//        prediksiWaktu = jumlahWaktu.average()
//        var residualsWaktu = jumlahWaktu.map {
//            decimalFormat.parse(decimalFormat.format(it - prediksiWaktu))!!.toDouble()
//        }
//
//        val epsilon = 1e-6
//        for (iterasi in 1..maxIterasi) {
//            val rootAir = buatPohon(
//                dataPoints.map {
//                    DataPoint(it.tanggal, it.minum, residualsAir[dataPoints.indexOf(it)], it.timeDifference)
//                }, 0
//            )
//            treesAir.add(rootAir)
//
//            val rootWaktu = buatPohon(
//                dataPoints.map {
//                    DataPoint(it.tanggal, it.minum, residualsWaktu[dataPoints.indexOf(it)], it.timeDifference)
//                }, 0
//            )
//            treesWaktu.add(rootWaktu)
//
//            val newResidualsAir = dataPoints.mapIndexed { index, point ->
//                val outputValue = hitungOutputValue(rootAir, point.tanggal)
//                residualsAir[index] - learningRate * outputValue
//            }
//
//            val newResidualsWaktu = dataPoints.mapIndexed { index, point ->
//                val outputValue = hitungOutputValue(rootWaktu, point.tanggal)
//                residualsWaktu[index] - learningRate * outputValue
//            }
//
//            val deltaAir = newResidualsAir.zip(residualsAir).map { (new, old) -> abs(new - old) }.average()
//            val deltaWaktu = newResidualsWaktu.zip(residualsWaktu).map { (new, old) -> abs(new - old) }.average()
//
//            residualsAir = newResidualsAir
//            residualsWaktu = newResidualsWaktu
//
//            if (deltaAir < epsilon && deltaWaktu < epsilon) {
//                println("Pelatihan berhenti pada iterasi ke-$iterasi karena perubahan residual sudah kecil.")
//                break
//            }
//        }
//    }
//
//    private fun buatPohon(data: List<DataPoint>, depth: Int = 0): TreeNode {
//        val rootSimilarity = hitungSimilarity(data.map { it.residual })
//
//        if (depth >= maxDepth) {
//            return TreeNode(
//                residuals = data.map { it.residual },
//                similarity = rootSimilarity,
//                isLeaf = true
//            )
//        }
//
//        val (bestThreshold, bestGain) = temukanThresholdTerbaik(data)
//
//        if (bestGain - gamma < 0) {
//            return TreeNode(
//                residuals = data.map { it.residual },
//                similarity = rootSimilarity,
//                isLeaf = true
//            )
//        }
//
//        val leftData = data.filter {  it.tanggal < bestThreshold }
//        val rightData = data.filter { it.tanggal >= bestThreshold }
//
//        if(leftData.isEmpty() || rightData.isEmpty()){
//            return TreeNode(
//                residuals = data.map { it.residual },
//                similarity = rootSimilarity,
//                isLeaf = true
//            )
//        }
//
//        return TreeNode(
//            residuals = data.map { it.residual },
//            similarity = rootSimilarity,
//            threshold = bestThreshold,
//            gain = bestGain,
//            left = buatPohon(leftData, depth + 1),
//            right = buatPohon(rightData, depth + 1)
//        )
//    }
//
//    private fun hitungSimilarity(residuals: List<Double>): Double {
//        val sumResiduals = decimalFormat.parse(decimalFormat.format(residuals.sum()))!!.toDouble()
//        val similarity = (sumResiduals * sumResiduals) / (residuals.size + lambda)
//        return similarity
//    }
//
//    private fun hitungGain(leftResiduals: List<Double>, rightResiduals: List<Double>, rootSimilarity: Double): Double {
//        val leftSimilarity = hitungSimilarity(leftResiduals)
//        val rightSimilarity = hitungSimilarity(rightResiduals)
//        return leftSimilarity + rightSimilarity - rootSimilarity
//    }
//
//    private fun temukanThresholdTerbaik(data: List<DataPoint>): Pair<String, Double> {
//        var thresholdTerbaik = ""
//        var gainTerbaik = Double.NEGATIVE_INFINITY
//
//        for (i in 1 until data.size) {
//
//            val threshold = data[i].tanggal.toString()
//
//            val leftResiduals = data.filter { it.tanggal < data[i].tanggal }.map { it.residual }
//            val rightResiduals = data.filter { it.tanggal >= data[i].tanggal }.map { it.residual }
//
//            val rootSimilarity = hitungSimilarity(data.map { it.residual })
//            val gain = hitungGain(leftResiduals, rightResiduals, rootSimilarity)
//
//            if (gain > gainTerbaik) {
//                thresholdTerbaik = threshold
//                gainTerbaik = gain
//            }
//        }
//        return thresholdTerbaik to gainTerbaik
//    }
//
//    private fun hitungOutputValue(pohon: TreeNode, tanggal: String): Double {
//        if (pohon.isLeaf) {
//            return pohon.residuals.sum() / (pohon.residuals.size + lambda)
//        }
//        return if (tanggal < pohon.threshold!!) {
//            hitungOutputValue(pohon.left!!, tanggal)
//        } else {
//            hitungOutputValue(pohon.right!!, tanggal)
//        }
//    }
//
//    fun prediksi(waktuTerakhir: String, batasWaktu: String, tanggalTerakhir: String, isBedaHari: Boolean): Pair<DoubleArray, Array<Double>>? {
//        if (treesAir.isEmpty() || treesWaktu.isEmpty()) {
//            println("Model has not been trained. Please train before prediction.")
//            return null
//        }
//
//        val waktuTerakhirDetik = LocalTime.parse(waktuTerakhir).toSecondOfDay().toDouble()
//        var batasWaktuDetik = LocalTime.parse(batasWaktu).toSecondOfDay().toDouble()
//
//        if(isBedaHari){
//            batasWaktuDetik += 86400
//        }
//
////        println(treesAir.last())
////        println(treesWaktu.last())
//
//        val predAir = prediksiAir + learningRate * hitungOutputValue(treesAir.last(), tanggalTerakhir)
//        val predWaktu = prediksiWaktu + learningRate * hitungOutputValue(treesWaktu.last(), tanggalTerakhir)
//
//        if (predWaktu <= 0) {
//            println("Invalid predicted time interval (predWaktu): $predWaktu")
//            return null
//        }
//
//        val totalInterval = ((batasWaktuDetik - waktuTerakhirDetik) / predWaktu).toInt()
//
//        val waktuArray = Array(totalInterval) { index ->
//            val waktuDetik = waktuTerakhirDetik + (index + 1) * predWaktu
//            var waktu:String
//            if(isBedaHari && waktuDetik > 86400){
//                waktu = LocalTime.ofSecondOfDay((waktuDetik - 86400).toLong()).toString()
//            } else {
//                waktu = LocalTime.ofSecondOfDay(waktuDetik.toLong()).toString()
//            }
//            waktu
//        }
//
//        val prediksiAir = waktuArray.map { predAir }.toDoubleArray()
//        val prediksiWaktu = waktuArray.map { predWaktu }.toTypedArray()
//
//        return prediksiAir to prediksiWaktu
//    }
//
//
//    fun evaluasi(actual: LinkedHashMap<String, Double>, predicted: LinkedHashMap<String, Double>): EvaluationMetrics {
//        val n = actual.size
//
//        if (n == 0) {
//            return EvaluationMetrics(0.0, 0.0, 0.0, 0.0)
//        }
//
//        println("----actual-----")
//
//        actual.entries.forEachIndexed { index, entry ->
//            val date = entry.key
//            val drinkValue = entry.value
//
//            println("date: $date | drink: $drinkValue")
//        }
//
//        println("----prediksi-----")
//
//        predicted.entries.forEachIndexed { index, entry ->
//            val date = entry.key
//            val drinkValue = entry.value
//
//            println("date: $date | drink: $drinkValue")
//        }
//
//        val actualValues = actual.values.toList()
//        val predictedValues = predicted.values.toList()
//
//        val mae = actualValues.zip(predictedValues).sumOf { (a, p) ->
//            abs(a - p)
//        } / n
//
//        val rmse = sqrt(
//            actualValues.zip(predictedValues).sumOf { (a, p) ->
//                (a - p).pow(2)
//            } / n
//        )
//
//        val smape = actualValues.zip(predictedValues).sumOf { (a, p) ->
//            val denominator = (abs(a) + abs(p))
//            if (denominator != 0.0) 2 * abs(a - p) / denominator else 0.0
//        } / n
//
//        val meanActual = actualValues.average()
//        val ssTotal = actualValues.sumOf { (it - meanActual).pow(2) }
//        val ssResidual = actualValues.zip(predictedValues).sumOf { (a, p) ->
//            (a - p).pow(2)
//        }
//        val r2 = if (ssTotal != 0.0) 1 - ssResidual / ssTotal else 0.0
//
//        println("max_depth: $maxDepth, gamma: $gamma, lambda: $lambda, learning_rate: $learningRate")
//        println("mae: \n$mae\nrmse: \n$rmse\nsmape: \n$smape\nr2: \n$r2")
//
//        return EvaluationMetrics(smape, mae, rmse, r2)
//    }
//}
//
//
//data class TreeNode(
//    val residuals: List<Double>,
//    val similarity: Double,
//    val threshold: String? = null,
//    val gain: Double? = null,
//    val left: TreeNode? = null,
//    val right: TreeNode? = null,
//    val isLeaf: Boolean = false
//)

//data class DataPoint(
//    val tanggal: String,
//    val minum: Double,
////    val waktu: Double,
//    val residual: Double,
//    val timeDifference: Double
//)

//data class EvaluationMetrics(
//    val smape: Double,
//    val mae: Double,
//    val rmse: Double,
//    val r2: Double
//)

//package com.example.stationbottle.data
//
//import java.text.DecimalFormat
//import java.text.DecimalFormatSymbols
//import java.time.LocalDateTime
//import java.time.LocalTime
//import java.time.format.DateTimeFormatter
//import java.util.Locale
//import kotlin.math.abs
//import kotlin.math.pow
//import kotlin.math.sqrt
//
//class XGBoost(private val learningRate: Double, private val lambda: Double, private val gamma: Double) {
////    private val lambda = 100.0
////    private val gamma = 0.0
////    private val learningRate = 0.3
//    private val maxDepth = 10
//    private var prediksiAir = 0.0
//    private var prediksiWaktu = 0.0
//    private val treesAir = mutableListOf<TreeNode>()
//    private val treesWaktu = mutableListOf<TreeNode>()
//    val symbols = DecimalFormatSymbols(Locale.US)
//    val decimalFormat = DecimalFormat("#.##", symbols)
//
//    fun latihModel(tanggal: Array<String>, waktu: Array<String>, jumlahAir: DoubleArray, maxIterasi: Int) {
//
//        val waktuDalamDetik = DoubleArray(waktu.size) { index ->
//            LocalTime.parse(waktu[index]).toSecondOfDay().toDouble()
//        }
//        val jumlahWaktu = DoubleArray(waktuDalamDetik.size)
//
//        val dataPoints = waktuDalamDetik.mapIndexed { index, waktuDetik ->
//            val perbedaanWaktu = if (index == 0 || waktuDetik - waktuDalamDetik[index - 1] < 0) {
//                0.0
//            } else {
//                waktuDetik - waktuDalamDetik[index - 1]
//            }
//            jumlahWaktu[index] = perbedaanWaktu
//            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
//            val localDateTime = LocalDateTime.parse(tanggal[index], formatter)
//            val localDate = localDateTime.toLocalDate()
//            val localTime = LocalTime.parse(waktu[index])
//            val dayOfWeek = localDate.dayOfWeek.value  // 1 (Monday) to 7 (Sunday)
//            val isWeekend = if (dayOfWeek >= 6) 1 else 0
//            val hourOfDay = localTime.hour
//
//            DataPoint(
//                tanggal[index],
//                jumlahAir[index],
//                0.0,
//                perbedaanWaktu,
//                dayOfWeek,
//                isWeekend,
//                hourOfDay,
//                waktu[index]
//            )
//        }
//
//        prediksiAir = jumlahAir.average()
//        var residualsAir = jumlahAir.map {
//            decimalFormat.parse(decimalFormat.format(it - prediksiAir))!!.toDouble()
//        }
//
//        prediksiWaktu = jumlahWaktu.average()
//        var residualsWaktu = jumlahWaktu.map {
//            decimalFormat.parse(decimalFormat.format(it - prediksiWaktu))!!.toDouble()
//        }
//
//        val epsilon = 1e-6
//        for (iterasi in 1..maxIterasi) {
//
//            val rootAir = buatPohon(
//                dataPoints.mapIndexed { i, dp -> dp.copy(residual = residualsAir[i]) }
//            )
//            treesAir.add(rootAir)
//
//            val rootWaktu = buatPohon(
//                dataPoints.mapIndexed { i, dp -> dp.copy(residual = residualsWaktu[i]) }
//            )
//            treesWaktu.add(rootWaktu)
//
//            val newResidualsAir = dataPoints.mapIndexed { index, point ->
//                val outputValue = hitungOutputValue(rootAir, point)
//                residualsAir[index] - learningRate * outputValue
//            }
//
//            val newResidualsWaktu = dataPoints.mapIndexed { index, point ->
//                val outputValue = hitungOutputValue(rootWaktu, point)
//                residualsWaktu[index] - learningRate * outputValue
//            }
//
//            val deltaAir = newResidualsAir.zip(residualsAir).map { (new, old) -> abs(new - old) }.average()
//            val deltaWaktu = newResidualsWaktu.zip(residualsWaktu).map { (new, old) -> abs(new - old) }.average()
//
//            residualsAir = newResidualsAir
//            residualsWaktu = newResidualsWaktu
//
//            if (deltaAir < epsilon && deltaWaktu < epsilon) {
//                println("Pelatihan berhenti pada iterasi ke-$iterasi karena perubahan residual sudah kecil.")
//                break
//            }
//        }
//    }
//
//    private fun buatPohon(data: List<DataPoint>, depth: Int = 0): TreeNode {
//        val rootSimilarity = hitungSimilarity(data.map { it.residual })
//
//        if (depth >= maxDepth) {
//            return TreeNode(
//                residuals = data.map { it.residual },
//                similarity = rootSimilarity,
//                isLeaf = true
//            )
//        }
//
//        val fiturList: List<(DataPoint) -> Double> = listOf(
//            { it.hourOfDay.toDouble() },
//            { it.isWeekend.toDouble() },
//            { it.timeDifference }
//        )
//
//        var bestThreshold = 0.0
//        var bestGain = Double.NEGATIVE_INFINITY
//        var bestFeatureIndex = -1
//        var bestLeft: List<DataPoint> = emptyList()
//        var bestRight: List<DataPoint> = emptyList()
//
//        for ((i, fitur) in fiturList.withIndex()) {
//            val (threshold, gain, left, right) = temukanThresholdTerbaik(data, fitur)
//            if (gain > bestGain) {
//                bestGain = gain
//                bestThreshold = threshold
//                bestFeatureIndex = i
//                bestLeft = left
//                bestRight = right
//            }
//        }
//
//        if (bestGain - gamma < 0 || bestLeft.isEmpty() || bestRight.isEmpty()) {
//            return TreeNode(
//                residuals = data.map { it.residual },
//                similarity = rootSimilarity,
//                isLeaf = true
//            )
//        }
//
//        return TreeNode(
//            residuals = data.map { it.residual },
//            similarity = rootSimilarity,
//            threshold = bestThreshold.toString(),
//            featureIndex = bestFeatureIndex,
//            gain = bestGain,
//            left = buatPohon(bestLeft, depth + 1),
//            right = buatPohon(bestRight, depth + 1)
//        )
//    }
//
//    private fun hitungSimilarity(residuals: List<Double>): Double {
//        val sumResiduals = decimalFormat.parse(decimalFormat.format(residuals.sum()))!!.toDouble()
//        val similarity = (sumResiduals * sumResiduals) / (residuals.size + lambda)
//        return similarity
//    }
//
//    private fun hitungGain(leftResiduals: List<Double>, rightResiduals: List<Double>, rootSimilarity: Double): Double {
//        val leftSimilarity = hitungSimilarity(leftResiduals)
//        val rightSimilarity = hitungSimilarity(rightResiduals)
//        return leftSimilarity + rightSimilarity - rootSimilarity
//    }
//
//    private fun temukanThresholdTerbaik(
//        data: List<DataPoint>,
//        featureSelector: (DataPoint) -> Double
//    ): Quadruple<Double, Double, List<DataPoint>, List<DataPoint>> {
//        var thresholdTerbaik = 0.0
//        var gainTerbaik = Double.NEGATIVE_INFINITY
//        var bestLeft = emptyList<DataPoint>()
//        var bestRight = emptyList<DataPoint>()
//
//        val sortedData = data.sortedBy { featureSelector(it) }
//
//        for (i in 1 until sortedData.size) {
//            val threshold = (featureSelector(sortedData[i - 1]) + featureSelector(sortedData[i])) / 2
//
//            val left = sortedData.filter { featureSelector(it) < threshold }
//            val right = sortedData.filter { featureSelector(it) >= threshold }
//
//            val rootSimilarity = hitungSimilarity(data.map { it.residual })
//            val gain = hitungGain(left.map { it.residual }, right.map { it.residual }, rootSimilarity)
//
//            if (gain > gainTerbaik) {
//                thresholdTerbaik = threshold
//                gainTerbaik = gain
//                bestLeft = left
//                bestRight = right
//            }
//        }
//
//        return Quadruple(thresholdTerbaik, gainTerbaik, bestLeft, bestRight)
//    }
//
//    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
//
//    private fun hitungOutputValue(pohon: TreeNode, data: DataPoint): Double {
//        if (pohon.isLeaf) {
//            return pohon.residuals.sum() / (pohon.residuals.size + lambda)
//        }
//
//        val fiturValue = when (pohon.featureIndex) {
//            0 -> data.hourOfDay.toDouble()
//            1 -> data.isWeekend.toDouble()
//            2 -> data.timeDifference
//            else -> throw IllegalArgumentException("Invalid feature index")
//        }
//
//        val threshold = pohon.threshold!!.toDouble()
//        return if (fiturValue < threshold) {
//            hitungOutputValue(pohon.left!!, data)
//        } else {
//            hitungOutputValue(pohon.right!!, data)
//        }
//    }
//
//    fun prediksi(waktuTerakhir: String, batasWaktu: String, tanggalTerakhir: String, isBedaHari: Boolean): Pair<DoubleArray, Array<Double>>? {
//        if (treesAir.isEmpty() || treesWaktu.isEmpty()) {
//            println("Model has not been trained. Please train before prediction.")
//            return null
//        }
//
//        val waktuTerakhirDetik = LocalTime.parse(waktuTerakhir).toSecondOfDay().toDouble()
//        var batasWaktuDetik = LocalTime.parse(batasWaktu).toSecondOfDay().toDouble()
//
//        if(isBedaHari){
//            batasWaktuDetik += 86400
//        }
//
////        println(treesAir.last())
////        println(treesWaktu.last())
//
////        val predAir = prediksiAir + learningRate * hitungOutputValue(treesAir.last())
////        val predWaktu = prediksiWaktu + learningRate * hitungOutputValue(treesWaktu.last())
//
//        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
//        val localDateTime = LocalDateTime.parse(tanggalTerakhir, formatter)
//        val localDate = localDateTime.toLocalDate()
//        val dayOfWeek = localDate.dayOfWeek.value
//        val isWeekend = if (dayOfWeek >= 6) 1 else 0
//        val hourOfDay = LocalTime.parse(waktuTerakhir).hour
//
//        val dataPoint = DataPoint(
//            tanggal = tanggalTerakhir,
//            minum = 0.0, // placeholder
//            residual = 0.0, // placeholder
//            timeDifference = 0.0, // bisa dihitung atau di-set default
//            dayOfWeek = dayOfWeek,
//            isWeekend = isWeekend,
//            hourOfDay = hourOfDay,
//            waktuDetik = waktuTerakhir
//        )
//
////        val outputAir = hitungOutputValue(treesAir.last(), dataPoint)
////        val outputWaktu = hitungOutputValue(treesWaktu.last(), dataPoint)
////
////        val predAir = prediksiAir + learningRate * outputAir
////        val predWaktu = prediksiWaktu + learningRate * outputWaktu
//
//        val predAir = prediksiAir + treesAir.sumOf { tree -> learningRate * hitungOutputValue(tree, dataPoint) }
//        val predWaktu = prediksiWaktu + treesWaktu.sumOf { tree -> learningRate * hitungOutputValue(tree, dataPoint) }
//
//        if (predWaktu <= 0) {
//            println("Invalid predicted time interval (predWaktu): $predWaktu")
//            return null
//        }
//
//        val totalInterval = ((batasWaktuDetik - waktuTerakhirDetik) / predWaktu).toInt()
//
//        val waktuArray = Array(totalInterval) { index ->
//            val waktuDetik = waktuTerakhirDetik + (index + 1) * predWaktu
//            var waktu:String
//            if(isBedaHari && waktuDetik > 86400){
//                waktu = LocalTime.ofSecondOfDay((waktuDetik - 86400).toLong()).toString()
//            } else {
//                waktu = LocalTime.ofSecondOfDay(waktuDetik.toLong()).toString()
//            }
//            waktu
//        }
//
//        val prediksiAir = waktuArray.map { predAir }.toDoubleArray()
//        val prediksiWaktu = waktuArray.map { predWaktu }.toTypedArray()
//
//        return prediksiAir to prediksiWaktu
//    }
//
//
//    fun evaluasi(actual: LinkedHashMap<String, Double>, predicted: LinkedHashMap<String, Double>): EvaluationMetrics {
//        val n = actual.size
//
//        if (n == 0) {
//            return EvaluationMetrics(0.0, 0.0, 0.0, 0.0)
//        }
//
//        println("----actual-----")
//
//        actual.entries.forEachIndexed { index, entry ->
//            val date = entry.key
//            val drinkValue = entry.value
//
//            println("date: $date | drink: $drinkValue")
//        }
//
//        println("----prediksi-----")
//
//        predicted.entries.forEachIndexed { index, entry ->
//            val date = entry.key
//            val drinkValue = entry.value
//
//            println("date: $date | drink: $drinkValue")
//        }
//
//        val actualValues = actual.values.toList()
//        val predictedValues = predicted.values.toList()
//
//        val mae = actualValues.zip(predictedValues).sumOf { (a, p) ->
//            abs(a - p)
//        } / n
//
//        val rmse = sqrt(
//            actualValues.zip(predictedValues).sumOf { (a, p) ->
//                (a - p).pow(2)
//            } / n
//        )
//
//        val smape = actualValues.zip(predictedValues).sumOf { (a, p) ->
//            val denominator = (abs(a) + abs(p))
//            if (denominator != 0.0) 2 * abs(a - p) / denominator else 0.0
//        } / n
//
//        val meanActual = actualValues.average()
//        val ssTotal = actualValues.sumOf { (it - meanActual).pow(2) }
//        val ssResidual = actualValues.zip(predictedValues).sumOf { (a, p) ->
//            (a - p).pow(2)
//        }
//        val r2 = if (ssTotal != 0.0) 1 - ssResidual / ssTotal else 0.0
//
//        println("max_depth: $maxDepth, gamma: $gamma, lambda: $lambda, learning_rate: $learningRate")
//        println("mae: \n$mae\nrmse: \n$rmse\nsmape: \n$smape\nr2: \n$r2")
//
//        return EvaluationMetrics(smape, mae, rmse, r2)
//    }
//}
//
//
//data class TreeNode(
//    val residuals: List<Double>,
//    val similarity: Double,
//    val threshold: String? = null,
//    val featureIndex: Int? = null,
//    val gain: Double? = null,
//    val left: TreeNode? = null,
//    val right: TreeNode? = null,
//    val isLeaf: Boolean = false
//)
//
//data class DataPoint(
//    val tanggal: String,
//    val minum: Double,
////    val waktu: Double,
//    val residual: Double,
//    val timeDifference: Double,
//    val dayOfWeek: Int,
//    val isWeekend: Int,
//    val hourOfDay: Int,
//    val waktuDetik: String
//)
//
//data class EvaluationMetrics(
//    val mape: Double,
//    val mae: Double,
//    val rmse: Double,
//    val r2: Double
//)
