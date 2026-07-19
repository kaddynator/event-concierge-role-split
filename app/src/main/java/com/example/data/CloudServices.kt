package com.example.data

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.DateFormat

object CloudServices {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val concierge by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.5-flash",
            systemInstruction = content {
                text(
                    """
                    You are the concise, friendly voice concierge for the Aurora Foundation Gala.
                    Only answer event questions using these facts:
                    Harbor House, Pier 26, San Francisco. Reception 6 PM; remarks 7 PM;
                    dinner and auction 7:30 PM; dessert and live music 9:30 PM; end 11 PM.
                    Dress is elegant formal or black tie optional. Complimentary valet and
                    rideshare drop-off are at the main entrance. The venue is ADA accessible.
                    Dinner has coastal Californian, vegan and gluten-free choices.
                    Keep responses under 55 words. If the facts do not answer the question,
                    say that event staff can help; never invent guest details.
                    """.trimIndent()
                )
            }
        )
    }
    private var stopVoiceAction: (suspend () -> Unit)? = null

    fun connect(onResult: (Result<String>) -> Unit) {
        val ready: (String) -> Unit = { uid ->
            db.collection("events").document("aurora-gala")
                .collection("sessions").document(uid)
                .set(mapOf("platform" to "android", "lastSeen" to FieldValue.serverTimestamp()))
                .addOnSuccessListener { onResult(Result.success(uid)) }
                .addOnFailureListener { onResult(Result.failure(it)) }
        }
        auth.currentUser?.uid?.let(ready) ?: auth.signInAnonymously()
            .addOnSuccessListener { ready(it.user?.uid.orEmpty()) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    suspend fun ask(question: String): String =
        concierge.generateContent(question).text.orEmpty().trim()

    fun save(collection: String, id: String, data: Map<String, Any?>) {
        if (auth.currentUser == null) return
        db.collection("events").document("aurora-gala")
            .collection(collection).document(id)
            .set(data + ("updatedAt" to FieldValue.serverTimestamp()))
    }

    fun delete(collection: String, id: String) {
        if (auth.currentUser == null) return
        db.collection("events").document("aurora-gala")
            .collection(collection).document(id).delete()
    }

    fun observeBroadcasts(
        onUpdate: (List<Announcement>) -> Unit,
        onError: (Exception) -> Unit
    ): () -> Unit {
        val listener = db.collection("events").document("aurora-gala")
            .collection("broadcasts")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                onUpdate(snapshot?.documents.orEmpty().map { document ->
                    Announcement(
                        id = document.id,
                        title = document.getString("title").orEmpty(),
                        content = document.getString("message").orEmpty(),
                        time = document.getTimestamp("updatedAt")?.toDate()?.let {
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(it)
                        }.orEmpty()
                    )
                })
            }
        return listener::remove
    }

    fun sendBroadcast(title: String, message: String, onResult: (Result<Unit>) -> Unit) {
        val user = auth.currentUser
            ?: return onResult(Result.failure(IllegalStateException("Cloud is not connected.")))
        db.collection("events").document("aurora-gala")
            .collection("broadcasts")
            .add(
                mapOf(
                    "title" to title,
                    "message" to message,
                    "sentBy" to user.uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    @OptIn(PublicPreviewAPI::class)
    suspend fun toggleVoice(): Boolean {
        if (stopVoice()) {
            return false
        }
        check(
            ContextCompat.checkSelfPermission(
                FirebaseApp.getInstance().applicationContext,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) { "Microphone permission is required for live voice." }
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = "gemini-2.5-flash-native-audio-preview-12-2025",
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
            },
            systemInstruction = content {
                text("You are the Aurora Foundation Gala concierge. Be warm, brief, and never invent guest information.")
            }
        )
        val session = model.connect()
        session.startAudioConversation()
        stopVoiceAction = {
            session.stopAudioConversation()
            session.close()
        }
        return true
    }

    suspend fun stopVoice(): Boolean {
        val stop = stopVoiceAction ?: return false
        stop()
        stopVoiceAction = null
        return true
    }
}
