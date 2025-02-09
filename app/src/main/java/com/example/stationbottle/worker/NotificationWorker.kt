package com.example.stationbottle.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.stationbottle.data.UserDataStore.getUser
import kotlinx.coroutines.flow.first


class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val user = getUser(applicationContext).first()

        val hasilPred = calculatePrediction(
            context = applicationContext,
            userId = user?.id!!,
            waktuMulai = user.waktu_mulai!!,
            waktuSelesai = user.waktu_selesai!!
        )

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "hydration_notifications"
        val channelName = "Hydration Notifications"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, channelName, importance)
        notificationManager.createNotificationChannel(channel)

        val result = user.daily_goal!! - hasilPred.todayPrediksi
        val formattedResult = String.format("%.1f", result).toDouble()

        val title = "Prediksi Hidrasi"
        val message =
        if (hasilPred.todayAktual == 0.0) {
            "Mulai Hari dengan minum"
        } else if (user.daily_goal > hasilPred.todayPrediksi) {
            "Menurut Prediksi AI, ${hasilPred.todayPrediksi}/${user.daily_goal} anda kurang sebanyak " +
                    "$formattedResult mL, untuk saat ini anda baru minum ${hasilPred.todayAktual}"
        } else {
            "Selamat! Menurut Prediksi AI Anda akan mencapai target hidrasi harian Anda yaitu " +
                    "${hasilPred.todayPrediksi} mL/${user.daily_goal} mL, anda baru mencapai " +
                    "${hasilPred.todayAktual}!"
        }
        val status = hasilPred.todayPrediksi >= user.daily_goal

        println("NOTIFIKASI AKTUAL: ${hasilPred.todayAktual}")
        println("NOTIFIKASI PREDIKSI: ${hasilPred.todayPrediksi}")

        val iconRes = if (status) {
            android.R.drawable.ic_dialog_info
        } else {
            android.R.drawable.ic_dialog_alert
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)

        return Result.success()
    }
}