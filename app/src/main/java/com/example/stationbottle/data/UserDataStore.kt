package com.example.stationbottle.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.userDataStore by preferencesDataStore(name = "user_data_store")

object UserDataStore {
    private val TOKEN_KEY = stringPreferencesKey("user_token")
    private val ID_KEY = intPreferencesKey("user_id")
    private val EMAIL_KEY = stringPreferencesKey("user_email")
    private val NAME_KEY = stringPreferencesKey("user_name")
    private val DATEOFBIRTH_KEY = stringPreferencesKey("user_date_of_birth")
    private val WEIGHT_KEY = doublePreferencesKey("user_weight")
    private val HEIGHT_KEY = doublePreferencesKey("user_height")
    private val GENDER_KEY = stringPreferencesKey("user_gender")
    private val PREGNANCYDATE_KEY = stringPreferencesKey("user_pregnancy_date")
    private val BREASTFEEDINGDATE_KEY = stringPreferencesKey("user_breastfeeding_date")
    private val DAILY_GOAL_KEY = doublePreferencesKey("user_daily_goal")
    private val WAKTU_MULAI_KEY = stringPreferencesKey("user_waktu_mulai")
    private val WAKTU_SELESAI_KEY = stringPreferencesKey("user_waktu_selesai")
    private val RFID_TAG_KEY = stringPreferencesKey("user_rfid_tag")
    private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
    private val MINUM_AKHIR_KEY = doublePreferencesKey("minum_akhir")
    private val WAKTU_AKHIR_KEY = stringPreferencesKey("waktu_akhir")
    private val PREDIKSI_MINUM_KEY = stringPreferencesKey("prediksi_minum")
    private val PREDIKSI_WAKTU_KEY = stringPreferencesKey("prediksi_waktu")
    private val WAKTU_PREDIKSI_MULAI_KEY = stringPreferencesKey("waktu_prediksi_mulai")
    private val WAKTU_PREDIKSI_SELESAI_KEY = stringPreferencesKey("waktu_prediksi_selesai")
    private val TOTAL_PREDIKSI_KEY = doublePreferencesKey("total_prediksi")
    private val TOTAL_AKTUAL_KEY = doublePreferencesKey("total_aktual")
    private val DATE_PREDIKSI_KEY = stringPreferencesKey("date_prediksi")

