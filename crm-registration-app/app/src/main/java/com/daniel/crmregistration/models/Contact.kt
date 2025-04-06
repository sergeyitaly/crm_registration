package com.daniel.crmregistration.models

import com.google.gson.annotations.SerializedName

data class Contact(
    @SerializedName("contactid") val contactId: String,
    @SerializedName("firstname") val firstName: String,
    @SerializedName("middlename") val middleName: String?,
    @SerializedName("lastname") val lastName: String,
    @SerializedName("birthdate") val birthDate: String?,
    @SerializedName("gendercode") var genderCode: Int?,
    @SerializedName("nationality") val nationality: String?,
    @SerializedName("emailaddress1") val email: String,
    @SerializedName("telephone1") val phone: String?,
    @SerializedName("address1_line1") val address1: String?,
    @SerializedName("address1_line2") val address2: String?,
    @SerializedName("address1_city") val city: String?,
    @SerializedName("address1_stateorprovince") val state: String?,
    @SerializedName("address1_postalcode") val postalCode: String?,
    @SerializedName("address1_country") val country: String?,
    @SerializedName("new_appartmentid") val appartmentId: String?,
    @SerializedName("new_bankaccount") val bankAccount: String?,
    @SerializedName("new_passportid") val passportId: String?
)