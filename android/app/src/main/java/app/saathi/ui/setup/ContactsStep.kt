package app.saathi.ui.setup

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.saathi.core.ANYWHERE
import app.saathi.core.Contact
import app.saathi.core.PendingResult
import app.saathi.core.SetupField
import app.saathi.core.SetupProblem
import app.saathi.core.addPending
import app.saathi.core.cityNote
import app.saathi.core.formatPhone
import app.saathi.core.normalisePhone
import app.saathi.trip.Geocode
import app.saathi.trip.LocationOnce
import app.saathi.ui.theme.Saathi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A validation message: alum, never madder, with an info mark. Sits directly under the field it is about. */
@Composable
internal fun ProblemText(message: String, modifier: Modifier = Modifier) {
    val alum = Saathi.colors.alum
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Info, null, Modifier.padding(top = 2.dp).size(16.dp), tint = alum)
        Spacer(Modifier.size(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = alum)
    }
}

/** Step 1: her name and who gets texted. State lives in [SetupFlow]; this only draws it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContactsStep(
    name: String,
    onNameChange: (String) -> Unit,
    contacts: List<Contact>,
    onContactsChange: (List<Contact>) -> Unit,
    formOpen: Boolean,
    onFormOpenChange: (Boolean) -> Unit,
    formName: String,
    onFormNameChange: (String) -> Unit,
    formPhone: String,
    onFormPhoneChange: (String) -> Unit,
    problem: SetupProblem?,
    problemNonce: Int,
    onProblemChange: (SetupProblem?) -> Unit,
) {
    val c = Saathi.colors

    val nameRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(problem, problemNonce) {
        if (problem?.field == SetupField.YOUR_NAME) nameRequester.bringIntoView()
    }

    Column(Modifier.fillMaxWidth()) {
        Text("Who should we tell?", style = MaterialTheme.typography.headlineMedium)
        Text(
            "If you don't answer a check-in, Saathi texts these people from your own SIM.",
            style = MaterialTheme.typography.bodyLarge, color = c.fg2, modifier = Modifier.padding(top = 8.dp),
        )

        OutlinedTextField(
            name, { onNameChange(it); if (problem?.field == SetupField.YOUR_NAME) onProblemChange(null) },
            Modifier.fillMaxWidth().padding(top = 24.dp).bringIntoViewRequester(nameRequester),
            label = { Text("Your first name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            supportingText = {
                Column {
                    if (problem?.field == SetupField.YOUR_NAME) ProblemText(problem.message)
                    Text(
                        "Shown in alerts: \"${name.trim().ifBlank { "Priya" }} hasn't answered a check-in.\"",
                        style = MaterialTheme.typography.bodySmall, color = c.fg2,
                    )
                }
            },
        )

        ContactsEditor(
            contacts = contacts, onContactsChange = onContactsChange,
            formOpen = formOpen, onFormOpenChange = onFormOpenChange,
            formName = formName, onFormNameChange = onFormNameChange,
            formPhone = formPhone, onFormPhoneChange = onFormPhoneChange,
            problem = problem, problemNonce = problemNonce, onProblemChange = onProblemChange,
            modifier = Modifier.padding(top = 24.dp),
            heading = "People to text",
        )
    }
}

/**
 * The contact rows, the pickers and the type-a-number form, without any step heading.
 * Used by [ContactsStep] and by Settings. State is hoisted; the editor only draws it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContactsEditor(
    contacts: List<Contact>,
    onContactsChange: (List<Contact>) -> Unit,
    formOpen: Boolean,
    onFormOpenChange: (Boolean) -> Unit,
    formName: String,
    onFormNameChange: (String) -> Unit,
    formPhone: String,
    onFormPhoneChange: (String) -> Unit,
    problem: SetupProblem?,
    problemNonce: Int,
    onProblemChange: (SetupProblem?) -> Unit,
    modifier: Modifier = Modifier,
    /** A title drawn above the rows, or null when the caller has its own. */
    heading: String? = null,
    /** When set, shown as a problem (not the plain hint) while there are no contacts. */
    emptyProblem: String? = null,
) {
    val ctx = LocalContext.current
    val c = Saathi.colors

    val peopleRequester = remember { BringIntoViewRequester() }
    val formRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(problem, problemNonce) {
        when (problem?.field) {
            SetupField.CONTACTS -> peopleRequester.bringIntoView()
            SetupField.CONTACT_NAME, SetupField.CONTACT_PHONE -> formRequester.bringIntoView()
            SetupField.YOUR_NAME, null -> {}
        }
    }
    fun clearIf(field: SetupField) { if (problem?.field == field) onProblemChange(null) }

    // Her current city, only if location is already allowed. Never asked for here.
    var currentCity by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
        if (has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            currentCity = withContext(Dispatchers.IO) {
                runCatching {
                    LocationOnce.get(ctx, 3000)?.let { Geocode.lookup(ctx, it.lat, it.lng)?.locality }
                }.getOrNull()?.takeIf { it.isNotBlank() }
            }
        }
    }

    fun addPicked(pickedName: String, rawNumber: String) {
        val phone = normalisePhone(rawNumber)
        if (phone != null) {
            if (contacts.none { it.phone == phone }) onContactsChange(contacts + Contact(pickedName.trim(), phone, ANYWHERE))
            clearIf(SetupField.CONTACTS)
        } else {
            onFormNameChange(pickedName.trim()); onFormPhoneChange(rawNumber.trim()); onFormOpenChange(true)
            (addPending(contacts, pickedName, rawNumber, ANYWHERE) as? PendingResult.Problem)?.let { onProblemChange(it.problem) }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (res.resultCode == Activity.RESULT_OK && uri != null) {
            runCatching {
                ctx.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, null,
                )?.use { cur ->
                    if (cur.moveToFirst()) {
                        val n = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val p = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (n >= 0 && p >= 0) addPicked(cur.getString(n).orEmpty(), cur.getString(p).orEmpty())
                    }
                }
            }
        }
    }

    Column(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().bringIntoViewRequester(peopleRequester)) {
            if (heading != null) {
                Text(heading, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
            }
            contacts.forEachIndexed { i, ct ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                ContactRow(
                    ct, currentCity,
                    onCity = { city -> onContactsChange(contacts.mapIndexed { j, x -> if (j == i) x.copy(city = city) else x }) },
                    onRemove = { onContactsChange(contacts.filterIndexed { j, _ -> j != i }) },
                )
            }
            if (contacts.isEmpty()) {
                if (emptyProblem != null) {
                    ProblemText(emptyProblem)
                } else {
                    Text(
                        "No one yet. Pick at least one person you'd want to know.",
                        style = MaterialTheme.typography.bodyMedium, color = c.fg2,
                    )
                }
            }
            if (problem?.field == SetupField.CONTACTS) ProblemText(problem.message)
        }

        FilledTonalButton(
            onClick = {
                runCatching {
                    picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                }
            },
            Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 56.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Icon(Icons.Rounded.Contacts, null, Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text("Choose from contacts", style = MaterialTheme.typography.titleMedium)
        }

        if (!formOpen) {
            TextButton(onClick = { onFormOpenChange(true) }, Modifier.align(Alignment.CenterHorizontally)) {
                Text("Type a number instead")
            }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp).bringIntoViewRequester(formRequester),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    formName, { onFormNameChange(it); clearIf(SetupField.CONTACT_NAME) },
                    Modifier.fillMaxWidth(),
                    label = { Text("Name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    supportingText = if (problem?.field == SetupField.CONTACT_NAME) {
                        { ProblemText(problem.message) }
                    } else null,
                )
                OutlinedTextField(
                    formPhone, { onFormPhoneChange(it); clearIf(SetupField.CONTACT_PHONE) },
                    Modifier.fillMaxWidth(),
                    label = { Text("Phone number") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    supportingText = {
                        Column {
                            if (problem?.field == SetupField.CONTACT_PHONE) ProblemText(problem.message)
                            Text(
                                "With country code, like +91 98765 43210",
                                style = MaterialTheme.typography.bodySmall, color = c.fg2,
                            )
                        }
                    },
                )
                FilledTonalButton(
                    onClick = {
                        when (val r = addPending(contacts, formName, formPhone, ANYWHERE)) {
                            is PendingResult.Ok -> {
                                onContactsChange(r.contacts)
                                onFormNameChange(""); onFormPhoneChange(""); onFormOpenChange(false)
                                clearIf(SetupField.CONTACTS)
                            }
                            is PendingResult.Problem -> onProblemChange(r.problem)
                        }
                    },
                    Modifier.heightIn(min = 48.dp),
                ) { Text("Add") }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: Contact, currentCity: String?, onCity: (String) -> Unit, onRemove: () -> Unit) {
    val c = Saathi.colors
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),contentAlignment = Alignment.Center) {
                Text(
                    contact.name.trim().firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(contact.name, style = MaterialTheme.typography.titleSmall)
                Text(formatPhone(contact.phone), style = MaterialTheme.typography.bodySmall, color = c.fg2)
                Text(cityNote(contact.city), style = MaterialTheme.typography.bodySmall, color = c.fg2)
            }
            CityChip(contact.city, currentCity, onCity)
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove ${contact.name}") }
        }
    }
}

