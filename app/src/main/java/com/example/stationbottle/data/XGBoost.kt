package com.example.stationbottle.data

import java.text.DecimalFormat
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class XGBoost {
    private val formatter = DecimalFormat("#.###")
    private val lambda = 1.0
    private val gamma = 50.0
    private val learningRate = 0.5
    private var prediksiAir = 0.0
    private var prediksiWaktu = 0.0
    private val treesAir = mutableListOf<TreeNode>()
    private val treesWaktu = mutableListOf<TreeNode>()

    fun latihModel(tanggal: Array<String>, waktu: Array<String>, jumlahAir: DoubleArray, maxIterasi: Int) {
        val waktuDalamDetik = waktu.map { LocalTime.parse(it).toSecondOfDay().toDouble() }
        val jumlahWaktu = DoubleArray(waktuDalamDetik.size)

        val dataPoints = waktuDalamDetik.mapIndexed { index, waktu ->
            val perbedaanWaktu = if (index == 0 || waktu - waktuDalamDetik[index - 1] < 0) {
                0.0
            } else {
                waktu - waktuDalamDetik[index - 1]
            }
            jumlahWaktu[index] = perbedaanWaktu
            DataPoint(tanggal[index], jumlahAir[index], waktu, perbedaanWaktu)
        }

        prediksiAir = jumlahAir.average()
        var residualsAir = jumlahAir.map {
            formatter.format(it - prediksiAir).toDouble()
        }

        prediksiWaktu = jumlahWaktu.average()
        var residualsWaktu = jumlahWaktu.map {
            formatter.format(it - prediksiWaktu).toDouble()
        }

        val epsilon = 1e-6
        for (iterasi in 1..maxIterasi) {
            val rootAir = buatPohon(dataPoints.map { DataPoint(it.tanggal, it.minum, residualsAir[dataPoints.indexOf(it)], it.timeDifference) }, "Root Air")
            treesAir.add(rootAir)

            val rootWaktu = buatPohon(dataPoints.map { DataPoint(it.tanggal, it.minum, residualsWaktu[dataPoints.indexOf(it)], it.timeDifference) }, "Root Waktu")
            treesWaktu.add(rootWaktu)

            val newResidualsAir = dataPoints.mapIndexed { index, point ->
                val outputValue = hitungOutputValue(rootAir, point.tanggal)
                residualsAir[index] - learningRate * outputValue
            }

            val newResidualsWaktu = dataPoints.mapIndexed { index, point ->
                val outputValue = hitungOutputValue(rootWaktu, point.tanggal)
                residualsWaktu[index] - learningRate * outputValue
            }

            val deltaAir = newResidualsAir.zip(residualsAir).map { (new, old) -> abs(new - old) }.average()
            val deltaWaktu = newResidualsWaktu.zip(residualsWaktu).map { (new, old) -> abs(new - old) }.average()

            residualsAir = newResidualsAir
            residualsWaktu = newResidualsWaktu

            if (deltaAir < epsilon && deltaWaktu < epsilon) {
                println("Pelatihan berhenti pada iterasi ke-$iterasi karena perubahan residual sudah kecil.")
                break
            }
        }
    }

    private fun buatPohon(data: List<DataPoint>, side: String): TreeNode {
        val rootSimilarity = hitungSimilarity(data.map { it.residual })
        val (bestThreshold, bestGain) = temukanThresholdTerbaik(data)

        if (bestGain - gamma < 0) {
            return TreeNode(
                residuals = data.map { it.residual },
                similarity = rootSimilarity,
                isLeaf = true
            )
        }

        val leftData = data.filter {  it.tanggal < bestThreshold }
        val rightData = data.filter { it.tanggal >= bestThreshold }

        return TreeNode(
            residuals = data.map { it.residual },
            similarity = rootSimilarity,
            threshold = bestThreshold,
            gain = bestGain,
            left = buatPohon(leftData, "Kiri"),
            right = buatPohon(rightData, "Kanan")
        )
    }

    private fun hitungSimilarity(residuals: List<Double>): Double {
        val sumResiduals = formatter.format(residuals.sum()).toDouble()
        val similarity = (sumResiduals * sumResiduals) / (residuals.size + lambda)
        return similarity
    }

    private fun hitungGain(leftResiduals: List<Double>, rightResiduals: List<Double>, rootSimilarity: Double): Double {
        val leftSimilarity = hitungSimilarity(leftResiduals)
        val rightSimilarity = hitungSimilarity(rightResiduals)
        return leftSimilarity + rightSimilarity - rootSimilarity
    }

    private fun temukanThresholdTerbaik(data: List<DataPoint>): Pair<String, Double> {
        var thresholdTerbaik = ""
        var gainTerbaik = Double.NEGATIVE_INFINITY

        for (i in 1 until data.size) {

            val threshold = data[i].tanggal.toString()

            val leftResiduals = data.filter { it.tanggal < data[i].tanggal }.map { it.residual }
            val rightResiduals = data.filter { it.tanggal >= data[i].tanggal }.map { it.residual }

            val rootSimilarity = hitungSimilarity(data.map { it.residual })
            val gain = hitungGain(leftResiduals, rightResiduals, rootSimilarity)

            if (gain > gainTerbaik) {
                thresholdTerbaik = threshold
                gainTerbaik = gain
            }
        }
        return thresholdTerbaik to gainTerbaik
    }

    private fun hitungOutputValue(pohon: TreeNode, tanggal: String): Double {
        if (pohon.isLeaf) {
            return pohon.residuals.sum() / (pohon.residuals.size + lambda)
        }
        return if (tanggal < pohon.threshold!!) {
            hitungOutputValue(pohon.left!!, tanggal)
        } else {
            hitungOutputValue(pohon.right!!, tanggal)
        }
    }

    fun prediksi(waktuTerakhir: String, batasWaktu: String, tanggalTerakhir: String): Pair<DoubleArray, Array<Double>>? {
        if (treesAir.isEmpty() || treesWaktu.isEmpty()) {
            println("Model has not been trained. Please train before prediction.")
            return null
        }

        val waktuTerakhirDetik = LocalTime.parse(waktuTerakhir).toSecondOfDay().toDouble()
        val batasWaktuDetik = LocalTime.parse(batasWaktu).toSecondOfDay().toDouble()

        println(treesAir.last())
        println(treesWaktu.last())

        val predAir = prediksiAir + learningRate * hitungOutputValue(treesAir.last(), tanggalTerakhir)
        val predWaktu = prediksiWaktu + learningRate * hitungOutputValue(treesWaktu.last(), tanggalTerakhir)

        if (predWaktu <= 0) {
            println("Invalid predicted time interval (predWaktu): $predWaktu")
            return null
        }

        val totalInterval = ((batasWaktuDetik - waktuTerakhirDetik) / predWaktu).toInt()

        val waktuArray = Array(totalInterval) { index ->
            val waktuDetik = waktuTerakhirDetik + (index + 1) * predWaktu
            val waktu = LocalTime.ofSecondOfDay(waktuDetik.toLong()).toString()
            waktu
        }

        val prediksiAir = waktuArray.map { predAir }.toDoubleArray()
        val prediksiWaktu = waktuArray.map { predWaktu }.toTypedArray()

        return prediksiAir to prediksiWaktu
    }


    fun evaluasi(actual: DoubleArray, predicted: DoubleArray?): EvaluationMetrics {
        if (predicted == null) return EvaluationMetrics(Double.NaN, Double.NaN, Double.NaN, Double.NaN)
        val n = actual.size

        val smape = actual.zip(predicted).sumOf { (a, p) ->
            val diff = abs(a - p)
            val denominator = (abs(a) + abs(p)) / 2
            if (denominator != 0.0) diff / denominator else 0.0
        } / n

        val mae = actual.zip(predicted).sumOf { (a, p) -> abs(a - p) } / n

        val rmse = sqrt(actual.zip(predicted).sumOf { (a, p) -> (a - p).pow(2) } / n)

        val meanActual = actual.average()
        val ssTotal = actual.sumOf { (it - meanActual).pow(2) }
        val ssResidual = actual.zip(predicted).sumOf { (a, p) -> (a - p).pow(2) }
        val r2 = 1 - ssResidual / ssTotal

        return EvaluationMetrics(smape, mae, rmse, r2)
    }
}


data class TreeNode(
    val residuals: List<Double>,
    val similarity: Double,
    val threshold: String? = null,
    val gain: Double? = null,
    val left: TreeNode? = null,
    val right: TreeNode? = null,
    val isLeaf: Boolean = false
)

data class DataPoint(
    val tanggal: String,
    val minum: Double,
//    val waktu: Double,
    val residual: Double,
    val timeDifference: Double
)

data class EvaluationMetrics(
    val mape: Double,
    val mae: Double,
    val rmse: Double,
    val r2: Double
)
