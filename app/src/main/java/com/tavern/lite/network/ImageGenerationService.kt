package com.tavern.lite.network

import android.content.Context
import android.util.Log
import com.tavern.lite.data.model.ApiConfig
import com.tavern.lite.data.model.ApiProvider
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageGenerationService @Inject constructor(
    private val client: OkHttpClient,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ImageGenerationService"
    }

    /**
     * Generate an image using DALL-E API (OpenAI-compatible).
     * Returns the local file path of the downloaded image, or null on failure.
     */
    suspend fun generateImage(
        prompt: String,
        config: ApiConfig,
        size: String = "1024x1024"
    ): String? = withContext(Dispatchers.IO) {
        val provider = config.provider
        if (provider !is ApiProvider.OpenAI) {
            Log.w(TAG, "Image generation only supports OpenAI provider")
            return@withContext null
        }

        try {
            val body = JSONObject().apply {
                put("model", "dall-e-3")
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

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Image generation failed: ${response.code} $errorBody")
                response.close()
                return@withContext null
            }

            val responseBody = response.body?.string()
            response.close()

            if (responseBody == null) {
                Log.e(TAG, "Empty response body")
                return@withContext null
            }

            val json = JSONObject(responseBody)
            val dataArray = json.optJSONArray("data")
            if (dataArray == null || dataArray.length() == 0) {
                Log.e(TAG, "No image data in response")
                return@withContext null
            }

            val imageUrl = dataArray.getJSONObject(0).optString("url")
            if (imageUrl.isNullOrEmpty()) {
                Log.e(TAG, "No image URL in response")
                return@withContext null
            }

            // Download the image
            downloadImage(imageUrl)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Image generation error", e)
            null
        }
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
}
