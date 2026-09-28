package com.jarvis.ai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var prefs: SharedPreferences

    private var selectedLanguage = "bn"
    private val voiceRequest = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(
            "jarvis_settings",
            Context.MODE_PRIVATE
        )

        tts = TextToSpeech(this, this)

        setContent {
            JarvisScreen(
                onSpeak = { startVoiceInput() },
                onLanguage = { selectedLanguage = it },
                onTextCommand = { handleCommand(it) },
                initialApiKey =
                    prefs.getString("gemini_api_key", "") ?: "",
                onSaveApiKey = { key ->
                    prefs.edit()
                        .putString("gemini_api_key", key.trim())
                        .apply()
                }
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            setTtsLanguage(selectedLanguage)
        }
    }

    private fun setTtsLanguage(language: String) {
        val locale = when (language) {
            "hi" -> Locale("hi", "IN")
            "en" -> Locale.US
            else -> Locale("bn", "IN")
        }

        tts.language = locale
    }

    private fun speak(text: String) {
        setTtsLanguage(selectedLanguage)

        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "JARVIS"
        )
    }

    private fun startVoiceInput() {
        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            when (selectedLanguage) {
                "hi" -> "hi-IN"
                "en" -> "en-IN"
                else -> "bn-IN"
            }
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_PROMPT,
            "Speak to JARVIS"
        )

        startActivityForResult(intent, voiceRequest)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode == voiceRequest &&
            resultCode == Activity.RESULT_OK
        ) {
            val text =
                data
                    ?.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS
                    )
                    ?.firstOrNull()

            if (!text.isNullOrBlank()) {
                handleCommand(text)
            }
        }
    }

    private fun handleCommand(command: String) {

        val q = command
            .trim()
            .lowercase(Locale.getDefault())

        when {

            q.contains("who made you") ||
            q.contains("who created you") ||
            q.contains("কে বানিয়েছে") ||
            q.contains("কে বানিয়েছে") -> {

                speak(
                    when (selectedLanguage) {
                        "en" ->
                            "I was created by Izaz Ahammed."

                        "hi" ->
                            "मुझे Izaz Ahammed ने बनाया है।"

                        else ->
                            "আমাকে বানিয়েছে Izaz Ahammed।"
                    }
                )
            }

            q.contains("time") ||
            q.contains("সময়") ||
            q.contains("সময়") ||
            q.contains("समय") -> {

                val now =
                    java.text.SimpleDateFormat(
                        "hh:mm a",
                        Locale.getDefault()
                    ).format(
                        java.util.Date()
                    )

                speak(
                    when (selectedLanguage) {
                        "en" ->
                            "The current time is $now."

                        "hi" ->
                            "अभी समय $now है।"

                        else ->
                            "এখন সময় $now।"
                    }
                )
            }

            q.contains("alarm") ||
            q.contains("অ্যালার্ম") ||
            q.contains("अलार्म") -> {

                try {
                    startActivity(
                        Intent(
                            android.provider.AlarmClock.ACTION_SET_ALARM
                        )
                    )

                    speak(
                        when (selectedLanguage) {
                            "en" ->
                                "The alarm screen is open."

                            "hi" ->
                                "अलार्म स्क्रीन खोल दी है।"

                            else ->
                                "অ্যালার্মের স্ক্রিন খুলে দিয়েছি।"
                        }
                    )

                } catch (e: Exception) {
                    speak(
                        "Alarm is not available on this device."
                    )
                }
            }

            q.contains("timer") ||
            q.contains("টাইমার") ||
            q.contains("टाइमर") -> {

                try {
                    startActivity(
                        Intent(
                            android.provider.AlarmClock.ACTION_SET_TIMER
                        )
                    )

                    speak(
                        when (selectedLanguage) {
                            "en" ->
                                "The timer screen is open."

                            "hi" ->
                                "टाइमर स्क्रीन खोल दी है।"

                            else ->
                                "টাইমারের স্ক্রিন খুলে দিয়েছি।"
                        }
                    )

                } catch (e: Exception) {
                    speak(
                        "Timer is not available on this device."
                    )
                }
            }

            q.contains("weather") ||
            q.contains("আবহাওয়া") ||
            q.contains("আবহাওয়া") ||
            q.contains("मौसम") -> {

                val city = extractCity(command)
                fetchWeather(city)
            }

            else -> {
                askGemini(command)
            }
        }
    }

    private fun askGemini(command: String) {

        val apiKey =
            prefs
                .getString("gemini_api_key", "")
                ?.trim()
                .orEmpty()

        if (apiKey.isBlank()) {

            speak(
                when (selectedLanguage) {
                    "en" ->
                        "Gemini AI is ready, but your API key has not been added yet."

                    "hi" ->
                        "Gemini AI तैयार है, लेकिन API key अभी जोड़ी नहीं गई है।"

                    else ->
                        "Gemini AI প্রস্তুত আছে, কিন্তু API key এখনো যোগ করা হয়নি।"
                }
            )

            return
        }

        Thread {

            try {

                val endpoint =
                    "https://generativelanguage.googleapis.com/" +
                    "v1beta/models/" +
                    "gemini-2.5-flash-lite:generateContent" +
                    "?key=$apiKey"

                val parts =
                    JSONArray().put(
                        JSONObject().put(
                            "text",
                            """
                            You are JARVIS, a helpful personal AI assistant.

                            Reply in the same language as the user.

                            Be concise, friendly and easy to understand.

                            User said:
                            $command
                            """.trimIndent()
                        )
                    )

                val contents =
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            parts
                        )
                    )

                val body =
                    JSONObject().put(
                        "contents",
                        contents
                    )

                val connection =
                    URL(endpoint)
                        .openConnection()
                        as HttpURLConnection

                connection.requestMethod = "POST"

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                connection.doOutput = true

                connection.outputStream.use {
                    it.write(
                        body.toString()
                            .toByteArray(Charsets.UTF_8)
                    )
                }

                val responseCode =
                    connection.responseCode

                val stream =
                    if (responseCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                val response =
                    stream.bufferedReader().use {
                        it.readText()
                    }

                if (responseCode !in 200..299) {
                    runOnUiThread {
                        speak(
                            "Gemini API error. Please check your API key and quota."
                        )
                    }

                    return@Thread
                }

                val json =
                    JSONObject(response)

                val answer =
                    json
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                        .trim()

                runOnUiThread {
                    speak(answer)
                }

            } catch (e: Exception) {

                runOnUiThread {
                    speak(
                        "I could not connect to Gemini right now."
                    )
                }
            }

        }.start()
    }

    private fun extractCity(command: String): String {

        val patterns =
            listOf(
                Regex(
                    "weather in (.+)",
                    RegexOption.IGNORE_CASE
                ),

                Regex(
                    "আবহাওয়া (.+)",
                    RegexOption.IGNORE_CASE
                ),

                Regex(
                    "আবহাওয়া (.+)",
                    RegexOption.IGNORE_CASE
                ),

                Regex(
                    "मौसम (.+)",
                    RegexOption.IGNORE_CASE
                )
            )

        return patterns
            .firstNotNullOfOrNull {
                it.find(command)
                    ?.groupValues
                    ?.getOrNull(1)
            }
            ?.trim()
            ?.ifBlank {
                "Kolkata"
            }
            ?: "Kolkata"
    }

    private fun fetchWeather(city: String) {

        Thread {

            try {

                val geoUrl =
                    URL(
                        "https://geocoding-api.open-meteo.com/" +
                        "v1/search?name=" +
                        URLEncoder.encode(city, "UTF-8") +
                        "&count=1" +
                        "&language=en" +
                        "&format=json"
                    )

                val geoConnection =
                    geoUrl.openConnection()
                        as HttpURLConnection

                geoConnection.requestMethod = "GET"

                val geoResponse =
                    geoConnection
                        .inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                val results =
                    JSONObject(geoResponse)
                        .optJSONArray("results")

                if (
                    results == null ||
                    results.length() == 0
                ) {

                    runOnUiThread {
                        speak(
                            "I could not find that city."
                        )
                    }

                    return@Thread
                }

                val place =
                    results.getJSONObject(0)

                val latitude =
                    place.getDouble("latitude")

                val longitude =
                    place.getDouble("longitude")

                val weatherUrl =
                    URL(
                        "https://api.open-meteo.com/" +
                        "v1/forecast?" +
                        "latitude=$latitude" +
                        "&longitude=$longitude" +
                        "&current=temperature_2m,weather_code" +
                        "&timezone=auto"
                    )

                val weatherConnection =
                    weatherUrl.openConnection()
                        as HttpURLConnection

                weatherConnection.requestMethod = "GET"

                val weatherResponse =
                    weatherConnection
                        .inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                val current =
                    JSONObject(weatherResponse)
                        .getJSONObject("current")

                val temperature =
                    current.getDouble(
                        "temperature_2m"
                    )

                val answer =
                    when (selectedLanguage) {

                        "en" ->
                            "The current temperature in $city is $temperature degrees Celsius."

                        "hi" ->
                            "$city में अभी तापमान $temperature डिग्री सेल्सियस है।"

                        else ->
                            "$city-তে এখন তাপমাত্রা $temperature ডিগ্রি সেলসিয়াস।"
                    }

                runOnUiThread {
                    speak(answer)
                }

            } catch (e: Exception) {

                runOnUiThread {
                    speak(
                        "Weather data could not be loaded right now."
                    )
                }
            }

        }.start()
    }
