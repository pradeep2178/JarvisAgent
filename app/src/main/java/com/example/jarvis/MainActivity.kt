package com.example.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: android.speech.tts.TextToSpeech
    private lateinit var statusText: TextView
    private lateinit var voiceButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        voiceButton = findViewById(R.id.voiceButton)

        setupSpeechRecognizer()
        setupTextToSpeech()
        requestRequiredPermissions()

        voiceButton.setOnClickListener {
            startListening()
        }

        updateStatus("Say 'hello Jarvis' or 'what time is it?'")
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateStatus("Listening...")
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No command detected"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Speech service error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    else -> "Speech recognition error: $error"
                }
                updateStatus(message)
                speak("I didn't catch that. Please try again.")
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val command = matches?.firstOrNull()?.orEmpty() ?: ""
                if (command.isNotBlank()) {
                    updateStatus("Heard: $command")
                    handleCommand(command)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    private fun setupTextToSpeech() {
        textToSpeech = android.speech.tts.TextToSpeech(this) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.US
            }
        }
    }

    private fun requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CALL_PHONE)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1001)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty()) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                updateStatus("Permissions ready. Jarvis is online.")
                speak("Jarvis is online and ready to help.")
            } else {
                updateStatus("Some permissions were denied. Voice features may be limited.")
            }
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRequiredPermissions()
            updateStatus("Microphone permission is required to listen.")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening for a command")
        }

        speechRecognizer.startListening(intent)
    }

    private fun handleCommand(command: String) {
        val message = command.trim()
        if (message.isEmpty()) return

        val lowerMessage = message.lowercase(Locale.getDefault())

        when {
            lowerMessage.contains("hello") || lowerMessage.contains("hi jarvis") -> {
                speak("Hello! How can I help you today?")
            }
            lowerMessage.contains("what time") || lowerMessage.contains("time") -> {
                val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                speak("The time is $time")
            }
            lowerMessage.contains("what date") || lowerMessage.contains("date") -> {
                val date = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
                speak("Today is $date")
            }
            lowerMessage.contains("open camera") -> openCamera()
            lowerMessage.contains("open settings") -> openSettings()
            lowerMessage.contains("open google") || lowerMessage.contains("google") -> openBrowser("https://www.google.com")
            lowerMessage.contains("open youtube") -> openBrowser("https://www.youtube.com")
            lowerMessage.contains("call") -> handlePhoneCall(message)
            lowerMessage.contains("weather") -> speak("I can check the weather once you connect a weather API or service.")
            lowerMessage.contains("who are you") || lowerMessage.contains("what can you do") -> {
                speak("I am Jarvis, your personal Android assistant. I can tell time, open apps, launch settings, and assist with calls.")
            }
            else -> {
                speak("I heard: $message. I can help with time, calls, settings, and app actions.")
            }
        }
    }

    private fun handlePhoneCall(command: String) {
        val match = Regex("(?:call|dial)\\s+([\\d+\\s-]+)").find(command)
        val number = match?.groupValues?.get(1)?.replace(Regex("[^\\d+]"), "")

        if (!number.isNullOrEmpty()) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            if (intent.resolveActivity(packageManager) != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    startActivity(intent)
                    speak("Calling number $number")
                } else {
                    speak("Phone permission is required to place calls.")
                }
            }
        } else {
            speak("Please tell me the number to call.")
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            speak("Opening the camera.")
        } else {
            speak("The camera app is not available.")
        }
    }

    private fun openSettings() {
        val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            speak("Opening settings.")
        } else {
            speak("I could not open settings.")
        }
    }

    private fun openBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            speak("Opening the browser.")
        } else {
            speak("No browser is available on this device.")
        }
    }

    private fun updateStatus(message: String) {
        statusText.text = message
    }

    private fun speak(message: String) {
        if (::textToSpeech.isInitialized) {
            textToSpeech.speak(message, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "jarvis_message")
        }
        updateStatus(message)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.stopListening()
            speechRecognizer.destroy()
        }
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }
}
