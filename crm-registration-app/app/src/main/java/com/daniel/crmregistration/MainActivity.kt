// MainActivity.kt
package com.daniel.crmregistration

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.daniel.crmregistration.databinding.ActivityRegistrationBinding
import com.daniel.crmregistration.models.Contact
import com.daniel.crmregistration.network.ApiService
import com.daniel.crmregistration.repository.CrmRepository
import com.daniel.crmregistration.ui.theme.CRMRegistrationTheme
import com.google.gson.Gson
//import com.journeyapps.barcodescanner.BarcodeFormat
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.viewModels
import com.daniel.crmregistration.viewmodels.RegistrationViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.appcompat.app.AlertDialog
import android.view.View
import java.util.UUID
import java.util.UUID.nameUUIDFromBytes
import retrofit2.Response
import com.daniel.crmregistration.network.ApiResponse
import com.daniel.crmregistration.network.TokenManager
import java.time.LocalDate
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import com.daniel.crmregistration.models.ErrorResponse

// Main Activity (Compose)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var apiService: ApiService
    @Inject lateinit var crmRepository: CrmRepository

    private val REQUEST_CAMERA_PERMISSION = 100
    private val REQUEST_CODE_SCAN = 101

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startQrScanner()
        } else {
            showToast("Camera permission is required to scan QR codes")
            showPermissionRationale()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check and request camera permission
        if (ContextCompat.checkSelfPermission(
                this, 
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startQrScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        try {
            val assetList = assets.list("")
            Log.d("ASSET_DEBUG", "Assets in APK: ${assetList?.joinToString()}")
            
            val inputStream = assets.open("secrets.properties")
            val content = inputStream.bufferedReader().use { it.readText() }
            Log.d("ASSET_DEBUG", "File content:\n$content")
        } catch (e: Exception) {
            Log.e("ASSET_DEBUG", "Asset access failed", e)
        }

        enableEdgeToEdge()
        setContent {
            CRMRegistrationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding))
                }
            }
        }
        
        startActivity(Intent(this, RegistrationActivity::class.java))
        finish()
    }


    private fun showPermissionRationale() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            AlertDialog.Builder(this)
                .setTitle("Camera Permission Needed")
                .setMessage("This app needs the Camera permission to scan QR codes")
                .setPositiveButton("Grant") { _, _ ->
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun startQrScanner() {
        val intent = Intent(this, QrScannerActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE_SCAN)
    }

    private fun handleQrContent(qrContent: String) {
        Log.d("QR_SCAN", "Scanned QR content: $qrContent")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCAN && resultCode == Activity.RESULT_OK) {
            data?.getStringExtra("SCAN_RESULT")?.let { qrContent ->
                handleQrContent(qrContent)
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CRMRegistrationTheme {
        Greeting("Android")
    }
}

@AndroidEntryPoint
class RegistrationActivity : AppCompatActivity() {
    // Dependencies
    @Inject lateinit var apiService: ApiService
    @Inject lateinit var secrets: Secrets
    @Inject lateinit var crmRepository: CrmRepository
    @Inject lateinit var tokenManager: TokenManager
    
    // View binding
    private lateinit var binding: ActivityRegistrationBinding
    
    // ViewModel
    private val viewModel: RegistrationViewModel by viewModels()
    
    // Request codes
    private val REQUEST_CODE_SCAN = 101
    private val REQUEST_SCAN_ID = 1001
    private val REQUEST_SCAN_BANK_CARD = 1002
    private val REQUEST_GALLERY = 1003

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupForm()
        setupCrmLink()
        setupScanButton()
    }

    /* ====================== */
    /* === Setup Functions === */
    /* ====================== */
    
    private fun setupScanButton() {
        binding.buttonScanQr.setOnClickListener {
            try {
                val intent = Intent(this, QrScannerActivity::class.java)
                startActivityForResult(intent, REQUEST_CODE_SCAN)
            } catch (e: Exception) {
                showToast("Error launching QR scanner: ${e.message}")
                Log.e("QR_SCAN", "Failed to launch QR scanner", e)
            }
        }
    }

    private fun setupCrmLink() {
        binding.tvCrmLink.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val link = crmRepository.getCrmEntityListLink("contact")
                    Log.d("CRM_LINK", "Generated URL: $link")
                    withContext(Dispatchers.Main) {
                        openCrmLink(link)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val errorMsg = when {
                            e.message?.contains("Invalid CRM URL") == true -> 
                                "Invalid CRM configuration. Please check settings."
                            else -> "Error opening CRM: ${e.message}"
                        }
                        showToast(errorMsg)
                        Log.e("CRM_LINK", "Error generating link", e)
                    }
                }
            }
        }
    }

    private fun setupForm() {
        // Personal Information
        val titles = arrayOf("Mr.", "Mrs.", "Ms.", "Dr.", "Other")
        ArrayAdapter(this, android.R.layout.simple_spinner_item, titles).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerTitle.adapter = adapter
        }
        
        // Gender dropdown
        val genders = arrayOf("Male", "Female", "Other", "Prefer not to say")
        ArrayAdapter(this, android.R.layout.simple_spinner_item, genders).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerGender.adapter = adapter
        }
        
        // Nationality dropdown
        val countries = arrayOf("Germany", "France", "UK", "USA", "Ukraine","Other")
        ArrayAdapter(this, android.R.layout.simple_spinner_item, countries).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerNationality.adapter = adapter
            binding.spinnerCountry.adapter = adapter
        }
        
        // Date picker
        binding.editBirthDate.setOnClickListener {
            showDatePickerDialog()
        }
        
        // Submit button
        binding.buttonSubmit.setOnClickListener {
            if (validateForm()) {
                submitFormToBackend()
            }
        }
        
        if (binding.root.findViewById<Button>(R.id.buttonScanQr) == null) {
            val scanButton = Button(this).apply {
                id = R.id.buttonScanQr
                text = "Scan QR Code"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 16.dpToPx(), 0, 16.dpToPx())
                }
            }
            (binding.root as LinearLayout).addView(scanButton, 0)
        }
        
        // Set click listener
        binding.root.findViewById<Button>(R.id.buttonScanQr).setOnClickListener {
            val intent = Intent(this, QrScannerActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE_SCAN)
        }
    }

    /* ========================== */
    /* === Activity Lifecycle === */
    /* ========================== */

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_CODE_SCAN -> {
                if (resultCode == RESULT_OK) {
                    data?.getStringExtra("SCAN_RESULT")?.let { qrContent ->
                        try {
                            Log.d("QR_CONTENT", "Raw QR content: $qrContent")
                            val contact = parseQrContent(qrContent)
                            if (contact.firstName.isNotEmpty() && contact.lastName.isNotEmpty() && contact.email.isNotEmpty()) {
                                populateFormWithContact(contact)
                                showToast("QR data loaded successfully")
                            } else {
                                showToast("Invalid QR content: missing required fields")
                            }
                        } catch (e: Exception) {
                            showToast("Error parsing QR code: ${e.message}")
                            Log.e("QR_SCAN", "Error parsing QR content", e)
                            Log.e("QR_SCAN", "QR content was: $qrContent")
                        }
                    } ?: showToast("No QR data received")
                }
            }
            REQUEST_SCAN_ID -> handleScanResult(resultCode, data, "ID")
            REQUEST_SCAN_BANK_CARD -> handleScanResult(resultCode, data, "Bank Card")
            REQUEST_GALLERY -> handleGalleryResult(resultCode, data)
        }
    }

    /* ======================== */
    /* === Form Operations === */
    /* ======================== */

    private fun validateForm(): Boolean {
        var isValid = true

        if (binding.editFirstName.text.isNullOrEmpty()) {
            binding.editFirstName.error = "First name is required"
            isValid = false
        }

        if (binding.editLastName.text.isNullOrEmpty()) {
            binding.editLastName.error = "Last name is required"
            isValid = false
        }

        if (binding.etEmail.text.isNullOrEmpty()) {
            binding.etEmail.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(binding.etEmail.text.toString()).matches()) {
            binding.etEmail.error = "Please enter a valid email"
            isValid = false
        }

        return isValid
    }

    private fun clearForm() {
        // Clear all text fields
        binding.editFirstName.text?.clear()
        binding.editMiddleName.text?.clear()
        binding.editLastName.text?.clear()
        binding.editBirthDate.text?.clear()
        binding.etEmail.text?.clear()
        binding.etPhone.text?.clear()
        binding.editAddress1.text?.clear()
        binding.editAddress2.text?.clear()
        binding.editCity.text?.clear()
        binding.editState.text?.clear()
        binding.editPostalCode.text?.clear()
        binding.editPassport.text?.clear()
        binding.editAppartmentId.text?.clear()
        binding.editBuildingName.text?.clear()
        binding.editBankAccount.text?.clear()
        binding.editContactId.text?.clear()

        // Reset spinners to first position
        binding.spinnerGender.setSelection(0)
        binding.spinnerCountry.setSelection(0)
        binding.spinnerNationality.setSelection(0)
    }

    private fun populateFormWithContact(contact: Contact) {
        clearForm()

        try {
            // Contact and apartment IDs
            binding.editContactId.setText(contact.contactId)
            contact.appartmentId?.let { 
                binding.editAppartmentId.setText(it.toString())
            }
            contact.buildingName?.let { 
                binding.editBuildingName.setText(it)
            }

            // Personal Information
            binding.editFirstName.setText(contact.firstName)
            binding.editMiddleName.setText(contact.middleName)
            binding.editLastName.setText(contact.lastName)
            
            // Format birth date if available
            contact.birthDate?.let { dateStr ->
                try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val date = inputFormat.parse(dateStr)
                    binding.editBirthDate.setText(outputFormat.format(date))
                } catch (e: Exception) {
                    binding.editBirthDate.setText(dateStr)
                    Log.w("DATE_PARSE", "Couldn't parse date: $dateStr")
                }
            }
            
            // Set gender spinner (0 = Male, 1 = Female)
            binding.spinnerGender.setSelection(
                when(contact.genderCode) {
                    2 -> 1 // Female
                    else -> 0 // Male
                }
            )

            // Contact Information
            binding.etEmail.setText(contact.email)
            binding.etPhone.setText(contact.phone)

            // Address Information
            binding.editAddress1.setText(contact.address1)
            binding.editAddress2.setText(contact.address2)
            binding.editCity.setText(contact.city)
            binding.editState.setText(contact.state)
            binding.editPostalCode.setText(contact.postalCode)

            // Set country spinner
            contact.country?.let { country ->
                val countries = resources.getStringArray(R.array.countries_array)
                val position = countries.indexOfFirst { it.equals(country, ignoreCase = true) }
                if (position >= 0) binding.spinnerCountry.setSelection(position)
            }

            // Financial and Identification
            binding.editPassport.setText(contact.passportId)
            binding.editBankAccount.setText(contact.bankAccount)

            // Nationality spinner
            contact.nationality?.let { nationality ->
                val nationalities = resources.getStringArray(R.array.nationalities_array)
                val position = nationalities.indexOfFirst { it.equals(nationality, ignoreCase = true) }
                if (position >= 0) binding.spinnerNationality.setSelection(position)
            }

        } catch (e: Exception) {
            Log.e("FORM_POPULATE", "Error populating form", e)
            showToast("Error loading contact data: ${e.message}")
        }
    }

    /* ======================= */
    /* === CRM Operations === */
    /* ======================= */
    private fun submitFormToBackend() {
    val rawContactId = binding.editContactId.text.toString().trim()
    if (rawContactId.isEmpty()) {
        showToast("Contact ID is required.")
        return
    }

    lifecycleScope.launch {
        try {
            val authToken = tokenManager.getAuthToken().takeIf { it.isNotBlank() }
                ?: run {
                    showToast("Authentication required. Please login again.")
                    return@launch
                }

            // First try to GET the contact to check if it exists
            val getResponse = try {
                apiService.getContact(
                    contactId = rawContactId,
                    authHeader = "Bearer $authToken"
                )
            } catch (e: Exception) {
                null // If GET fails, we'll treat as new contact
            }

            val contact = createContactObject().copy(contactId = rawContactId)

            if (getResponse?.isSuccessful == true) {
                // Contact exists - perform UPDATE
                val updateResponse = apiService.updateContact(
                    contactId = rawContactId,
                    contact = contact,
                    authHeader = "Bearer $authToken"
                )

                if (updateResponse.isSuccessful) {
                    val updatedContact = updateResponse.body()
                    val name = updatedContact?.firstName ?: "Unknown"
                    showToast("✅ Contact '$name' updated successfully.")
                    Log.d("ContactAPI", "Update success. Contact ID: ${updatedContact?.contactId}")
                } else {
                    handleErrorResponse(updateResponse)
                }
            } else {
                // Contact doesn't exist - perform CREATE
                val createResponse = apiService.createContact(
                    contact = contact,
                    authHeader = "Bearer $authToken"
                )

                if (createResponse.isSuccessful) {
                    val createdContact = createResponse.body()
                    val name = createdContact?.firstName ?: "Unknown"
                    showToast("🎉 New contact '$name' created successfully.")
                    Log.d("ContactAPI", "Create success. Contact ID: ${createdContact?.contactId}")
                } else {
                    handleErrorResponse(createResponse)
                }
            }

        } catch (e: Exception) {
            showToast("⚠️ Network error: ${e.message ?: "Please check your connection"}")
            Log.e("ContactAPI", "Operation failed", e)
        }
    }
}