    suspend fun saveDarkMode(context: Context, isDarkMode: Boolean) {
        context.userDataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = isDarkMode
        }
    }

    fun getDarkMode(context: Context): Flow<Boolean> {
        return context.userDataStore.data
            .map { preferences ->
                preferences[DARK_MODE_KEY] ?: false
            }
    }

    suspend fun savePrediksi(context: Context, userPrediksi: UserPrediksi) {
        val gson = Gson()
        context.userDataStore.edit { preferences ->
            preferences[MINUM_AKHIR_KEY] = userPrediksi.minumAkhir ?: 0.0
            preferences[WAKTU_AKHIR_KEY] = userPrediksi.waktuAkhir
            preferences[PREDIKSI_MINUM_KEY] = gson.toJson(userPrediksi.prediksiMinum)
            preferences[PREDIKSI_WAKTU_KEY] = gson.toJson(userPrediksi.prediksiWaktu)
            preferences[WAKTU_PREDIKSI_MULAI_KEY] = userPrediksi.waktuPredMulai
            preferences[WAKTU_PREDIKSI_SELESAI_KEY] = userPrediksi.waktuPredSelesai
            preferences[TOTAL_PREDIKSI_KEY] = userPrediksi.totalPrediksi ?: 0.0
            preferences[TOTAL_AKTUAL_KEY] = userPrediksi.totalAktual ?: 0.0
            preferences[DATE_PREDIKSI_KEY] = userPrediksi.datePrediksi
        }
    }

    fun getPrediksi(context: Context): Flow<UserPrediksi> {
        return context.userDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    println(exception)
                } else {
                    println(exception)
                    throw exception
                }
            }
            .map { preferences ->
                val gson = Gson()

                val waktuAkhir = preferences[WAKTU_AKHIR_KEY]
                val minumAkhir = preferences[MINUM_AKHIR_KEY]
                val prediksiWaktuJson = preferences[PREDIKSI_WAKTU_KEY]
                val prediksiMinumJson = preferences[PREDIKSI_MINUM_KEY]

                val prediksiWaktu: Array<Double> = try {
                    if (prediksiWaktuJson != null) {
                        gson.fromJson(prediksiWaktuJson, Array<Double>::class.java)
                    } else {
                        emptyArray()
                    }
                } catch (e: Exception) {
                    println(e)
                    emptyArray()
                }

                val prediksiMinum: DoubleArray = try {
                    if (prediksiMinumJson != null) {
                        gson.fromJson(prediksiMinumJson, DoubleArray::class.java)
                    } else {
                        doubleArrayOf()
                    }
                } catch (e: Exception) {
                    println(e)
                    doubleArrayOf()
                }

                val waktuPredMulai = preferences[WAKTU_PREDIKSI_MULAI_KEY]
                val waktuPredSelesai = preferences[WAKTU_PREDIKSI_SELESAI_KEY]
                val totalPrediksi = preferences[TOTAL_PREDIKSI_KEY]
                val totalAktual = preferences[TOTAL_AKTUAL_KEY]
                val datePrediksi = preferences[DATE_PREDIKSI_KEY]

                UserPrediksi(
                    waktuAkhir = waktuAkhir.toString(),
                    minumAkhir = minumAkhir,
                    prediksiWaktu = prediksiWaktu,
                    prediksiMinum = prediksiMinum,
                    waktuPredMulai = waktuPredMulai.toString(),
                    waktuPredSelesai = waktuPredSelesai.toString(),
                    totalPrediksi = totalPrediksi,
                    totalAktual = totalAktual,
                    datePrediksi = datePrediksi.toString()
                )
            }
    }

    // Fungsi untuk menyimpan token
    suspend fun saveUser(context: Context, user: User) {
        context.userDataStore.edit { preferences ->
            preferences[TOKEN_KEY] = user.token
            preferences[ID_KEY] = user.id
            preferences[EMAIL_KEY] = user.email
            preferences[NAME_KEY] = user.name ?: ""
            preferences[DATEOFBIRTH_KEY] = user.date_of_birth ?: ""
            preferences[WEIGHT_KEY] = user.weight ?: 0.0
            preferences[HEIGHT_KEY] = user.height ?: 0.0
            preferences[GENDER_KEY] = user.gender ?: ""
            preferences[PREGNANCYDATE_KEY] = user.pregnancy_date ?: ""
            preferences[BREASTFEEDINGDATE_KEY] = user.breastfeeding_date ?: ""
            preferences[DAILY_GOAL_KEY] = user.daily_goal ?: 0.0
            preferences[WAKTU_MULAI_KEY] = user.waktu_mulai ?: ""
            preferences[WAKTU_SELESAI_KEY] = user.waktu_selesai ?: ""
            preferences[RFID_TAG_KEY] = user.rfid_tag ?: ""
        }
    }

    fun getUser(context: Context): Flow<User?> {
        return context.userDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    // Handle error, for example, logging
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val token = preferences[TOKEN_KEY]
                val id = preferences[ID_KEY]
                val email = preferences[EMAIL_KEY]
                val name = preferences[NAME_KEY] ?: ""
                val date_of_birth = preferences[DATEOFBIRTH_KEY] ?: ""
                val weight = preferences[WEIGHT_KEY] ?: 0.0
                val height = preferences[HEIGHT_KEY] ?: 0.0
                val gender = preferences[GENDER_KEY] ?: ""
                val pregnancy_date = preferences[PREGNANCYDATE_KEY] ?: ""
                val breastfeeding_date = preferences[BREASTFEEDINGDATE_KEY] ?: ""
                val daily_goal = preferences[DAILY_GOAL_KEY] ?: 0.0
                val waktu_mulai = preferences[WAKTU_MULAI_KEY] ?: ""
                val waktu_selesai = preferences[WAKTU_SELESAI_KEY] ?: ""
                val rfid_tag = preferences[RFID_TAG_KEY] ?: ""

                if (token != null && id != null && email != null) {
                    User(
                        token = token,
                        id = id,
                        email = email,
                        name = name,
                        date_of_birth = date_of_birth,
                        weight = weight,
                        height = height,
                        gender = gender,
                        pregnancy_date = pregnancy_date,
                        breastfeeding_date = breastfeeding_date,
                        daily_goal = daily_goal,
                        waktu_mulai = waktu_mulai,
                        waktu_selesai = waktu_selesai,
                        rfid_tag = rfid_tag
                    )
                } else {
                    null
                }
            }
    }

    suspend fun clearUser(context: Context) {
        context.userDataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
            preferences.remove(ID_KEY)
            preferences.remove(EMAIL_KEY)
            preferences.remove(NAME_KEY)
            preferences.remove(DATEOFBIRTH_KEY)
            preferences.remove(WEIGHT_KEY)
            preferences.remove(HEIGHT_KEY)
            preferences.remove(GENDER_KEY)
            preferences.remove(PREGNANCYDATE_KEY)
            preferences.remove(BREASTFEEDINGDATE_KEY)
            preferences.remove(DAILY_GOAL_KEY)
            preferences.remove(WAKTU_MULAI_KEY)
            preferences.remove(WAKTU_SELESAI_KEY)
            preferences.remove(RFID_TAG_KEY)
            preferences.remove(MINUM_AKHIR_KEY)
            preferences.remove(WAKTU_AKHIR_KEY)
            preferences.remove(PREDIKSI_MINUM_KEY)
            preferences.remove(PREDIKSI_WAKTU_KEY)
            preferences.remove(WAKTU_PREDIKSI_MULAI_KEY)
            preferences.remove(WAKTU_PREDIKSI_SELESAI_KEY)
            preferences.remove(TOTAL_PREDIKSI_KEY)
            preferences.remove(TOTAL_AKTUAL_KEY)
        }
    }
}
