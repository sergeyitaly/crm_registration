// MainActivity.kt

package com.daniel.crmregistration

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.daniel.crmregistration.databinding.ActivityRegistrationBinding
import com.daniel.crmregistration.models.Contact
import com.daniel.crmregistration.network.ApiResponse
import com.daniel.crmregistration.network.RetrofitClient
import com.daniel.crmregistration.repository.CrmRepository
import com.daniel.crmregistration.ui.theme.CRMRegistrationTheme
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import com.daniel.crmregistration.network.ApiService
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import android.util.Log
import androidx.activity.viewModels
import com.daniel.crmregistration.viewmodels.RegistrationViewModel
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope

// Main Activity (Compose)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var apiService: ApiService
    @Inject lateinit var crmRepository: CrmRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Correct debug code:
        try {
            val assetList = assets.list("")
            Log.d("ASSET_DEBUG", "Assets in APK: ${assetList?.joinToString()}")
            
            // Correct way to open the file:
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
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
        
        startActivity(Intent(this, RegistrationActivity::class.java))
        finish()
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
    @Inject lateinit var apiService: ApiService
    @Inject lateinit var secrets: Secrets
    @Inject lateinit var crmRepository: CrmRepository
    private lateinit var binding: ActivityRegistrationBinding
    private val viewModel: RegistrationViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        Log.d("Registration", "Using CRM URL: ${secrets.crmUrl}")
        
        setupForm()
        setupCrmLink()
    }

    private fun setupCrmLink() {
        binding.tvCrmLink.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val link = crmRepository.getCrmEntityListLink("contact")
                    Log.d("CRM_LINK", "Generated URL: $link") // Debug log
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
    private fun openCrmLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                // Add these flags for better compatibility
                addCategory(Intent.CATEGORY_BROWSABLE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                
                // Optionally force Chrome if needed (uncomment if required)
                // setPackage("com.android.chrome")
            }
            
            // Verify there's an activity to handle the intent
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


private fun submitFormToBackend() {
    if (!validateForm()) return  // Stop if validation fails

    val contact = Contact(
        //title = binding.spinnerTitle.selectedItem?.toString().takeIf { !it.isNullOrEmpty() },
        firstName = binding.editFirstName.text.toString(),
        middleName = binding.editMiddleName.text.toString().takeIf { !it.isNullOrEmpty() },
        lastName = binding.editLastName.text.toString(),
        birthDate = binding.editBirthDate.text.toString().takeIf { !it.isNullOrEmpty() },
        gender = when(binding.spinnerGender.selectedItem?.toString()) {
            "Female" -> 2  // Female maps to 2
            else -> 1      // Default to Male (1) for all other cases
        },

        //nationality = binding.spinnerNationality.selectedItem?.toString().takeIf { !it.isNullOrEmpty() },
        email = binding.etEmail.text.toString(),
        phone = binding.etPhone.text.toString().takeIf { !it.isNullOrEmpty() },
        address1 = binding.editAddress1.text.toString().takeIf { !it.isNullOrEmpty() },
        address2 = binding.editAddress2.text.toString().takeIf { !it.isNullOrEmpty() },
        city = binding.editCity.text.toString().takeIf { !it.isNullOrEmpty() },
        state = binding.editState.text.toString().takeIf { !it.isNullOrEmpty() },
        postalCode = binding.editPostalCode.text.toString().takeIf { !it.isNullOrEmpty() },
        country = binding.spinnerCountry.selectedItem?.toString().takeIf { !it.isNullOrEmpty() }
    )

    lifecycleScope.launch {
        try {
            val authToken = "Bearer ${secrets.clientSecret}"
            val response = apiService.createContact(contact, authToken)
            if (response.isSuccessful) {
                showToast("Contact created successfully!")
                setupCrmLink()
            } else {
                showToast("Error: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            showToast("Error: ${e.message}")
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
        val countries = arrayOf("Germany", "France", "UK", "USA", "Other")
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
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_SCAN_ID -> handleScanResult(resultCode, data, "ID")
            REQUEST_SCAN_BANK_CARD -> handleScanResult(resultCode, data, "Bank Card")
            REQUEST_GALLERY -> handleGalleryResult(resultCode, data)
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

    companion object {
        const val REQUEST_SCAN_ID = 1001
        const val REQUEST_SCAN_BANK_CARD = 1002
        const val REQUEST_GALLERY = 1003
    }
}