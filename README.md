<h1 align="center">Binot</h1>

<p align="center">
  <strong>A Smart Voice Assistant & Note-Taking Application Powered by Google Gemini AI</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/github/stars/DENSLnetion/Binot?style=for-the-badge&color=gold" alt="GitHub Stars" />
  <img src="https://img.shields.io/github/forks/DENSLnetion/Binot?style=for-the-badge&color=lightgray" alt="GitHub Forks" />
  <img src="https://img.shields.io/github/v/release/DENSLnetion/Binot?style=for-the-badge&color=blue" alt="Latest Release" />
  <img src="https://img.shields.io/github/license/DENSLnetion/Binot?style=for-the-badge" alt="License" />
</p>

<br>

## Interface Overview

<img width="2889" height="1612" alt="ResizedImage_2026-06-21_05-10-22_3517" src="https://github.com/user-attachments/assets/3d189488-10c8-4f76-b9dc-edddc203ddbb" />


---

## About Binot

Binot is a native Android application designed to bridge the gap between spoken ideas and structured written text. Utilizing the native Android SpeechRecognizer and the generative power of the Google Gemini API, Binot accurately transcribes live audio, processes imported audio files, and transforms raw transcripts into readable, perfectly formatted markdown notes. 

Built with privacy and personalization in mind, Binot allows users to integrate their own API keys, ensuring complete control over their data and AI utilization.

## Key Features

*   **Intelligent Voice Dictation:** Real-time audio transcription featuring an automated system-volume override to prevent notification beeps from interrupting the recording process.
*   **AI-Powered Processing:** Leverages the Gemini 2.5 Flash model to summarize lengthy transcripts, tidy up grammatical errors, and translate text into multiple languages (English, Indonesian, Spanish, Chinese, Japanese).
*   **Mathematical Context Recognition:** Automatically detects spoken mathematical concepts (e.g., "kuadrat", "integral") and forcefully converts them into strict Unicode symbols (e.g., ², ∫).
*   **Audio Import & Playback:** Import existing MP3/Audio files for AI transcription, or playback your recorded sessions directly within the note interface.
*   **Advanced Note Organization:** Comprehensive local database management including custom labeling, multi-selection, pinning, duplication, and a dedicated trash recovery system.
*   **Rich Text Markdown Rendering:** Features a custom-built markdown engine capable of rendering headers, lists, italics, bolding, and active search-word highlighting.
*   **Secure & Local-First:** All notes are saved locally using Room Database. Users can export or restore their entire database via JSON backups.
*   **In-App Updates:** Integrated GitHub release checker automatically notifies users of new updates and facilitates direct APK downloads.

---

## Tech Stack

Binot is built entirely with modern Android development standards:

<p align="left">
  <img src="https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Room_Database-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Room Database" />
  <img src="https://img.shields.io/badge/Retrofit-FF0000?style=for-the-badge&logo=square&logoColor=white" alt="Retrofit" />
  <img src="https://img.shields.io/badge/Gemini_API-8E75B2?style=for-the-badge&logo=googlebard&logoColor=white" alt="Gemini API" />
</p>

---

## Build from Source

If you wish to compile and build Binot from the source code, please follow these instructions:

## Build from Source

If you wish to compile and build Binot from the source code, please follow these instructions:

1. **Clone the repository:**

```bash
git clone https://github.com/DENSLnetion/Binot.git
cd Binot
```

2. **Set up the Environment Variables:**

Binot requires a Gemini API Key to compile successfully.

- Locate the `.env.example` file in the root directory.
- Rename it to `.env`.
- Open the file and insert your API key:

```env
GEMINI_API_KEY=your_actual_gemini_api_key_here
```

3. **Build and Run:**

Open the project in **Android Studio** (Koala or newer recommended), sync the Gradle files, and run the app on your emulator or physical device.

---

## Contributing

Contributions are highly welcomed. Whether it is a bug report, a new feature suggestion, or a code improvement, your input helps make Binot better.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

Please ensure your code follows the existing architectural patterns (MVVM) and respects the Jetpack Compose guidelines used throughout the project.

---

##Support & Donation

Binot is an open-source project provided for free. If you find this application helpful for your daily productivity, study, or work, consider supporting the development. Your contribution helps maintain the repository and fuels future updates.

<a href="https://saweria.co/Densl" target="_blank">
  <img src="https://img.shields.io/badge/Support_via-Saweria-F2A900?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black" alt="Support via Saweria" />
</a>

---

License

Distributed under the MIT License. See "LICENSE" for more information.
