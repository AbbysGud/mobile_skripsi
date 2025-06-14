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
    val ordinalDate: Int,
    val sinTimeDayInteraction: Double,
    val cosTimeDayInteraction: Double,
    val isPuasa: Boolean,
//    val jamCategory: Int,
    val jamFreqZscore: Double,
    val jamFreqZscoreDecay: Double,
    val puasaTimeInteraction: Double,
    val puasaDecayInteraction: Double,
    val sinDayHourInteraction: Double,
    val cosDayHourInteraction: Double,
    val sinHour: Double,
    val cosHour: Double,
    val isWeekend: Boolean,
    val isMealTime: Boolean,
    val mealType: Int
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

    private val SARAPAN_RANGE = 6..9
    private val MAKAN_SIANG_RANGE = 11..13
    private val MAKAN_MALAM_RANGE = 17..20

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

        globalMinumPerJam = minumPerJam

        val entryCounts = minumPerJam.mapValues { it.value.size }

        val meanFreq = entryCounts.values.average()
        val stdFreq = entryCounts.values.map { (it - meanFreq).pow(2) }.average().let { sqrt(it) }

        val jamFreqZscore = entryCounts.mapValues { (it.value - meanFreq) / (stdFreq + 1e-6) }

        val weights = 14
        val divider = 2
        val decayWeights = List(weights) { exp(-it.toDouble() / divider) }

        val lastDate = tanggal.maxOf { LocalDate.parse(it.substring(0, 10)) }

        val jamFreqWithDecay = mutableMapOf<Int, Double>().apply {
            tanggal.forEachIndexed { i, tgl ->
                val hariKe = ChronoUnit.DAYS.between(
                    LocalDate.parse(tgl.substring(0, 10)),
                    lastDate
                ).toInt()

                val weight = decayWeights.getOrElse(hariKe.coerceIn(0, weights - 1)) { 0.0 }
                val jam = LocalTime.parse(waktu[i]).hour
                this[jam] = this.getOrDefault(jam, 0.0) + weight
            }
        }

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

//            val timeBucket = when (hour) {
//                in 0..4 -> 0
//                in 5..8 -> 1
//                in 9..11 -> 2
//                in 12..15 -> 3
//                in 16..19 -> 4
//                in 20..23 -> 5
//                else -> -1
//            }

            val timeBucket = when (hour) {
                in 0..4 -> 0
                in 5..9 -> 1
                in 10..14 -> 2
                in 15..19 -> 3
                in 20..23 -> 4
                else -> -1
            }

            val timeDayInteraction = timeBucket * dayOfWeek
            val sinTimeDayInteraction = sin(2 * Math.PI * timeDayInteraction / (6*7))
            val cosTimeDayInteraction = cos(2 * Math.PI * timeDayInteraction / (6*7))

