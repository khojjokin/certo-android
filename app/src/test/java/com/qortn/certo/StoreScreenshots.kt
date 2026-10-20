package com.qortn.certo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.qortn.certo.data.AnswerRecord
import com.qortn.certo.data.Appearance
import com.qortn.certo.data.CertProgress
import com.qortn.certo.data.QuizMode
import com.qortn.certo.data.StudyStore
import com.qortn.certo.ui.CertoTheme
import com.qortn.certo.ui.LearnTab
import com.qortn.certo.ui.Nav
import com.qortn.certo.ui.OnboardingScreen
import com.qortn.certo.ui.P
import com.qortn.certo.ui.Page
import com.qortn.certo.ui.QuizScreen
import com.qortn.certo.ui.StatsTab
import com.qortn.certo.ui.StudyTab
import org.junit.Rule
import org.junit.Test

/** Renders real app screens with sample progress for the Google Play listing. */
class StoreScreenshots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 100.0, useDeviceResolution = true)

    init {
        val dir = listOf(java.io.File("src/main/assets/certs"), java.io.File("app/src/main/assets/certs")).first { it.exists() }
        com.qortn.certo.data.CertLibrary.preload(dir.listFiles()!!.filter { it.name.endsWith(".json") }.map { it.readText() })
    }

    private fun store(): StudyStore {
        val s = StudyStore(paparazzi.context)
        s.isPremium = true
        val cert = s.certs.first { it.id == "netplus" }
        val now = com.qortn.certo.data.nowAppleSeconds()
        val answers = cert.questions.take(140).mapIndexed { i, q ->
            q.id to AnswerRecord(if (i % 5 == 0) (q.answer + 1) % q.choices.size else q.answer, i % 5 != 0, now - i * 3600.0, 1, if (i % 5 == 0) 0 else 1)
        }.toMap()
        val days = (0..6).associate { back ->
            StudyStore.dayKey(System.currentTimeMillis() - back * 86_400_000L) to listOf(14, 22, 9, 30, 18, 25, 12)[back]
        }
        s.update {
            it.copy(
                onboarded = true, enrolled = listOf("netplus", "secplus", "awsccp"), selectedCertID = "netplus",
                progress = mapOf("netplus" to CertProgress(answers)), dailyCounts = days, appearance = Appearance.Black,
            )
        }
        return s
    }

    private fun shot(content: @Composable () -> Unit) = paparazzi.snapshot {
        CertoTheme(Appearance.Black) { Box(Modifier.fillMaxSize().background(P.bg)) { content() } }
    }

    @Test fun s1_study() { val s = store(); shot { StudyTab(s, Nav()) } }
    @Test fun s2_quiz() {
        val s = store()
        val cert = s.certs.first { it.id == "netplus" }
        val qs = cert.questions.drop(150).take(10)
        shot { QuizScreen(s, Page.Quiz(QuizMode.Quick10, cert, qs), {}, {}) }
    }
    @Test fun s3_learn() { val s = store(); shot { LearnTab(s, Nav()) } }
    @Test fun s4_stats() { val s = store(); shot { StatsTab(s) } }
    @Test fun s5_paths() { val s = StudyStore(paparazzi.context); shot { OnboardingScreen(s) } }
}
