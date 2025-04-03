package com.daniel.crmregistration.models

import com.google.gson.annotations.SerializedName

data class Contact(
    //@SerializedName("title") val title: String?,
    @SerializedName("firstname") val firstName: String,  // Required
    @SerializedName("middlename") val middleName: String?,
    @SerializedName("lastname") val lastName: String,   // Required
    @SerializedName("birthdate") val birthDate: String?,
    @SerializedName("gendercode") val gender: Int?,
    //@SerializedName("nationality") val nationality: String?,
    @SerializedName("emailaddress1") val email: String, // Required
    @SerializedName("telephone1") val phone: String?,
    @SerializedName("address1_line1") val address1: String?,
    @SerializedName("address1_line2") val address2: String?,
    @SerializedName("address1_city") val city: String?,
    @SerializedName("address1_stateorprovince") val state: String?,
    @SerializedName("address1_postalcode") val postalCode: String?,
    @SerializedName("address1_country") val country: String?
)