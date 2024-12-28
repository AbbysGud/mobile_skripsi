package com.example.stationbottle.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.stationbottle.models.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// Membuat DataStore instance
val Context.userDataStore by preferencesDataStore(name = "user_data_store")

object UserDataStore {
    private val TOKEN_KEY = stringPreferencesKey("user_token")
    private val ID_KEY = intPreferencesKey("user_id")
    private val EMAIL_KEY = stringPreferencesKey("user_email")
    private val NAME_KEY = stringPreferencesKey("user_name")
    private val AGE_KEY = intPreferencesKey("user_age")
    private val WEIGHT_KEY = floatPreferencesKey("user_weight")
    private val DAILY_GOAL_KEY = floatPreferencesKey("user_daily_goal")
    private val RFID_TAG_KEY = stringPreferencesKey("user_rfid_tag")

    // Fungsi untuk menyimpan token
    suspend fun saveUser(context: Context, user: User) {
        context.userDataStore.edit { preferences ->
            preferences[TOKEN_KEY] = user.token
            preferences[ID_KEY] = user.id
            preferences[EMAIL_KEY] = user.email
            // Menyimpan nilai hanya jika tidak null
            user.name?.let { preferences[NAME_KEY] = it }
            user.age?.let { preferences[AGE_KEY] = it }
            user.weight?.let { preferences[WEIGHT_KEY] = it }
            user.dailyGoal?.let { preferences[DAILY_GOAL_KEY] = it }
            user.rfidTag?.let { preferences[RFID_TAG_KEY] = it }
        }
    }

    // Fungsi untuk mengambil token
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
                val age = preferences[AGE_KEY] ?: 0
                val weight = preferences[WEIGHT_KEY] ?: 0f
                val dailyGoal = preferences[DAILY_GOAL_KEY] ?: 0f
                val rfidTag = preferences[RFID_TAG_KEY] ?: ""

                // Pastikan semua data tersedia untuk membuat objek User
                if (token != null && id != null && email != null) {
                    User(
                        token = token,
                        id = id,
                        email = email,
                        name = name,
                        age = age,
                        weight = weight,
                        dailyGoal = dailyGoal,
                        rfidTag = rfidTag
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
            preferences.remove(AGE_KEY)
            preferences.remove(WEIGHT_KEY)
            preferences.remove(DAILY_GOAL_KEY)
            preferences.remove(RFID_TAG_KEY)
        }
    }
}