@Composable
private fun CityChip(city: String, currentCity: String?, onCity: (String) -> Unit) {
    val anywhere = city.trim().equals(ANYWHERE, ignoreCase = true)
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(false) }
    Box(Modifier.minimumInteractiveComponentSize()) {
        Surface(
            onClick = { menu = true },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, Saathi.colors.hair),
            modifier = Modifier.heightIn(min = 36.dp),
        ) {
            Row(
                Modifier.padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (anywhere) Icons.Rounded.Public else Icons.Rounded.NearMe, null,
                    Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    if (anywhere) "Anywhere" else city.trim(),
                    Modifier.widthIn(max = 96.dp),
                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Anywhere") }, onClick = { menu = false; onCity(ANYWHERE) })
            if (currentCity != null) {
                DropdownMenuItem(text = { Text(currentCity) }, onClick = { menu = false; onCity(currentCity) })
            }
            DropdownMenuItem(text = { Text("Other city…") }, onClick = { menu = false; dialog = true })
        }
    }
    if (dialog) {
        var typed by remember { mutableStateOf(if (anywhere) "" else city.trim()) }
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("Which city?") },
            text = {
                OutlinedTextField(
                    typed, { typed = it }, label = { Text("City") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = typed.trim()
                    dialog = false
                    if (t.isNotEmpty()) onCity(t)
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } },
        )
    }
}