private fun createContactObject(contactId: String? = null): Contact {
    return Contact(
        contactId = contactId, // Add this for updates
        firstName = binding.editFirstName.text.toString(),
        lastName = binding.editLastName.text.toString(),
        middleName = binding.editMiddleName.text.toString().takeIf { it.isNotEmpty() },
        birthDate = binding.editBirthDate.text.toString().takeIf { it.isNotEmpty() },
        genderCode = when (binding.spinnerGender.selectedItemPosition) {
            1 -> 2
            else -> 1
        },
        email = binding.etEmail.text.toString(),
        phone = binding.etPhone.text.toString().takeIf { it.isNotEmpty() },
        address1 = binding.editAddress1.text.toString().takeIf { it.isNotEmpty() },
        address2 = binding.editAddress2.text.toString().takeIf { it.isNotEmpty() },
        city = binding.editCity.text.toString().takeIf { it.isNotEmpty() },
        state = binding.editState.text.toString().takeIf { it.isNotEmpty() },
        postalCode = binding.editPostalCode.text.toString().takeIf { it.isNotEmpty() },
        country = binding.spinnerCountry.selectedItem?.toString().takeIf { it != "Select Country" },
        appartmentId = binding.editAppartmentId.text.toString().takeIf { it.isNotEmpty() },
        buildingName = binding.editBuildingName.text.toString().takeIf { it.isNotEmpty() },
        bankAccount = binding.editBankAccount.text.toString().takeIf { it.isNotEmpty() },
        passportId = binding.editPassport.text.toString().takeIf { it.isNotEmpty() }
    )
}

    /* ======================= */
    /* === Helper Methods === */
    /* ======================= */

    private fun parseQrContent(qrContent: String): Contact {
        val lines = qrContent.split("\n")
        val dataMap = mutableMapOf<String, String>()

        // Normalize all keys by removing spaces and making lowercase
        for (line in lines) {
            if (line.contains(":")) {
                val parts = line.split(":", limit = 2)
                val key = parts[0].trim().lowercase().replace(" ", "")
                val value = parts[1].trim()
                dataMap[key] = value
            }
        }

        Log.d("QR_PARSER", "Parsed data: $dataMap")

        return Contact(
            contactId = dataMap["contactid"] ?: throw IllegalArgumentException("Contact ID is required"),
            firstName = dataMap["firstname"] ?: throw IllegalArgumentException("First name is required"),
            middleName = dataMap["middlename"],
            lastName = dataMap["lastname"] ?: throw IllegalArgumentException("Last name is required"),
            birthDate = dataMap["birthdate"],
            genderCode = when (dataMap["gendercode"]?.toIntOrNull()) {
                2 -> 2  // Female
                else -> 1  // Male
            },
            nationality = dataMap["nationality"],
            email = dataMap["emailaddress1"] ?: throw IllegalArgumentException("Email is required"),
            phone = dataMap["telephone1"],
            address1 = dataMap["address1line1"] ?: dataMap["address1line1"],
            address2 = dataMap["address1line2"] ?: dataMap["address1line2"],
            city = dataMap["address1city"],
            state = dataMap["address1stateorprovince"],
            postalCode = dataMap["address1postalcode"],
            country = dataMap["address1country"],
            appartmentId = dataMap["appartmentid"] ?: dataMap["apartmentnumber"],
            bankAccount = dataMap["bankaccount"],
            passportId = dataMap["passportid"],
            buildingName = dataMap["buildingname"]
        )
    }

    private fun handleResponse(response: Response<ApiResponse>, isUpdate: Boolean) {
        when {
            response.isSuccessful -> {
                when (response.code()) {
                    201 -> showToast("Contact created successfully")
                    204 -> showToast("Contact updated successfully")
                    else -> showToast("Operation completed with status ${response.code()}")
                }
            }
            else -> handleErrorResponse(response)
        }
    }

    private fun handleErrorResponse(response: Response<*>) {
        val errorMessage = try {
            val errorBody = response.errorBody()?.string()
            when {
                errorBody.isNullOrEmpty() -> "Error ${response.code()}: Empty response"
                else -> {
                    try {
                        Json.decodeFromString<ErrorResponse>(errorBody).error?.message 
                            ?: "Error ${response.code()}: ${errorBody.take(200)}"
                    } catch (e: Exception) {
                        "Error ${response.code()}: ${errorBody.take(200)}"
                    }
                }
            }
        } catch (e: Exception) {
            "Error processing response: ${e.message}"
        }
        showToast(errorMessage)
    }

    private fun openCrmLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                showToast("No browser available to open the link")
                Log.e("CRM", "No activity found to handle URL: $url")
            }
        } catch (e: Exception) {
            showToast("Error opening CRM: ${e.message}")
            Log.e("CRM", "Error opening URL: $url", e)
        }
    }

    private fun handleScanResult(resultCode: Int, data: Intent?, scanType: String) {
        if (resultCode == RESULT_OK) {
            Toast.makeText(this, "$scanType scanned successfully", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun handleGalleryResult(resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                // Process the image URI
            }
        }
    }

    /* ====================== */
    /* === GUID Utilities === */
    /* ====================== */

    private fun isValidGuid(value: String): Boolean {
        return try {
            UUID.fromString(value) != null
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun formatAsGuid(value: String): String {
        require(value.length == 32) { "Input must be 32 characters long" }
        return "${value.substring(0, 8)}-${value.substring(8, 12)}-${value.substring(12, 16)}-" +
               "${value.substring(16, 20)}-${value.substring(20, 32)}"
    }

    private fun generateGuid(entityName: String, inputValue: String): String {
        return if (inputValue.length == 32 && inputValue.matches(Regex("[0-9a-fA-F]+"))) {
            formatAsGuid(inputValue)
        } else {
            val combined = "$entityName-$inputValue".lowercase()
            UUID.nameUUIDFromBytes(combined.toByteArray()).toString()
        }
    }

    /* ===================== */
    /* === UI Utilities === */
    /* ===================== */

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                binding.editBirthDate.setText("$year-${month + 1}-$day")
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val REQUEST_SCAN_ID = 1001
        const val REQUEST_SCAN_BANK_CARD = 1002
        const val REQUEST_GALLERY = 1003
    }
}

class QrScannerActivity : AppCompatActivity() {
    private lateinit var barcodeView: DecoratedBarcodeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check camera permission again in case it was revoked
        if (ContextCompat.checkSelfPermission(
                this, 
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            finish()
            return
        }
        
        setContentView(R.layout.activity_qr_scanner)
        
        barcodeView = findViewById(R.id.barcode_scanner)
        barcodeView.barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
        barcodeView.initializeFromIntent(intent)
        
        barcodeView.decodeSingle { result ->
            result.text?.let { qrContent ->
                setResult(Activity.RESULT_OK, Intent().apply {
                    putExtra("SCAN_RESULT", qrContent)
                })
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(
                this, 
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            barcodeView.resume()
        } else {
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }
}