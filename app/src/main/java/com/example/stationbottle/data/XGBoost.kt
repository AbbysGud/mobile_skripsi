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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val Json = Json { prettyPrint = true }

@Serializable
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

@Serializable
data class SerializableTreeNode(
    val residuals: List<Double>,
    val similarity: Double,
    val isLeaf: Boolean = false,
    val feature: String? = null,
    val thresholdNumeric: Double? = null,
    val gain: Double? = null,
    val left: SerializableTreeNode? = null,
    val right: SerializableTreeNode? = null
)

@Serializable
data class SerializableXGBoostModel(
    val maxDepth: Int,
    val gamma: Double,
    val lambda: Double,
    val learningRate: Double,
    val basePredictionAir: Double,
    val basePredictionWaktu: Double,
    val treesAir: List<SerializableTreeNode>,
    val treesWaktu: List<SerializableTreeNode>,
    val globalMinumPerJam: Map<Int, List<Double>>
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

data class TrainingEvaluationResults(
    val airMetrics: EvaluationMetrics,
    val waktuMetrics: EvaluationMetrics
)

class XGBoost(
    private val maxDepth: Int,
    private val gamma: Double,
    private val lambda: Double,
    private val learningRate: Double
) {
    var basePredictionAir = 0.0
    var basePredictionWaktu = 0.0
    val treesAir = mutableListOf<TreeNode>()
    val treesWaktu = mutableListOf<TreeNode>()

    private var globalMinumPerJam = mutableMapOf<Int, MutableList<Double>>()
    private val RAMADAN_START = LocalDate.of(2025, 2, 28)
    private val RAMADAN_END = LocalDate.of(2025, 3, 31)

    private val SARAPAN_RANGE = 6..9
    private val MAKAN_SIANG_RANGE = 11..13
    private val MAKAN_MALAM_RANGE = 17..20

    private val DECAY_WEIGHTS = 7
    private val DECAY_DIVIDER = 1

    private val features = listOf(
//        "ordinalDate",
//        "sinDayOfWeek", "cosDayOfWeek",
//        "sinTimeBucket", "cosTimeBucket",
        "sinTimeDayInteraction", "cosTimeDayInteraction",
//        "isPuasa",
//        "jamFreqZscore",
        "jamFreqZscoreDecay",
//        "puasaTimeInteraction",
//        "puasaDecayInteraction",
        "sinDayHourInteraction", "cosDayHourInteraction",
//        "sinHour", "cosHour",
//        "isWeekend",
//        "isMealTime",
//        "mealType"
    )

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
    ): TrainingEvaluationResults? {

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

        this.globalMinumPerJam = minumPerJam

        val entryCounts = minumPerJam.mapValues { it.value.size }

        val meanFreq = entryCounts.values.average()
        val stdFreq = entryCounts.values.map { (it - meanFreq).pow(2) }.average().let { sqrt(it) }

        val jamFreqZscore = entryCounts.mapValues { (it.value - meanFreq) / (stdFreq + 1e-6) }

        val decayWeights = List(DECAY_WEIGHTS) { exp(-it.toDouble() / DECAY_DIVIDER) }

        val lastDate = tanggal.maxOf { LocalDate.parse(it.substring(0, 10)) }

        val jamFreqWithDecay = mutableMapOf<Int, Double>().apply {
            tanggal.forEachIndexed { i, tgl ->
                val hariKe = ChronoUnit.DAYS.between(
                    LocalDate.parse(tgl.substring(0, 10)),
                    lastDate
                ).toInt()

                val weight = decayWeights.getOrElse(hariKe.coerceIn(0, DECAY_WEIGHTS - 1)) { 0.0 }
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

            val isPuasa = (
                    !localDate.isBefore(RAMADAN_START) && !localDate.isAfter(RAMADAN_END)
                    ) || (
                    jumlahWaktu[it] > 12 * 3600 && jumlahWaktu[it] != 0.0
                    )

            val puasaTimeInteraction: Double = if (isPuasa) {
                when (timeBucket) {
                    0 -> 0.7  // Sahur
                    3 -> 1.2  // Buka puasa
                    4 -> 0.9  // Tarawih
                    else -> 0.3
                }
            } else {
                0.0
            }

            val puasaDecayInteraction: Double = jamFreqZscoreDecay[hour]?.times((if (isPuasa) 1.5 else 0.8)) ?: 0.0

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
        var finalTrainingResults: TrainingEvaluationResults? = null

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

            if (iter == maxIterasi - 1) {
                val predictedAir = dataPoints.map { dp ->
                    basePredictionAir + treesAir.sumOf { tree ->
                        learningRate * predictTree(tree, dp)
                    }
                }

                val predictedWaktu = dataPoints.map { dp ->
                    basePredictionWaktu + treesWaktu.sumOf { tree ->
                        learningRate * predictTree(tree, dp)
                    }
                }

                val airEval = calculateMetrics(actualAir, predictedAir)
                val waktuEval = calculateMetrics(actualWaktu, predictedWaktu)
                finalTrainingResults = TrainingEvaluationResults(airEval, waktuEval)

//                println("Iterasi ${iter + 1} - Metrik Pelatihan | Air: R²=${airEval.r2} | Waktu: R²=${waktuEval.r2}")
//                println("            Air: MAE=${(airEval.mae)}, RMSE=${(airEval.rmse)}, SMAPE=${(airEval.smape)}")
//                println("            Waktu: MAE=${(waktuEval.mae)}, RMSE=${(waktuEval.rmse)}, SMAPE=${(waktuEval.smape)}")
            }

            val deltaAir = delta(residualsAir, dataPoints.map { dp -> predictTree(treeAir, dp) })
            val deltaWaktu = delta(residualsWaktu, dataPoints.map { dp -> predictTree(treeWaktu, dp) })

            if (deltaAir < epsilon && deltaWaktu < epsilon) {
                println("Konvergen pada iterasi $iter")
                return finalTrainingResults
            }
        }

        return finalTrainingResults
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

            val decayWeights = List(DECAY_WEIGHTS) { exp(-it.toDouble() / DECAY_DIVIDER) }
            val jamFreqWithDecay = mutableMapOf<Int, Double>().apply {
                globalMinumPerJam.forEach { (jam, records) ->
                    val totalDecay = records.mapIndexed { i, _ ->
                        decayWeights.getOrElse(i.coerceAtMost(DECAY_WEIGHTS - 1)) { 0.0 }
                    }.sum()
                    this[jam] = totalDecay
                }
            }

            val meanDecay = jamFreqWithDecay.values.average()
            val stdDecay = jamFreqWithDecay.values.std()
            val jamFreqZscoreDecay = jamFreqWithDecay[hour]?.let {
                (it - meanDecay) / (stdDecay + 1e-6)
            } ?: 0.0

            val isPuasa = false // Perlu disesuaikan jika ingin memperhitungkan puasa saat prediksi.

            val puasaTimeInteraction: Double = if (isPuasa) {
                when (timeBucket) {
                    0 -> 0.7  // Sahur
                    3 -> 1.2  // Buka puasa
                    4 -> 0.9  // Tarawih
                    else -> 0.3
                }
            } else {
                0.0
            }

            val puasaDecayInteraction: Double = jamFreqZscoreDecay.times((if (isPuasa) 1.5 else 0.8))

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
                sinTimeBucket = sin(2 * Math.PI * timeBucket / 5),
                cosTimeBucket = cos(2 * Math.PI * timeBucket / 5),
                ordinalDate = localDate.toEpochDay().toInt(),
                sinTimeDayInteraction = sinTimeDayInteraction,
                cosTimeDayInteraction = cosTimeDayInteraction,
                isPuasa = isPuasa,
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

            currentTime += predWaktu
        }

        return predictionsAir.toDoubleArray() to predictionsTime.toTypedArray()
    }

    private fun delta(old: List<Double>, predicted: List<Double>): Double {
        return old.zip(predicted).map { abs(it.first - it.second) }.average()
    }

    fun evaluasi(
        actual: LinkedHashMap<String, Double>,
        predicted: LinkedHashMap<String, Double>
    ): EvaluationMetrics {
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

//        println("max_depth: $maxDepth | gamma: $gamma | lambda: $lambda | learning_rate: $learningRate | user: $idUser")
//        println("MAE: $mae | RMSE: $rmse | SMAPE: $smape | R2: $r2")

        return EvaluationMetrics(smape, mae, rmse, r2)
    }

    data class FeatureImportanceInfo(
        val gainImportance: Double,
        val splitFrequency: Int
    )

    fun calculateFeatureImportanceWithFrequency(): Map<String, FeatureImportanceInfo> {
        val featureImportance = mutableMapOf<String, Double>()
        val featureFrequency = mutableMapOf<String, Int>()

        // Hitung importance dan frequency untuk treesAir
        treesAir.forEach { tree ->
            calculateTreeFeatureImportanceWithFrequency(tree, featureImportance, featureFrequency)
        }

        // Hitung importance dan frequency untuk treesWaktu
        treesWaktu.forEach { tree ->
            calculateTreeFeatureImportanceWithFrequency(tree, featureImportance, featureFrequency)
        }

        // Normalisasi nilai importance
        val totalImportance = featureImportance.values.sum()
        val result = mutableMapOf<String, FeatureImportanceInfo>()

        if (totalImportance > 0) {
            featureImportance.forEach { (feature, importance) ->
                val normalizedImportance = importance / totalImportance
                val frequency = featureFrequency.getOrDefault(feature, 0)
                result[feature] = FeatureImportanceInfo(normalizedImportance, frequency)
            }
        }

        return result.toMap()
    }

    private fun calculateTreeFeatureImportanceWithFrequency(
        node: TreeNode,
        importanceMap: MutableMap<String, Double>,
        frequencyMap: MutableMap<String, Int>
    ) {
        if (!node.isLeaf && node.feature != null && node.gain != null) {
            // Tambahkan gain ke feature importance
            importanceMap[node.feature] = importanceMap.getOrDefault(node.feature, 0.0) + node.gain

            // Tambahkan frekuensi pemilihan fitur
            frequencyMap[node.feature] = frequencyMap.getOrDefault(node.feature, 0) + 1

            // Rekursif ke child nodes
            node.left?.let { calculateTreeFeatureImportanceWithFrequency(it, importanceMap, frequencyMap) }
            node.right?.let { calculateTreeFeatureImportanceWithFrequency(it, importanceMap, frequencyMap) }
        }
    }

    fun printFeatureImportanceWithFrequency() {
        val importance = calculateFeatureImportanceWithFrequency()

        // Urutkan dari yang terpenting berdasarkan gain
        val sortedImportance = importance.entries.sortedByDescending { it.value.gainImportance }

        // Format output
        val output = StringBuilder("Feature Importance (Gain | Frequency):\n")
        sortedImportance.forEach { (feature, info) ->
            output.append("${"%.2f".format(info.gainImportance * 100)}% | ${info.splitFrequency} splits - $feature\n")
        }

        println(output.toString())
    }

    fun toSerializableModel(): SerializableXGBoostModel {
        return SerializableXGBoostModel(
            maxDepth = this.maxDepth,
            gamma = this.gamma,
            lambda = this.lambda,
            learningRate = this.learningRate,
            basePredictionAir = this.basePredictionAir,
            basePredictionWaktu = this.basePredictionWaktu,
            treesAir = this.treesAir.map { convertTreeNodeToSerializable(it) },
            treesWaktu = this.treesWaktu.map { convertTreeNodeToSerializable(it) },
            globalMinumPerJam = this.globalMinumPerJam.mapValues { it.value.toList() }
        )
    }

    private fun convertTreeNodeToSerializable(node: TreeNode): SerializableTreeNode {
        return SerializableTreeNode(
            residuals = node.residuals,
            similarity = node.similarity,
            isLeaf = node.isLeaf,
            feature = node.feature,
            thresholdNumeric = node.thresholdNumeric,
            gain = node.gain,
            left = node.left?.let { convertTreeNodeToSerializable(it) },
            right = node.right?.let { convertTreeNodeToSerializable(it) }
        )
    }

    private fun convertSerializableToTreeNode(sNode: SerializableTreeNode): TreeNode {
        return TreeNode(
            residuals = sNode.residuals,
            similarity = sNode.similarity,
            isLeaf = sNode.isLeaf,
            feature = sNode.feature,
            thresholdNumeric = sNode.thresholdNumeric,
            gain = sNode.gain,
            left = sNode.left?.let { convertSerializableToTreeNode(it) },
            right = sNode.right?.let { convertSerializableToTreeNode(it) }
        )
    }

    // Companion object untuk membuat dari serializable
    companion object {
        fun fromSerializableModel(serializableModel: SerializableXGBoostModel): XGBoost {
            val xgboost = XGBoost(
                maxDepth = serializableModel.maxDepth,
                gamma = serializableModel.gamma,
                lambda = serializableModel.lambda,
                learningRate = serializableModel.learningRate
            )
            xgboost.basePredictionAir = serializableModel.basePredictionAir
            xgboost.basePredictionWaktu = serializableModel.basePredictionWaktu
            xgboost.treesAir.addAll(serializableModel.treesAir.map { xgboost.convertSerializableToTreeNode(it) })
            xgboost.treesWaktu.addAll(serializableModel.treesWaktu.map { xgboost.convertSerializableToTreeNode(it) })
            xgboost.globalMinumPerJam.putAll(serializableModel.globalMinumPerJam.mapValues { it.value.toMutableList() })
            return xgboost
        }
    }
}
