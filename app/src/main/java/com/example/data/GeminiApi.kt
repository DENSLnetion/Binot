package com.example.data

import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.MultipartBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

// --- Gemini Models ---

data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null
)

data class Content(
    val parts: List<Part>
)

data class Part(
    val text: String? = null,
    val fileData: FileData? = null
)

data class FileData(
    val mimeType: String,
    val fileUri: String 
)

data class GenerateContentResponse(
    val candidates: List<Candidate>? = null,
    val error: GeminiError? = null
)

data class Candidate(
    val content: Content? = null
)

data class GeminiError(
    val message: String? = null
)

data class UploadResponse(
    val file: GeminiFile? = null,
    val error: GeminiError? = null
)

data class GeminiFile(
    val name: String,
    val uri: String,
    val mimeType: String,
    val state: String 
)

// --- Groq Models ---

data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>
)

data class GroqMessage(
    val role: String,
    val content: String
)

data class GroqChatResponse(
    val choices: List<GroqChoice>?
)

data class GroqChoice(
    val message: GroqMessage?
)

data class GroqAudioResponse(
    val text: String?
)

// --- GitHub Models ---

data class GithubRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val html_url: String? = null,
    val assets: List<GithubAsset>? = null
)

data class GithubAsset(
    val browser_download_url: String? = null
)

// --- Retrofit Services ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @POST("upload/v1beta/files")
    suspend fun uploadFile(
        @Query("key") apiKey: String,
        @Header("X-Goog-Upload-Protocol") protocol: String = "raw",
        @Header("X-Goog-Upload-Header-Content-Length") contentLength: Long,
        @Header("X-Goog-Upload-Header-Content-Type") contentType: String,
        @Header("Content-Type") mimeType: String,
        @Body fileBytes: RequestBody
    ): UploadResponse

    @GET("v1beta/{name}")
    suspend fun getFile(
        @Path("name", encoded = true) name: String,
        @Query("key") apiKey: String
    ): GeminiFile

    @DELETE("v1beta/{name}")
    suspend fun deleteFile(
        @Path("name", encoded = true) name: String,
        @Query("key") apiKey: String
    )
}

interface GroqApiService {
    @POST("openai/v1/chat/completions")
    suspend fun generateContent(
        @Header("Authorization") authHeader: String,
        @Body request: GroqChatRequest
    ): GroqChatResponse

    // Menggunakan pemanggilan eksplisit supaya tidak bentrok dengan data class Part Gemini
    @retrofit2.http.Multipart
    @POST("openai/v1/audio/transcriptions")
    suspend fun transcribeAudio(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.Part file: MultipartBody.Part,
        @retrofit2.http.Part("model") model: RequestBody,
        @retrofit2.http.Part("response_format") responseFormat: RequestBody
    ): GroqAudioResponse
}

interface GithubApiService {
    @Headers("User-Agent: BinotApp")
    @GET("repos/DENSLnetion/Binot/releases/latest")
    suspend fun getLatestRelease(): GithubRelease
}

object RetrofitClient {
    private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"
    private const val GITHUB_BASE_URL = "https://api.github.com/"
    private const val GROQ_BASE_URL = "https://api.groq.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(300, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(GEMINI_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    val groqService: GroqApiService by lazy {
        Retrofit.Builder()
            .baseUrl(GROQ_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GroqApiService::class.java)
    }

    val githubService: GithubApiService by lazy {
        Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GithubApiService::class.java)
    }
}

