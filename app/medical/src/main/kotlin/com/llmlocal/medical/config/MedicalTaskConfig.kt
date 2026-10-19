package com.llmlocal.medical.config

import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.medical.medical.domain.MedicalAdvice
import com.llmlocal.medical.medical.domain.MedicalParser
import com.llmlocal.medical.medical.domain.MedicalPromptBuilder

/**
 * The medical app's [LlmTaskConfig] — binds the prompt builder and parser
 * for medical-advice generation. Provided to Koin via [medicalsConfigModule].
 */
val MedicalTaskConfig: LlmTaskConfig<MedicalAdvice> = LlmTaskConfig(
    promptBuilder = MedicalPromptBuilder(),
    parser = MedicalParser(),
)