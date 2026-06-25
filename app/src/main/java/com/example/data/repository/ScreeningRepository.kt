package com.example.data.repository

import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.db.ScreeningDao
import com.example.data.db.ScreeningSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreeningRepository(private val screeningDao: ScreeningDao) {

    val allSessions: Flow<List<ScreeningSession>> = screeningDao.getAllSessions()

    fun getSessionsForPatient(patientName: String): Flow<List<ScreeningSession>> =
        screeningDao.getSessionsForPatient(patientName)

    suspend fun insertSession(session: ScreeningSession): Long = withContext(Dispatchers.IO) {
        screeningDao.insertSession(session)
    }

    suspend fun deleteSessionById(id: Int) = withContext(Dispatchers.IO) {
        screeningDao.deleteSessionById(id)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        screeningDao.clearAllSessions()
    }

    /**
     * Conducts cognitive screening using Gemini API, or falls back to a highly detailed local 
     * clinical decision tree heuristic if the API is unavailable.
     */
    suspend fun conductScreening(
        patientName: String,
        age: Int,
        gender: String,
        educationYears: Int,
        memoryScore: Int,      // Max 3
        orientationScore: Int, // Max 5
        dailyActivityScore: Int, // Max 10 (ADL)
        speechSample: String
    ): ScreeningSession = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val hasApiKey = apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY"

        if (hasApiKey) {
            try {
                val systemPrompt = """
                    You are an expert clinical neurologist and machine learning medical system specialized in the early detection of dementia and Mild Cognitive Impairment (MCI). 
                    Your task is to analyze the provided screening data, perform clinical risk estimation, and return a structured assessment report in JSON format.
                    
                    The output must be a valid JSON object matching this schema EXACTLY. Do not include markdown wraps (like ```json ... ```) or extra text.
                    Schema:
                    {
                      "riskScore": 75, // Integer from 0 to 100
                      "riskLevel": "High Risk", // "Low Risk", "Moderate Risk", or "High Risk"
                      "demographicRiskWeight": 0.15, // Float from 0.0 to 1.0 showing demographic risk contribution
                      "questionnaireRiskWeight": 0.20, // Float from 0.0 to 1.0 showing daily activity impairment contribution
                      "speechRiskWeight": 0.25, // Float from 0.0 to 1.0 showing speech pattern contribution
                      "memoryRiskWeight": 0.30, // Float from 0.0 to 1.0 showing memory score deficit contribution
                      "activityRiskWeight": 0.10, // Float from 0.0 to 1.0 showing functional activity contribution
                      "subtleCognitiveChanges": ["Mild verbal fluency reduction", "Memory recall deficit"], // List of string changes identified
                      "clinicalExplanation": "Detailed paragraph explaining which factors contributed most to this specific patient's risk profile based on clinical thresholds.",
                      "recommendations": ["Refer to neurologist for MMSE/MoCA", "Brain MRI scan", "Monitor safety in ADLs"] // List of clinical next steps
                    }
                """.trimIndent()

                val userPrompt = """
                    Patient Name: $patientName
                    Age: $age
                    Gender: $gender
                    Years of Education: $educationYears
                    Memory Recall Test Score: $memoryScore / 3 (Three-word memory test)
                    Orientation Test Score: $orientationScore / 5 (Orientation to Year, Season, Date, Month, Day)
                    Daily Living Activity Score (ADL): $dailyActivityScore / 10 (Impairments in basic daily routines)
                    Speech/Language Usage Sample:
                    "$speechSample"
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = userPrompt)))),
                    systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                    generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.1f)
                )

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

                if (!rawJson.isNullOrEmpty()) {
                    val cleanedJson = rawJson.trim()
                        .removePrefix("```json")
                        .removeSuffix("```")
                        .trim()

                    val json = JSONObject(cleanedJson)
                    val riskScore = json.getInt("riskScore")
                    val riskLevel = json.getString("riskLevel")
                    val dW = json.getDouble("demographicRiskWeight").toFloat()
                    val qW = json.getDouble("questionnaireRiskWeight").toFloat()
                    val sW = json.getDouble("speechRiskWeight").toFloat()
                    val mW = json.getDouble("memoryRiskWeight").toFloat()
                    val aW = json.getDouble("activityRiskWeight").toFloat()

                    val changes = mutableListOf<String>()
                    val changesArray = json.optJSONArray("subtleCognitiveChanges")
                    if (changesArray != null) {
                        for (i in 0 until changesArray.length()) {
                            changes.add(changesArray.getString(i))
                        }
                    }

                    val explanation = json.getString("clinicalExplanation")

                    val recs = mutableListOf<String>()
                    val recsArray = json.optJSONArray("recommendations")
                    if (recsArray != null) {
                        for (i in 0 until recsArray.length()) {
                            recs.add(recsArray.getString(i))
                        }
                    }

                    // Format complete visual explanation block
                    val fullAnalysis = StringBuilder().apply {
                        append("### CLINICAL RISK CLASSIFICATION REPORT\n")
                        append("**Status:** AI-Evaluated Screening (Gemini Platform)\n\n")
                        append("**Clinical Explanation:**\n$explanation\n\n")
                        append("**Identified Subtle Cognitive Changes:**\n")
                        if (changes.isEmpty()) append("- None identified\n")
                        else changes.forEach { append("- $it\n") }
                        append("\n**Clinical Decision Support Recommendations:**\n")
                        recs.forEach { append("- $it\n") }
                    }.toString()

                    val session = ScreeningSession(
                        patientName = patientName,
                        age = age,
                        gender = gender,
                        educationYears = educationYears,
                        memoryScore = memoryScore,
                        orientationScore = orientationScore,
                        dailyActivityScore = dailyActivityScore,
                        speechSample = speechSample,
                        riskScore = riskScore,
                        riskLevel = riskLevel,
                        aiAnalysis = fullAnalysis,
                        demographicRiskWeight = dW,
                        questionnaireRiskWeight = qW,
                        speechRiskWeight = sW,
                        memoryRiskWeight = mW,
                        activityRiskWeight = aW
                    )

                    insertSession(session)
                    return@withContext session
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // If API fails (e.g. network timeout), fall through to the highly refined clinical fallback engine!
            }
        }

        // --- LOCAL CLINICAL ALGORITHM (PREPROCESSING & CLASSIFIER BACKUP) ---
        // This calculates risk based on established neuro-psychological test rules
        
        // 1. Calculate individual score deficits
        val memoryDeficit = (3 - memoryScore).toFloat() / 3.0f // 0.0 (no deficit) to 1.0 (max deficit)
        val orientationDeficit = (5 - orientationScore).toFloat() / 5.0f // 0.0 to 1.0
        val activityDeficit = (10 - dailyActivityScore).toFloat() / 10.0f // 0.0 to 1.0 (Impairment in activities)

        // 2. Estimate speech patterns anomalies locally (Length and Keyword density analysis)
        val wordCount = speechSample.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
        var speechDeficit = 0.0f
        val details = mutableListOf<String>()

        if (wordCount < 15) {
            speechDeficit += 0.5f
            details.add("Reduced verbal output (micro-narrative / speech brevity)")
        } else if (wordCount > 150) {
            speechDeficit += 0.2f // Logorrhea can also happen, but verbosity with repetition is typical
        }

        // Check for repetitive phrases or words commonly associated with dementia word-finding pauses
        val speechLower = speechSample.lowercase(Locale.getDefault())
        val repetitiveTriggers = listOf("thing", "you know", "what is it", "that place", "stuff", "um", "uh", "forget")
        var matchCount = 0
        repetitiveTriggers.forEach { trigger ->
            if (speechLower.contains(trigger)) {
                matchCount++
            }
        }
        if (matchCount >= 2) {
            speechDeficit += 0.4f
            details.add("Frequent use of non-specific terms / placeholder phrases indicative of word-finding difficulty")
        }
        speechDeficit = speechDeficit.coerceAtMost(1.0f)

        // 3. Demographic base vulnerability
        var ageRisk = 0.0f
        if (age > 80) ageRisk = 0.8f
        else if (age > 70) ageRisk = 0.5f
        else if (age > 60) ageRisk = 0.2f

        val educationFactor = if (educationYears < 10) 0.3f else 0.0f // Low education can increase diagnostic vulnerability
        val demographicDeficit = (ageRisk + educationFactor).coerceAtMost(1.0f)

        // 4. Multi-factor Weighted Risk Score Model
        // Weights: Memory (30%), Orientation (25%), Activity Impairment (20%), Speech Patterns (15%), Demographics (10%)
        val mW = 0.30f
        val oW = 0.25f
        val aW = 0.20f
        val sW = 0.15f
        val dW = 0.10f

        val rawRiskValue = (memoryDeficit * mW + 
                             orientationDeficit * oW + 
                             activityDeficit * aW + 
                             speechDeficit * sW + 
                             demographicDeficit * dW) * 100

        val finalRiskScore = rawRiskValue.coerceIn(5.0f, 95.0f).toInt()
        
        val finalRiskLevel = when {
            finalRiskScore >= 65 -> "High Risk"
            finalRiskScore >= 35 -> "Moderate Risk"
            else -> "Low Risk"
        }

        // Add details depending on testing deficits
        if (memoryDeficit > 0.3f) details.add("Short-term cognitive recall deficit (${memoryScore}/3 recalled)")
        if (orientationDeficit > 0.2f) details.add("Mild temporal/spatial disorientation (${orientationScore}/5 correct)")
        if (activityDeficit > 0.2f) details.add("Subtle impairments in activities of daily living (ADL score ${dailyActivityScore}/10)")

        if (details.isEmpty()) {
            details.add("No clinical cognitive changes identified on baseline tests.")
        }

        val explanation = StringBuilder().apply {
            append("This risk assessment of $finalRiskScore% ($finalRiskLevel) is generated by NeuroCare's localized Clinical Heuristic Classifier.\n\n")
            append("The primary contributor is ")
            val maxFactor = maxOf(memoryDeficit * mW, orientationDeficit * oW, activityDeficit * aW, speechDeficit * sW)
            when (maxFactor) {
                memoryDeficit * mW -> append("short-term memory recall deficit, which represents an early biomarker for amnestic Mild Cognitive Impairment (MCI) or Alzheimer's-type dementia.")
                orientationDeficit * oW -> append("temporal/spatial disorientation, indicating localized parietal or frontal lobe cognitive fatigue.")
                activityDeficit * aW -> append("functional dependency issues, where self-reported challenges in daily activities suggest progressive cognitive impact.")
                else -> append("language pattern simplification and placeholder phrases, which commonly correlate with primary progressive aphasia or temporal lobe decline.")
            }
            append(" Years of education ($educationYears years) and patient age ($age) were incorporated to adjust cognitive reserve expectations.")
        }.toString()

        val recommendations = mutableListOf<String>().apply {
            add("Schedule standard MMSE (Mini-Mental State Examination) or MoCA (Montreal Cognitive Assessment) test.")
            if (finalRiskScore >= 35) {
                add("Refer to clinical neuropsychologist for structured diagnostic workup.")
                add("Recommend routine structural neuroimaging (Brain MRI or CT) to rule out vascular etiology.")
            }
            if (finalRiskScore >= 65) {
                add("Conduct comprehensive geriatric assessment and safety review (driving, medication compliance).")
                add("Screen for reversible causes of cognitive decline: Vitamin B12, Thyroid (TSH), and metabolic panels.")
            } else {
                add("Recommend cardiovascular exercises, Mediterranean diet, and high-cognitive stimulation games.")
                add("Re-evaluate cognitive screening in 6 to 12 months to observe rate of progression.")
            }
        }

        val fullHeuristicAnalysis = StringBuilder().apply {
            append("### CLINICAL RISK CLASSIFICATION REPORT\n")
            append("**Status:** Local Preprocessing & Classification Baseline Model\n\n")
            append("**Clinical Explanation:**\n$explanation\n\n")
            append("**Identified Subtle Cognitive Changes:**\n")
            details.forEach { append("- $it\n") }
            append("\n**Clinical Decision Support Recommendations:**\n")
            recommendations.forEach { append("- $it\n") }
        }.toString()

        val session = ScreeningSession(
            patientName = patientName,
            age = age,
            gender = gender,
            educationYears = educationYears,
            memoryScore = memoryScore,
            orientationScore = orientationScore,
            dailyActivityScore = dailyActivityScore,
            speechSample = speechSample,
            riskScore = finalRiskScore,
            riskLevel = finalRiskLevel,
            aiAnalysis = fullHeuristicAnalysis,
            demographicRiskWeight = demographicDeficit,
            questionnaireRiskWeight = activityDeficit,
            speechRiskWeight = speechDeficit,
            memoryRiskWeight = memoryDeficit,
            activityRiskWeight = activityDeficit
        )

        insertSession(session)
        session
    }
}
