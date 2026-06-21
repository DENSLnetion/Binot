package com.example.data

import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

// --- Common Data Classes for Gemini REST API ---

data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null
)

data class Content(
    val parts: List<Part>
)

data class Part(
    val text: String? = null,
    val fileData: FileData? = null // Ganti InlineData jadi FileData
)

// Data class baru untuk sistem File API
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

// Response dari proses Upload File
data class UploadResponse(
    val file: GeminiFile? = null,
    val error: GeminiError? = null
)

// Struktur metadata file di server Gemini
data class GeminiFile(
    val name: String,
    val uri: String,
    val mimeType: String,
    val state: String // Bisa "PROCESSING", "ACTIVE", atau "FAILED"
)

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

// --- Retrofit Setup ---

interface GeminiApiService {
    
    // 1. Endpoint Generate Content (Transkrip/Teks)
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    // 2. Endpoint Upload File (Raw Bytes)
    @POST("upload/v1beta/files")
    suspend fun uploadFile(
        @Query("key") apiKey: String,
        @Header("X-Goog-Upload-Protocol") protocol: String = "raw",
        @Header("X-Goog-Upload-Header-Content-Length") contentLength: Long,
        @Header("X-Goog-Upload-Header-Content-Type") contentType: String,
        @Header("Content-Type") mimeType: String,
        @Body fileBytes: RequestBody
    ): UploadResponse

    // 3. Endpoint Cek Status File (Polling)
    @GET("v1beta/{name}")
    suspend fun getFile(
        @Path("name", encoded = true) name: String,
        @Query("key") apiKey: String
    ): GeminiFile

    // 4. Endpoint Delete File (Wajib buat hemat kuota)
    @DELETE("v1beta/{name}")
    suspend fun deleteFile(
        @Path("name", encoded = true) name: String,
        @Query("key") apiKey: String
    )
}

interface GithubApiService {
    @Headers("User-Agent: BinotApp")
    @GET("repos/DENSLnetion/Binot/releases/latest")
    suspend fun getLatestRelease(): GithubRelease
}

object RetrofitClient {
    private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"
    private const val GITHUB_BASE_URL = "https://api.github.com/"

    // Timeout digedein biar upload file ukuran besar gak gampang putus
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

    val githubService: GithubApiService by lazy {
        Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GithubApiService::class.java)
    }
}

