package com.example.stationbottle.worker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.stationbottle.R
import com.example.stationbottle.data.UserDataStore.getUser
import com.example.stationbottle.data.fetchBMKGWeatherData
import com.example.stationbottle.models.SensorViewModel
import com.example.stationbottle.models.WilayahViewModel
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @SuppressLint("DefaultLocale")
    override suspend fun doWork(): Result {
        return try {
            val isCustomReminder = inputData.getBoolean("IS_CUSTOM_REMINDER", false)
            if (isCustomReminder) {
                handleCustomReminder(inputData)
            } else {
                handleScheduledHydrationCheck()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    /**
     * Menangani pembuatan notifikasi untuk pengingat minum kustom dari halaman Insight.
     */
    private suspend fun handleCustomReminder(data: Data): Result {
        val user = getUser(applicationContext).first() ?: return Result.failure()
        val recommendedVolume = data.getInt("RECOMMENDED_VOLUME", 0)

        // Hitung ulang data terkini untuk memastikan notifikasi akurat
        val hasilPred = calculatePrediction(
            context = applicationContext,
            user = user,
            waktuMulai = user.waktu_mulai!!,
            waktuSelesai = user.waktu_selesai!!,
            todayDate = LocalDate.now().toString()
        )
        val currentIntake = hasilPred.todayAktual
        val dailyGoal = user.daily_goal ?: 0.0

        // Ambil data suhu terkini
        val sensorViewModel = SensorViewModel()
        val wilayahViewModel = WilayahViewModel()
        val lastSuhu = sensorViewModel.getLastSuhuByDeviceId(user.device_id.toString())
        val suhuIndoor = lastSuhu?.data?.temperature

        val kodeLengkap = if (!user.id_kelurahan.isNullOrEmpty()) {
            wilayahViewModel.getKodeLengkap(user.id_kelurahan.toInt()).kode_kelurahan_lengkap
        } else ""
        val weatherResponse = fetchBMKGWeatherData(kodeLengkap)
        val suhuOutdoor = weatherResponse?.data?.firstOrNull()?.cuaca?.firstOrNull()?.firstOrNull()?.t?.toDouble()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "hydration_notifications_custom"
        val channelName = "Pengingat Minum Terjadwal"
        notificationManager.createNotificationChannel(NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH))

        val title = "Saatnya Minum, ${user.name}!"
        var message = "Sudah waktunya anda scan minum anda sebanyak ${recommendedVolume}mL! " +
                "Saat ini Anda baru minum ${currentIntake.toInt()}mL dan masih kurang " +
                "${(dailyGoal - currentIntake).roundToInt()}mL dari target harian (${dailyGoal.roundToInt()}mL)."

        val indoorTempStr = if (suhuIndoor != null) "${suhuIndoor.roundToInt()}°C" else "tidak tersedia"
        val outdoorTempStr = if (suhuOutdoor != null) "${suhuOutdoor.roundToInt()}°C" else "tidak tersedia"
        message += "\n\nSuhu ruangan: $indoorTempStr, Suhu luar: $outdoorTempStr."

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.outline_water_drop_24) // Pastikan ikon ini ada di drawable
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification) // ID unik untuk setiap notifikasi
        return Result.success()
    }

    /**
     * Menangani logika notifikasi reguler yang sudah ada sebelumnya.
     */
    private suspend fun handleScheduledHydrationCheck(): Result {
        // [ ... SELURUH KODE ASLI DARI FUNGSI doWork() SEBELUMNYA DITEMPATKAN DI SINI ... ]
        // Kode asli dimulai dari: val user = getUser(applicationContext).first()!!
        // ... hingga return Result.success()
        val user = getUser(applicationContext).first()!!
        val hasilPred = calculatePrediction(
            context = applicationContext,
            user = user,
            waktuMulai = user.waktu_mulai!!,
            waktuSelesai = user.waktu_selesai!!,
            todayDate = LocalDate.now().toString()
        )

        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "hydration_notifications"
        val channelName = "Hydration Notifications"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, channelName, importance)
        notificationManager.createNotificationChannel(channel)

        val result = user.daily_goal!! - hasilPred.todayPrediksi
        val status = hasilPred.todayPrediksi >= user.daily_goal

        val title = "Pengingat Hidrasi untuk ${user.name}"
        val message =
            if (hasilPred.todayAktual == 0.0) {
                "Jangan lupa minum hari ini!"
            } else if (hasilPred.todayPrediksi == 0.0){
                "Anda baru minum ${hasilPred.todayAktual.toInt()} mL dari ${user.daily_goal.toInt()} mL, anda kurang minum sebanyak " +
                        "${(user.daily_goal - hasilPred.todayAktual).toInt()} ml"
            } else if (user.daily_goal > hasilPred.todayPrediksi) {
                "Ayo Minum! \n" +
                        "Menurut Prediksi AI, anda kurang minum sebanyak ${result.toInt()} mL, " +
                        "untuk saat ini anda baru minum ${hasilPred.todayAktual.toInt()} mL dari " +
                        "kebutuhan anda ${user.daily_goal.toInt()} mL"
            } else {
                "Kerja Bagus! \n" +
                        "Menurut Prediksi AI Anda akan mencapai target hidrasi harian Anda yaitu " +
                        "${user.daily_goal.toInt()} mL, anda baru mencapai ${hasilPred.todayAktual.toInt()} mL!" +
                        "Keep up the good work!!"
            }

        val iconRes = if (status) {
            android.R.drawable.ic_dialog_info
        } else {
            android.R.drawable.ic_dialog_alert
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)

        val workManager = WorkManager.getInstance(applicationContext)

        workManager.cancelAllWorkByTag("hydration_notifications")

        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        val seconds = user.frekuensi_notifikasi?.takeIf { it != 0 } ?: 3600

        val today = LocalDate.now()
        val sekarang = LocalTime.now()
        val timeMulai = LocalTime.parse(user.waktu_mulai, timeFormatter)
        val timeSelesai = LocalTime.parse(user.waktu_selesai, timeFormatter)

        var fromDateTime = LocalDateTime.of(
            when {
                timeMulai > timeSelesai && sekarang < timeSelesai -> today.minusDays(1)
                timeMulai > timeSelesai -> today
                else -> today
            },
            timeMulai
        )

        var toDateTime = LocalDateTime.of(
            when {
                timeMulai > timeSelesai && sekarang < timeSelesai -> today
                timeMulai > timeSelesai -> today.plusDays(1)
                else -> today
            },
            timeSelesai
        )

        val now = LocalDateTime.now()
        val timeList = mutableListOf<String>()
        var current = fromDateTime
        var selisih: Long = 0L

        while (!current.isAfter(toDateTime)) {
            timeList.add(current.format(timeFormatter))
            if (current.isAfter(now)) {
                selisih = Duration.between(now, current).toMillis()
                break
            }

            current = current.plusSeconds(seconds.toLong())
        }

        if(selisih > 0L){
            val initialWorkRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay(selisih - 1000L, TimeUnit.MILLISECONDS)
                .addTag("hydration_notifications")
                .build()

            WorkManager.getInstance(applicationContext).enqueue(initialWorkRequest)
        }
        return Result.success()
    }
}