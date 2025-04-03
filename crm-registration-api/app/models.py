# crm-registration-api/app/models.py
from pydantic import BaseModel
from typing import Optional
class Contact(BaseModel):
    title: Optional[str] = None
    firstName: str
    middleName: Optional[str] = None
    lastName: str
    birthDate: Optional[str] = None
    gender: Optional[str] = None
    nationality: Optional[str] = None
    email: str
    phone: str
    address1: str
    address2: Optional[str] = None
    city: str
    state: Optional[str] = None
    postalCode: str
    country: str