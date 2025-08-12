package com.example.stationbottle.worker

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

data class RecommendationSchedule(
    val times: List<LocalTime>,
    val volumePerSession: Int
)

fun scheduleDrinkingReminders(
    context: Context,
    schedule: RecommendationSchedule,
) {
    val workManager = WorkManager.getInstance(context)
    val now = LocalDateTime.now()

    // Batalkan semua pengingat kustom yang dijadwalkan sebelumnya untuk menghindari tumpang tindih
    workManager.cancelAllWorkByTag("drinking_reminder_plan")

    schedule.times.forEach { time ->
        // Hanya jadwalkan untuk waktu di masa depan (dengan buffer 1 menit)
        if (time.isAfter(now.toLocalTime().plusMinutes(1))) {
            var scheduleDateTime = now.with(time)

            // Jika waktu yang dijadwalkan hari ini sudah lewat, atur untuk hari berikutnya
            if (scheduleDateTime.isBefore(now)) {
                scheduleDateTime = scheduleDateTime.plusDays(1)
            }

            val delay = Duration.between(now, scheduleDateTime).toMillis()

            if (delay > 0) {
                // Siapkan data input untuk Worker
                val inputData = workDataOf(
                    "RECOMMENDED_VOLUME" to schedule.volumePerSession,
                    "IS_CUSTOM_REMINDER" to true // Flag untuk membedakan dari notifikasi reguler
                )

                val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(inputData)
                    .addTag("drinking_reminder_plan") // Tag untuk membatalkan grup notifikasi ini
                    .build()

                workManager.enqueue(workRequest)
            }
        }
    }
}