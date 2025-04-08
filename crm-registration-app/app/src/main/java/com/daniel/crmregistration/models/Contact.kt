package com.daniel.crmregistration.models

import com.google.gson.annotations.SerializedName

data class Contact(
    @SerializedName("contactid")
    val contactId: String? = null,

    @SerializedName("firstname")
    val firstName: String,

    @SerializedName("middlename")
    val middleName: String? = null,

    @SerializedName("lastname")
    val lastName: String,

    @SerializedName("birthdate")
    val birthDate: String? = null,

    @SerializedName("gendercode")
    var genderCode: Int? = null,

    @SerializedName("nationality")
    val nationality: String? = null,

    @SerializedName("emailaddress1")
    val email: String,

    @SerializedName("address1_telephone1")
    val phone: String? = null,

    @SerializedName("address1_line1")
    val address1: String? = null,

    @SerializedName("address1_line2")
    val address2: String? = null,

    @SerializedName("address1_city")
    val city: String? = null,

    @SerializedName("address1_stateorprovince")
    val state: String? = null,

    @SerializedName("address1_postalcode")
    val postalCode: String? = null,

    @SerializedName("address1_country")
    val country: String? = null,

    @SerializedName("new_appartmentid")
    val appartmentId: String? = null,

    @SerializedName("new_buildingname")
    val buildingName: String? = null,

    @SerializedName("new_bankaccount")
    val bankAccount: String? = null,

    @SerializedName("new_passportid")
    val passportId: String? = null
)
