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
    private val DATEOFBIRTH_KEY = stringPreferencesKey("user_date_of_birth")
    private val WEIGHT_KEY = doublePreferencesKey("user_weight")
    private val HEIGHT_KEY = doublePreferencesKey("user_height")
    private val GENDER_KEY = stringPreferencesKey("user_gender")
    private val PREGNANCYDATE_KEY = stringPreferencesKey("user_pregnancy_date")
    private val BREASTFEEDINGDATE_KEY = stringPreferencesKey("user_breastfeeding_date")
    private val DAILY_GOAL_KEY = doublePreferencesKey("user_daily_goal")
    private val RFID_TAG_KEY = stringPreferencesKey("user_rfid_tag")

    // Fungsi untuk menyimpan token
    suspend fun saveUser(context: Context, user: User) {
        context.userDataStore.edit { preferences ->
            preferences[TOKEN_KEY] = user.token
            preferences[ID_KEY] = user.id
            preferences[EMAIL_KEY] = user.email
            // Menyimpan nilai hanya jika tidak null
            preferences[NAME_KEY] = user.name ?: ""
            preferences[DATEOFBIRTH_KEY] = user.date_of_birth ?: ""
            preferences[WEIGHT_KEY] = user.weight ?: 0.0
            preferences[HEIGHT_KEY] = user.height ?: 0.0
            preferences[GENDER_KEY] = user.gender ?: ""
            preferences[PREGNANCYDATE_KEY] = user.pregnancy_date ?: ""
            preferences[BREASTFEEDINGDATE_KEY] = user.breastfeeding_date ?: ""
            preferences[DAILY_GOAL_KEY] = user.daily_goal ?: 0.0
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
            preferences.remove(RFID_TAG_KEY)
        }
    }
}
