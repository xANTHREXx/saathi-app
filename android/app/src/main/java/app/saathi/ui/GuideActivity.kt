package app.saathi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import app.saathi.R
import app.saathi.core.ComplaintDraft
import app.saathi.data.Store

/**
 * Options for reporting and a draft complaint. The guide is fixed, reviewed text (res/raw/guide_en.txt), not
 * generated; the draft is filled only from her trip record. She edits and files it herself.
 */
class GuideActivity : ComponentActivity() {
    companion object { const val EXTRA_CASE = "case"; const val EXTRA_KIND = "kind" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val caseId = intent.getStringExtra(EXTRA_CASE)
        val record = Store.records(this).firstOrNull { it.caseId == caseId } ?: Store.records(this).lastOrNull()
        val guide = resources.openRawResource(R.raw.guide_en).use { it.reader().readText() }.trim()
        // The guide text is legal information that needs a qualified review before it is translated, so it stays English.
        val guideIsEnglishOnly = resources.getBoolean(R.bool.show_english_only_note)
        val draft = record?.let { r ->
            ComplaintDraft.draft(ComplaintDraft.Facts(
                caseId = r.caseId, startedAt = r.createdAt, endedAt = r.endedAt, mode = r.mode, app = r.app, plate = r.plate,
                plateCheck = r.plateCheck, events = r.events, lat = r.lat, lng = r.lng,
                evidence = r.evidence.map { it.name to it.sha256 },
            ))
        }
        setContent { app.saathi.ui.theme.SaathiTheme(dark = false) {
            Column(Modifier.fillMaxSize().background(Color(0xFFF5F6FA)).systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.guide_title), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D1222))
                if (guideIsEnglishOnly) Text(stringResource(R.string.guide_english_note), fontSize = 14.sp, color = Color(0xFF4D5675))
                Text(guide, fontSize = 16.sp, color = Color(0xFF0D1222), lineHeight = 23.sp)
                if (draft != null) {
                    Text(stringResource(R.string.guide_draft_heading), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0D1222))
                    SelectionContainer {
                        Text(draft, fontSize = 14.sp, color = Color(0xFF0D1222), lineHeight = 21.sp, fontFamily = FontFamily.Serif)
                    }
                    Button(onClick = {
                        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Saathi draft", draft))
                    }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.guide_copy)) }
                } else {
                    Text(stringResource(R.string.guide_no_record), fontSize = 14.sp, color = Color(0xFF4D5675))
                }
            }
        } }
    }
}
