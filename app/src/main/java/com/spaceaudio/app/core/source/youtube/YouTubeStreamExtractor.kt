package com.spaceaudio.app.core.source.youtube

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ExtractedAudioStream(
    val streamUrl: String,
    val mimeType: String,
    val bitrate: Int,
    val durationMs: Long
)

/**
 * Robust, multi-strategy YouTube Audio Stream Extractor.
 *
 * Strategy execution order:
 *  1. Invidious with local=true  → proxied through Invidious server (no IP-binding issue)
 *  2. Cobalt v10 API             → server-side conversion to MP3
 *  3. Piped instances            → also server-proxied
 *  4. iOS Innertube              → direct YT CDN (may be throttled)
 */
object YouTubeStreamExtractor {

    private const val TAG = "YouTubeStreamExtractor"
    const val MAX_ALLOWED_DURATION_MS = 3600 * 1000L // 1 hour max

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Invidious public instances – ordered by reliability
    private val invidiousInstances = listOf(
        "https://inv.nadeko.net/api/v1",
        "https://invidious.privacydev.net/api/v1",
        "https://invidious.nerdvpn.de/api/v1",
        "https://yt.artemislena.eu/api/v1",
        "https://invidious.projectsegfau.lt/api/v1",
        "https://invidious.jing.rocks/api/v1",
        "https://iv.ggtyler.dev/api/v1",
        "https://invidious.private.coffee/api/v1",
        "https://vid.puffyan.us/api/v1",
        "https://iv.datura.network/api/v1"
    )

    // Cobalt v10 self-hosted instances (POST to root `/` endpoint)
    private val cobaltEndpoints = listOf(
        "https://api.cobalt.tools",
        "https://co.wuk.sh",
        "https://cobalt.api.timelessnesses.de",
        "https://cobalt-api.kwiatekm.tokyo"
    )

