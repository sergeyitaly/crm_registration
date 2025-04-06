from pydantic import BaseModel, EmailStr
from typing import Optional
from datetime import date

class Contact(BaseModel):
    contact_id: Optional[int] = None
    apartment_id: Optional[int] = None
    firstname: str
    middlename: Optional[str] = None
    lastname: str
    birthdate: Optional[date] = None
    gendercode: Optional[int] = None
    nationality: Optional[str] = None
    emailaddress1: EmailStr
    telephone1: str
    address1_line1: str
    address1_line2: Optional[str] = None
    address1_city: str
    address1_stateorprovince: Optional[str] = None
    address1_postalcode: str
    address1_country: str
    bank_account: Optional[str] = None
    passport_id: Optional[str] = None
