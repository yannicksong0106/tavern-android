package com.tavern.lite.network

import android.content.Context
import android.util.Log
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.ApiProvider
import com.tavern.lite.domain.port.ImageGenerationPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageGenerationService @Inject constructor(
    private val client: OkHttpClient,
    @ApplicationContext private val context: Context
) : ImageGenerationPort {
    companion object {
        private const val TAG = "ImageGenerationService"
        private const val DEFAULT_OPENAI_IMAGE_MODEL = "dall-e-3"
        private const val DEFAULT_GEMINI_IMAGE_MODEL = "gemini-3.1-flash-image"
    }

    /**
     * Generate an image using the configured provider when it exposes image generation.
     * Returns the local file path of the downloaded image, or null on failure.
     */
    suspend fun generateImage(
        prompt: String,
        config: ApiConfig
    ): String? = generateImage(prompt, config, "1024x1024")

    override suspend fun generateImage(
        prompt: String,
        config: ApiConfig,
        size: String
    ): String? = withContext(Dispatchers.IO) {
        val provider = config.provider
        try {
            when (provider) {
                is ApiProvider.OpenAI -> generateOpenAIImage(prompt, provider, size)
                is ApiProvider.Gemini -> generateGeminiImage(prompt, provider, size)
                is ApiProvider.Claude -> {
                    Log.w(TAG, "Claude API does not provide image generation")
                    null
                }
                else -> {
                    Log.w(TAG, "Image generation does not support ${provider.displayName} provider")
                    null
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Image generation error", e)
            null
        }
    }

    private fun generateOpenAIImage(
        prompt: String,
        provider: ApiProvider.OpenAI,
        size: String
    ): String? {
        val body = JSONObject().apply {
            put("model", DEFAULT_OPENAI_IMAGE_MODEL)
            put("prompt", prompt)
            put("n", 1)
            put("size", size)
            put("response_format", "url")
        }

        val request = Request.Builder()
            .url("${provider.baseUrl}/images/generations")
            .addHeader("Authorization", "Bearer ${provider.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val responseBody = executeJsonRequest(request) ?: return null
        val json = JSONObject(responseBody)
        val dataArray = json.optJSONArray("data")
        if (dataArray == null || dataArray.length() == 0) {
            Log.e(TAG, "No image data in response")
            return null
        }

        val imageUrl = dataArray.getJSONObject(0).optString("url")
        if (imageUrl.isNullOrEmpty()) {
            Log.e(TAG, "No image URL in response")
            return null
        }

        return downloadImage(imageUrl)
    }

    private fun generateGeminiImage(
        prompt: String,
        provider: ApiProvider.Gemini,
        size: String
    ): String? {
        val model = provider.model.takeIf { it.contains("image", ignoreCase = true) || it.startsWith("imagen", ignoreCase = true) }
            ?: DEFAULT_GEMINI_IMAGE_MODEL
        return if (model.startsWith("imagen", ignoreCase = true)) {
            generateImagenImage(prompt, provider.apiKey, model, size)
        } else {
            generateGeminiNativeImage(prompt, provider.apiKey, model, size)
        }
    }

    private fun generateGeminiNativeImage(
        prompt: String,
        apiKey: String,
        model: String,
        size: String
    ): String? {
        val body = JSONObject().apply {
            put("contents", org.json.JSONArray().put(JSONObject().apply {
                put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("responseModalities", org.json.JSONArray().put("IMAGE"))
                put("responseFormat", JSONObject().apply {
                    put("image", JSONObject().apply {
                        put("aspectRatio", toGeminiAspectRatio(size))
                    })
                })
            })
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1/models/$model:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val responseBody = executeJsonRequest(request) ?: return null
        val imageData = JSONObject(responseBody)
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.let { parts ->
                (0 until parts.length()).firstNotNullOfOrNull { index ->
                    parts.optJSONObject(index)?.let { part ->
                        part.optJSONObject("inlineData")
                            ?.optString("data")
                            ?.takeIf { it.isNotBlank() }
                            ?: part.optJSONObject("inline_data")
                                ?.optString("data")
                                ?.takeIf { it.isNotBlank() }
                    }
                }
            }

        if (imageData.isNullOrBlank()) {
            Log.e(TAG, "No Gemini image data in response")
            return null
        }

        return saveBase64Image(imageData, "gemini")
    }

    private fun generateImagenImage(
        prompt: String,
        apiKey: String,
        model: String,
        size: String
    ): String? {
        val body = JSONObject().apply {
            put("instances", org.json.JSONArray().put(JSONObject().put("prompt", prompt)))
            put("parameters", JSONObject().apply {
                put("sampleCount", 1)
                put("aspectRatio", toGeminiAspectRatio(size))
            })
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:predict")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val responseBody = executeJsonRequest(request) ?: return null
        val imageData = JSONObject(responseBody)
            .optJSONArray("predictions")
            ?.optJSONObject(0)
            ?.optJSONObject("bytesBase64Encoded")
            ?.optString("data")
            ?.takeIf { it.isNotBlank() }
            ?: JSONObject(responseBody)
                .optJSONArray("predictions")
                ?.optJSONObject(0)
                ?.optString("bytesBase64Encoded")
                ?.takeIf { it.isNotBlank() }

        if (imageData.isNullOrBlank()) {
            Log.e(TAG, "No Imagen image data in response")
            return null
        }

        return saveBase64Image(imageData, "imagen")
    }

    private fun executeJsonRequest(request: Request): String? {
        val response = client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) {
                val errorBody = it.body?.string() ?: "Unknown error"
                Log.e(TAG, "Image generation failed: ${it.code} $errorBody")
                return null
            }

            val responseBody = it.body?.string()
            if (responseBody == null) {
                Log.e(TAG, "Empty response body")
                return null
            }
            return responseBody
        }
    }

    private fun saveBase64Image(base64Data: String, prefix: String): String? {
        val imageBytes = Base64.getDecoder().decode(base64Data)
        val imageDir = File(context.filesDir, "generated_images")
        imageDir.mkdirs()

        val file = File(imageDir, "${prefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { output ->
            output.write(imageBytes)
        }
        return file.absolutePath
    }

    private fun downloadImage(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            return null
        }

        val imageDir = File(context.filesDir, "generated_images")
        imageDir.mkdirs()

        val file = File(imageDir, "dalle_${System.currentTimeMillis()}.png")
        response.body?.byteStream()?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        response.close()

        return file.absolutePath
    }

    private fun toGeminiAspectRatio(size: String): String = when (size.substringBefore("x").toIntOrNull() to size.substringAfter("x").toIntOrNull()) {
        1024 to 1024 -> "1:1"
        1024 to 1792 -> "9:16"
        1792 to 1024 -> "16:9"
        else -> "1:1"
    }
}