    // Piped public API instances
    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.private.coffee",
        "https://pipedapi.leptons.xyz",
        "https://piped-api.lunar.icu",
        "https://piped.tokhmi.xyz"
    )

    // Common audio itag values (audio-only streams)
    private val audioItags = setOf(139, 140, 141, 171, 249, 250, 251)

    /**
     * Resolves a direct audio stream URL for the given YouTube Video ID.
     * Tries multiple strategies in order, returning the first successful result.
     */
    suspend fun extractAudioStream(videoId: String): Result<ExtractedAudioStream> =
        withContext(Dispatchers.IO) {

            // ────────────────────────────────────────────────────────
            // STRATEGY 1: Invidious with local=true
            //   local=true makes Invidious proxy the stream through their
            //   server, so the URL is not IP-bound and works from any device.
            // ────────────────────────────────────────────────────────
            for (instance in invidiousInstances) {
                try {
                    val apiUrl = "$instance/videos/$videoId?local=true"
                    val req = Request.Builder()
                        .url(apiUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) SpaceAudio/1.0")
                        .header("Accept", "application/json")
                        .build()

                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use
                        val body = resp.body?.string() ?: return@use
                        if (!body.trimStart().startsWith("{")) return@use

                        val json = JSONObject(body)
                        val lengthSeconds = json.optLong("lengthSeconds", 0L)
                        val durationMs = lengthSeconds * 1000L

                        val allFormats = mutableListOf<JSONObject>()
                        json.optJSONArray("adaptiveFormats")?.let { arr ->
                            for (i in 0 until arr.length()) allFormats.add(arr.getJSONObject(i))
                        }
                        // formatStreams = muxed, useful as last resort
                        json.optJSONArray("formatStreams")?.let { arr ->
                            for (i in 0 until arr.length()) allFormats.add(arr.getJSONObject(i))
                        }

                        var bestUrl = ""
                        var bestMime = "audio/mp4"
                        var maxBitrate = 0

                        for (fmt in allFormats) {
                            val type = fmt.optString("type", "")
                            val itag = fmt.optInt("itag", 0)
                            val isAudio = type.startsWith("audio/") || itag in audioItags
                            if (!isAudio) continue

                            val url = fmt.optString("url", "")
                            val bitrate = fmt.optInt("bitrate", 0)
                            if (url.isNotBlank() && bitrate >= maxBitrate) {
                                maxBitrate = bitrate
                                bestUrl = url
                                bestMime = if (type.isNotBlank()) type.substringBefore(';') else "audio/mp4"
                            }
                        }

                        if (bestUrl.isNotBlank()) {
                            Log.d(TAG, "✅ Strategy 1 (Invidious/$instance): $bestMime ${durationMs}ms")
                            return@withContext Result.success(
                                ExtractedAudioStream(bestUrl, bestMime, maxBitrate, durationMs)
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Invidious $instance failed: ${e.message}")
                }
            }

            // ────────────────────────────────────────────────────────
            // STRATEGY 2: Cobalt v10 API
            //   Cobalt converts and proxies the audio through its own server.
            //   POST to the root / endpoint with JSON body.
            //   Supports both `url` (redirect/tunnel) and `status` fields.
            // ────────────────────────────────────────────────────────
            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            val cobaltBody = JSONObject().apply {
                put("url", videoUrl)
                put("downloadMode", "audio")
                put("audioFormat", "mp3")
                put("audioBitrate", "128")
            }

            for (cobaltBase in cobaltEndpoints) {
                try {
                    val req = Request.Builder()
                        .url("$cobaltBase/")
                        .post(cobaltBody.toString().toRequestBody("application/json".toMediaType()))
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "SpaceAudio/1.0")
                        .build()

                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use
                        val body = resp.body?.string() ?: return@use
                        if (!body.trimStart().startsWith("{")) return@use

                        val json = JSONObject(body)
                        val status = json.optString("status", "")
                        val directUrl = json.optString("url", "")

                        if (directUrl.isNotBlank() &&
                            status in setOf("tunnel", "redirect", "stream", "success", "picker")
                        ) {
                            Log.d(TAG, "✅ Strategy 2 (Cobalt/$cobaltBase): status=$status")
                            return@withContext Result.success(
                                ExtractedAudioStream(directUrl, "audio/mpeg", 128000, 0L)
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Cobalt $cobaltBase failed: ${e.message}")
                }
            }

            // ────────────────────────────────────────────────────────
            // STRATEGY 3: Piped API
            //   Piped also proxies its audio streams, so URLs work from any IP.
            // ────────────────────────────────────────────────────────
            for (instance in pipedInstances) {
                try {
                    val req = Request.Builder()
                        .url("$instance/streams/$videoId")
                        .header("User-Agent", "SpaceAudio/1.0")
                        .header("Accept", "application/json")
                        .build()

                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use
                        val body = resp.body?.string() ?: return@use
                        if (!body.trimStart().startsWith("{")) return@use

                        val json = JSONObject(body)
                        val durationSec = json.optLong("duration", 0L)
                        val durationMs = durationSec * 1000L
                        val audioStreams = json.optJSONArray("audioStreams") ?: return@use

                        var bestUrl = ""
                        var bestMime = "audio/mp4"
                        var maxBitrate = 0

                        for (i in 0 until audioStreams.length()) {
                            val stream = audioStreams.getJSONObject(i)
                            val url = stream.optString("url", "")
                            val bitrate = stream.optInt("bitrate", 0)
                            val mime = stream.optString("mimeType", "audio/mp4")
                            if (url.isNotBlank() && bitrate >= maxBitrate) {
                                maxBitrate = bitrate
                                bestUrl = url
                                bestMime = mime.substringBefore(';')
                            }
                        }

                        if (bestUrl.isNotBlank()) {
                            Log.d(TAG, "✅ Strategy 3 (Piped/$instance): $bestMime ${durationMs}ms")
                            return@withContext Result.success(
                                ExtractedAudioStream(bestUrl, bestMime, maxBitrate, durationMs)
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Piped $instance failed: ${e.message}")
                }
            }

            // ────────────────────────────────────────────────────────
            // STRATEGY 4: YouTube Innertube — iOS Client
            //   Last resort: direct call to YouTube Innertube API.
            //   Only use plain `url` fields (no `signatureCipher`) to
            //   avoid n-parameter throttling.
            // ────────────────────────────────────────────────────────
            try {
                val payload = JSONObject().apply {
                    put("videoId", videoId)
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", "IOS")
                            put("clientVersion", "19.29.1")
                            put("deviceModel", "iPhone16,2")
                            put("osName", "iOS")
                            put("osVersion", "17.5.1.21F90")
                            put("hl", "en")
                            put("gl", "US")
                        })
                    })
                }

                val req = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .header("User-Agent", "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X; en_US)")
                    .header("X-YouTube-Client-Name", "5")
                    .header("X-YouTube-Client-Version", "19.29.1")
                    .build()

                httpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val root = JSONObject(body)
                        val videoDetails = root.optJSONObject("videoDetails")
                        val lengthSec = videoDetails?.optString("lengthSeconds", "0")?.toLongOrNull() ?: 0L
                        val durationMs = lengthSec * 1000L

                        val adaptiveFormats = root.optJSONObject("streamingData")
                            ?.optJSONArray("adaptiveFormats") ?: return@use

                        var bestStream: ExtractedAudioStream? = null
                        var highestBitrate = 0

                        for (i in 0 until adaptiveFormats.length()) {
                            val fmt = adaptiveFormats.getJSONObject(i)
                            val mimeType = fmt.optString("mimeType", "")
                            if (!mimeType.startsWith("audio/")) continue
                            // Skip cipher-encrypted URLs — they throttle to ~50 KB/s
                            val streamUrl = fmt.optString("url", "")
                            if (streamUrl.isBlank()) continue
                            val bitrate = fmt.optInt("bitrate", 0)
                            if (bitrate > highestBitrate) {
                                highestBitrate = bitrate
                                bestStream = ExtractedAudioStream(
                                    streamUrl = streamUrl,
                                    mimeType = mimeType.substringBefore(';'),
                                    bitrate = bitrate,
                                    durationMs = durationMs
                                )
                            }
                        }

                        if (bestStream != null) {
                            Log.d(TAG, "✅ Strategy 4 (iOS Innertube): ${bestStream.mimeType} ${bestStream.durationMs}ms")
                            return@withContext Result.success(bestStream)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Strategy 4 (iOS Innertube) failed: ${e.message}")
            }

            // All strategies exhausted
            Log.e(TAG, "❌ All strategies failed for videoId=$videoId")
            Result.failure(
                IllegalStateException(
                    "No se pudo obtener el audio de este vídeo de YouTube.\n" +
                    "Comprueba tu conexión a Internet o intenta con otro enlace."
                )
            )
        }
}
