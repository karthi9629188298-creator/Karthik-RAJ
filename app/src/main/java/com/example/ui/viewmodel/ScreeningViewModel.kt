package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.ScreeningSession
import com.example.data.repository.ScreeningRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScreeningViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ScreeningRepository(db.screeningDao())

    // All historic screening sessions observed reactively from Room database
    val sessions: StateFlow<List<ScreeningSession>> = repository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current screen active tab state (0 = Assessment, 1 = Clinician Dashboard, 2 = ML Metrics, 3 = Architecture & Ethics)
    private val _activeTab = MutableStateFlow(0)
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    // Interactive Assessment Form State
    val formPatientName = MutableStateFlow("")
    val formAge = MutableStateFlow("72")
    val formGender = MutableStateFlow("Female")
    val formEducationYears = MutableStateFlow("12")
    
    // Cognitive test sub-scores
    val formMemoryScore = MutableStateFlow(3) // 0 to 3
    val formOrientationScore = MutableStateFlow(5) // 0 to 5
    val formDailyActivityScore = MutableStateFlow(10) // 0 to 10

    // Speech pattern analysis input
    val formSpeechSample = MutableStateFlow(
        "I woke up this morning, had some tea and toast. Then I went out to get the mail, but I couldn't quite find the key for a minute. Eventually I remembered it was on the kitchen counter next to my eyeglasses."
    )

    // Active screen analysis states
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _selectedSession = MutableStateFlow<ScreeningSession?>(null)
    val selectedSession: StateFlow<ScreeningSession?> = _selectedSession.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // Pre-populate database with high-fidelity realistic clinical histories if empty
        viewModelScope.launch {
            repository.allSessions.collect { list ->
                if (list.isEmpty()) {
                    prepopulateDatabase()
                } else if (_selectedSession.value == null) {
                    _selectedSession.value = list.first()
                }
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _activeTab.value = tabIndex
    }

    fun selectSession(session: ScreeningSession) {
        _selectedSession.value = session
    }

    fun deleteSession(session: ScreeningSession) {
        viewModelScope.launch {
            repository.deleteSessionById(session.id)
            if (_selectedSession.value?.id == session.id) {
                _selectedSession.value = null
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAll()
            _selectedSession.value = null
        }
    }

    fun runCognitiveAssessment() {
        val name = formPatientName.value.trim()
        val ageStr = formAge.value.trim()
        val eduStr = formEducationYears.value.trim()
        val speech = formSpeechSample.value.trim()

        if (name.isEmpty()) {
            _errorMessage.value = "Patient Name cannot be empty"
            return
        }

        val age = ageStr.toIntOrNull()
        if (age == null || age !in 1..130) {
            _errorMessage.value = "Please enter a valid age (1-130)"
            return
        }

        val education = eduStr.toIntOrNull()
        if (education == null || education !in 0..40) {
            _errorMessage.value = "Please enter valid years of education (0-40)"
            return
        }

        if (speech.isEmpty()) {
            _errorMessage.value = "Please enter or record a brief speech sample"
            return
        }

        _errorMessage.value = null
        _isAnalyzing.value = true

        viewModelScope.launch {
            try {
                val session = repository.conductScreening(
                    patientName = name,
                    age = age,
                    gender = formGender.value,
                    educationYears = education,
                    memoryScore = formMemoryScore.value,
                    orientationScore = formOrientationScore.value,
                    dailyActivityScore = formDailyActivityScore.value,
                    speechSample = speech
                )
                _selectedSession.value = session
                _activeTab.value = 1 // Switch to visual clinician dashboard to show the results!
                
                // Reset form fields for next use
                resetForm()
            } catch (e: Exception) {
                _errorMessage.value = "Assessment failed: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private fun resetForm() {
        formPatientName.value = ""
        formAge.value = "72"
        formGender.value = "Female"
        formEducationYears.value = "12"
        formMemoryScore.value = 3
        formOrientationScore.value = 5
        formDailyActivityScore.value = 10
        formSpeechSample.value = "I woke up this morning, had some tea and toast. Then I went out to get the mail, but I couldn't quite find the key for a minute. Eventually I remembered it was on the kitchen counter next to my eyeglasses."
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    private suspend fun prepopulateDatabase() {
        // Patient 1: High Risk (Advanced Cognitive Impact)
        val highRiskSession = ScreeningSession(
            patientName = "Jane Miller",
            age = 81,
            gender = "Female",
            educationYears = 10,
            dateTimestamp = System.currentTimeMillis() - 86400000 * 30, // 30 days ago
            memoryScore = 0,
            orientationScore = 2,
            dailyActivityScore = 4,
            speechSample = "I... I went to the store today to get some... what do you call those things... the yellow things... bananas. My daughter came with me. Or was it yesterday? I forget. I have trouble remembering where my keys are.",
            riskScore = 88,
            riskLevel = "High Risk",
            aiAnalysis = """
                ### CLINICAL RISK CLASSIFICATION REPORT
                **Status:** Baseline Preprocessing & Classification Model
                
                **Clinical Explanation:**
                Patient Jane Miller presents with severe short-term memory recall deficits (0/3 word recall) paired with temporal-spatial disorientation (2/5 time/place markers missed). She reports notable functional decline in basic activities of daily living (ADL score of 4/10), indicating progressive impairment in maintaining self-care. Speech sample shows severe latency pauses, semantic word-substitution difficulty ("the yellow things" for bananas), and temporal confusion. This visual pattern highly correlates with severe cortical cognitive decline.
                
                **Identified Subtle Cognitive Changes:**
                - Severe short-term memory recall deficit (0/3 words)
                - Spatial/temporal disorientation (2/5 orientation elements)
                - Severe semantic placeholder usage ("yellow things") and verbal latency
                - Moderate-to-severe impairment in instrumental activities of daily living
                
                **Clinical Decision Support Recommendations:**
                - Urgent neurological referral for formal neuropsychological panel (MoCA / MMSE).
                - Schedule diagnostic high-resolution brain MRI to evaluate hippocampal volumetric atrophy and vascular lesions.
                - Review daily living safety profile immediately (medication compliance checks, cooking appliances oversight, driving safety restriction).
            """.trimIndent(),
            demographicRiskWeight = 0.85f,
            questionnaireRiskWeight = 0.80f,
            speechRiskWeight = 0.75f,
            memoryRiskWeight = 1.00f,
            activityRiskWeight = 0.80f
        )

        // Patient 1 Trend 2: (Jane Miller, 15 days ago, slightly better - illustrating decline trend)
        val highRiskSessionTrend = ScreeningSession(
            patientName = "Jane Miller",
            age = 81,
            gender = "Female",
            educationYears = 10,
            dateTimestamp = System.currentTimeMillis() - 86400000 * 15, // 15 days ago
            memoryScore = 1,
            orientationScore = 3,
            dailyActivityScore = 5,
            speechSample = "The... the yellow fruits are nice. I went to get some bananas with my daughter. Yes. I forget things sometimes. I forget where my glasses are.",
            riskScore = 74,
            riskLevel = "High Risk",
            aiAnalysis = "Patient exhibits progressive cognitive slide over a 15-day comparison window, primarily characterized by worsening temporal orientation and memory recall. Speech latency remains elevated.",
            demographicRiskWeight = 0.85f,
            questionnaireRiskWeight = 0.70f,
            speechRiskWeight = 0.65f,
            memoryRiskWeight = 0.85f,
            activityRiskWeight = 0.70f
        )

        // Patient 2: Moderate Risk (Early Cognitive Changes / MCI)
        val modRiskSession = ScreeningSession(
            patientName = "Arthur Pendelton",
            age = 74,
            gender = "Male",
            educationYears = 14,
            dateTimestamp = System.currentTimeMillis() - 86400000 * 5, // 5 days ago
            memoryScore = 1,
            orientationScore = 4,
            dailyActivityScore = 8,
            speechSample = "I woke up and read the newspaper, then did some gardening. My memory isn't quite as sharp as it used to be. I sometimes lose track of conversation lines, but I can manage my home okay.",
            riskScore = 48,
            riskLevel = "Moderate Risk",
            aiAnalysis = """
                ### CLINICAL RISK CLASSIFICATION REPORT
                **Status:** Baseline Preprocessing & Classification Model
                
                **Clinical Explanation:**
                Patient Arthur Pendelton presents with early amnestic Mild Cognitive Impairment (MCI) risk indicators. His memory recall is impaired (1/3 words), although orientation is mostly preserved (4/5). His high level of education (14 years) suggests a substantial cognitive reserve that may mask subtle frontal lobe changes. ADL functions are relatively intact (8/10), but mild word-finding pauses are evident in his descriptive sample.
                
                **Identified Subtle Cognitive Changes:**
                - Short-term cognitive recall deficit (1/3 words recalled)
                - Mild verbal fluency deceleration
                - Compensatory behaviors using structured routines
                
                **Clinical Decision Support Recommendations:**
                - Schedule formal clinical MoCA test within 3 months.
                - Implement cognitive training exercises and increase structured physical activity (aerobic).
                - Screen for reversible causes (Vitamin B12 deficiency, thyroid dysfunction, sleep apnea).
            """.trimIndent(),
            demographicRiskWeight = 0.50f,
            questionnaireRiskWeight = 0.30f,
            speechRiskWeight = 0.40f,
            memoryRiskWeight = 0.66f,
            activityRiskWeight = 0.30f
        )

        // Patient 3: Low Risk (Cognitive Healthy Controls)
        val lowRiskSession = ScreeningSession(
            patientName = "Elena Rostova",
            age = 63,
            gender = "Female",
            educationYears = 18,
            dateTimestamp = System.currentTimeMillis() - 86400000 * 1, // 1 day ago
            memoryScore = 3,
            orientationScore = 5,
            dailyActivityScore = 10,
            speechSample = "I am a retired school teacher. Today, I worked on a watercolor painting of the garden and then walked three miles with my husband. I feel very active and have no memory problems to report.",
            riskScore = 12,
            riskLevel = "Low Risk",
            aiAnalysis = """
                ### CLINICAL RISK CLASSIFICATION REPORT
                **Status:** Baseline Preprocessing & Classification Model
                
                **Clinical Explanation:**
                Elena Rostova presents with excellent cognitive performance matching her age and high education level (18 years). Memory recall is perfect (3/3), orientation is fully intact (5/5), and daily activities are unimpaired (10/10). Her speech sample is highly fluent, syntactically complex, and shows excellent semantic coherence. Risk for dementia is extremely low.
                
                **Identified Subtle Cognitive Changes:**
                - No diagnostic cognitive changes identified. High cognitive reserve.
                
                **Clinical Decision Support Recommendations:**
                - Maintain current healthy lifestyle (physical exercise, cognitive tasks).
                - Re-evaluate at standard physical wellness checkups in 24 months.
            """.trimIndent(),
            demographicRiskWeight = 0.10f,
            questionnaireRiskWeight = 0.10f,
            speechRiskWeight = 0.10f,
            memoryRiskWeight = 0.00f,
            activityRiskWeight = 0.10f
        )

        repository.insertSession(highRiskSession)
        repository.insertSession(highRiskSessionTrend)
        repository.insertSession(modRiskSession)
        repository.insertSession(lowRiskSession)
    }
}