//            val isPuasa: Boolean = jumlahWaktu[it] > 12 * 3600 && jumlahWaktu[it] != 0.0

            val isPuasa = (
                    !localDate.isBefore(RAMADAN_START) && !localDate.isAfter(RAMADAN_END)
                ) || (
                    jumlahWaktu[it] > 12 * 3600 && jumlahWaktu[it] != 0.0
                )

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

            val dayHourInteraction = dayOfWeek * hour

            val sinDayHourInteraction = sin(2 * Math.PI * dayHourInteraction / (7*24))
            val cosDayHourInteraction = cos(2 * Math.PI * dayHourInteraction / (7*24))

            val sinHour = sin(2 * Math.PI * hour / 24)
            val cosHour = cos(2 * Math.PI * hour / 24)

            val isWeekend = (dayOfWeek == 6 || dayOfWeek == 7)

            val isSarapan = hour in SARAPAN_RANGE
            val isMakanSiang = hour in MAKAN_SIANG_RANGE
            val isMakanMalam = hour in MAKAN_MALAM_RANGE

            val mealType = when {
                isSarapan -> 1
                isMakanSiang -> 2
                isMakanMalam -> 3
                else -> 0
            }

            DataPoint(
                tanggal = tanggal[it],
                air = jumlahAir[it],
                waktuDetik = waktuDetik[it],
                durasi = jumlahWaktu[it],
                dayOfWeek = dayOfWeek,
                sinDayOfWeek = sin(2 * Math.PI * dayOfWeek / 7),
                cosDayOfWeek = cos(2 * Math.PI * dayOfWeek / 7),
                timeBucket = timeBucket,
//                sinTimeBucket = sin(2 * Math.PI * timeBucket / 6),
//                cosTimeBucket = cos(2 * Math.PI * timeBucket / 6),
                sinTimeBucket = sin(2 * Math.PI * timeBucket / 5),
                cosTimeBucket = cos(2 * Math.PI * timeBucket / 5),
                sinTimeDayInteraction = sinTimeDayInteraction,
                cosTimeDayInteraction = cosTimeDayInteraction,
                isPuasa = isPuasa,
                jamFreqZscore = freqZscore,
                jamFreqZscoreDecay = freqZscoreDecay,
                puasaTimeInteraction = puasaTimeInteraction,
                puasaDecayInteraction = puasaDecayInteraction,
                ordinalDate = localDate.toEpochDay().toInt(),
                sinDayHourInteraction = sinDayHourInteraction,
                cosDayHourInteraction = cosDayHourInteraction,
                sinHour = sinHour,
                cosHour = cosHour,
                isWeekend = isWeekend,
                isMealTime = isSarapan || isMakanSiang || isMakanMalam,
                mealType = mealType
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
//            "sinDayOfWeek", "cosDayOfWeek",
//            "sinTimeBucket", "cosTimeBucket",
            "sinTimeDayInteraction", "cosTimeDayInteraction",
//            "isPuasa",
//            "jamFreqZscore",
            "jamFreqZscoreDecay",
//            "puasaTimeInteraction",
//            "puasaDecayInteraction",
            "sinDayHourInteraction", "cosDayHourInteraction",
//            "sinHour", "cosHour",
//            "isWeekend",
//            "isMealTime",
//            "mealType"
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
            "ordinalDate" -> dp.ordinalDate.toDouble()
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
            "sinDayHourInteraction" -> dp.sinDayHourInteraction
            "cosDayHourInteraction" -> dp.cosDayHourInteraction
            "sinHour" -> dp.sinHour
            "cosHour" -> dp.cosHour
            "isWeekend" -> if (dp.isWeekend) 1.0 else 0.0
            "isMealTime" -> if (dp.isMealTime) 1.0 else 0.0
            "mealType" -> dp.mealType.toDouble()
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

        val predictionsAir = mutableListOf<Double>()
        val predictionsTime = mutableListOf<Double>()

        var currentTime = start
        while (currentTime < end) {

            val localDate = LocalDate.parse(tanggal.substring(0,10))
            val hour = LocalTime.ofSecondOfDay(currentTime.toLong()).hour
            val dayOfWeek = localDate.dayOfWeek.value

//            val timeBucket = when (hour) {
//                in 0..4 -> 0
//                in 5..8 -> 1
//                in 9..11 -> 2
//                in 12..15 -> 3
//                in 16..19 -> 4
//                in 20..23 -> 5
//                else -> -1
//            }

            val timeBucket = when (hour) {
                in 0..4 -> 0
                in 5..9 -> 1
                in 10..14 -> 2
                in 15..19 -> 3
                in 20..23 -> 4
                else -> -1
            }

            val timeDayInteraction = timeBucket * dayOfWeek
            val sinTimeDayInteraction = sin(2 * Math.PI * timeDayInteraction / (6*7))
            val cosTimeDayInteraction = cos(2 * Math.PI * timeDayInteraction / (6*7))

            val entryCounts = globalMinumPerJam.mapValues { it.value.size }

            val meanFreq = entryCounts.values.average()
            val stdFreq = entryCounts.values.map { (it - meanFreq).pow(2) }.average().let { sqrt(it) }

            val jamFreqZscore = entryCounts.mapValues { (it.value - meanFreq) / (stdFreq + 1e-6) }

            val freqZscore = jamFreqZscore[hour] ?: 0.0

            val weights = 14
            val divider = 2
            val decayWeights = List(weights) { exp(-it.toDouble() / divider) }
            val jamFreqWithDecay = mutableMapOf<Int, Double>().apply {
                globalMinumPerJam.forEach { (jam, records) ->
                    val totalDecay = records.mapIndexed { i, _ ->
                        decayWeights.getOrElse(i.coerceAtMost(weights - 1)) { 0.0 }
                    }.sum()
                    this[jam] = totalDecay
                }
            }

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

            val dayHourInteraction = dayOfWeek * hour
            val sinDayHourInteraction = sin(2 * Math.PI * dayHourInteraction / (7*24))
            val cosDayHourInteraction = cos(2 * Math.PI * dayHourInteraction / (7*24))

            val sinHour = sin(2 * Math.PI * hour / 24)
            val cosHour = cos(2 * Math.PI * hour / 24)

            val isWeeekend = (dayOfWeek == 6 || dayOfWeek == 7)

            val isSarapan = hour in SARAPAN_RANGE
            val isMakanSiang = hour in MAKAN_SIANG_RANGE
            val isMakanMalam = hour in MAKAN_MALAM_RANGE

            val mealType = when {
                isSarapan -> 1
                isMakanSiang -> 2
                isMakanMalam -> 3
                else -> 0
            }

            val dp = DataPoint(
                tanggal = tanggal,
                air = 0.0,
                waktuDetik = currentTime,
                durasi = 0.0,
                dayOfWeek = dayOfWeek,
                sinDayOfWeek = sin(2 * Math.PI * dayOfWeek / 7),
                cosDayOfWeek = cos(2 * Math.PI * dayOfWeek / 7),
                timeBucket = timeBucket,
//                sinTimeBucket = sin(2 * Math.PI * timeBucket / 6),
//                cosTimeBucket = cos(2 * Math.PI * timeBucket / 6),
                sinTimeBucket = sin(2 * Math.PI * timeBucket / 5),
                cosTimeBucket = cos(2 * Math.PI * timeBucket / 5),
                ordinalDate = localDate.toEpochDay().toInt(),
                sinTimeDayInteraction = sinTimeDayInteraction,
                cosTimeDayInteraction = cosTimeDayInteraction,
                isPuasa = isPuasa,
        //            jamCategory = jamCategory,
                jamFreqZscore = freqZscore,
                jamFreqZscoreDecay = jamFreqZscoreDecay,
                puasaTimeInteraction = puasaTimeInteraction,
                puasaDecayInteraction = puasaDecayInteraction,
                sinDayHourInteraction = sinDayHourInteraction,
                cosDayHourInteraction = cosDayHourInteraction,
                sinHour = sinHour,
                cosHour = cosHour,
                isWeekend = isWeeekend,
                isMealTime = isSarapan || isMakanSiang || isMakanMalam,
                mealType = mealType
            )

            var predAir = basePredictionAir + treesAir.sumOf { tree ->
                learningRate * predictTree(tree, dp)
            }

            var predWaktu = basePredictionWaktu + treesWaktu.sumOf { tree ->
                learningRate * predictTree(tree, dp)
            }

            predictionsAir.add(predAir)
            predictionsTime.add(predWaktu)

            currentTime += predWaktu // Update waktu untuk prediksi berikutnya
        }

        return predictionsAir.toDoubleArray() to predictionsTime.toTypedArray()
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

//        actual.keys.forEachIndexed { index, key ->
//            val actualVal = actual[key]
//            val predVal = predicted[key]
//            println("Tanggal: $key | Actual: $actualVal | Predicted: ${predVal ?: "N/A"}")
//        }

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
