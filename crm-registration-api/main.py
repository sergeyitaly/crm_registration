import os
from fastapi import FastAPI, HTTPException, Header, Query
import requests
from msal import ConfidentialClientApplication
import logging
from dotenv import load_dotenv
from app.models import *

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)
load_dotenv()
app = FastAPI()


# CRM API Configuration
CRM_URL = os.getenv("CRM_URL")
TENANT_ID = os.getenv("TENANT_ID")
CLIENT_ID = os.getenv("CLIENT_ID")
CLIENT_SECRET = os.getenv("CLIENT_SECRET")
RESOURCE = os.getenv("RESOURCE")
APP_ID = os.getenv("APP_ID")


def get_access_token():
    authority = f"https://login.microsoftonline.com/{TENANT_ID}"
    app = ConfidentialClientApplication(
        CLIENT_ID,
        authority=authority,
        client_credential=CLIENT_SECRET
    )
    
    result = app.acquire_token_for_client(scopes=[RESOURCE])
    return result.get("access_token")

@app.get("/crm-entity-link")
async def get_crm_entity_link(entity_name: str, authorization: Optional[str] = Header(None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Unauthorized")

    access_token = authorization.split("Bearer ")[1]
    try:
        crm_entity_link = f"{os.getenv('CRM_URL').replace('/api/data/v9.0/', '')}main.aspx?appid={os.getenv('APP_ID')}&pagetype=entitylist&etn={entity_name}"
        return {"crm_entity_link": crm_entity_link}
    except Exception as e:
        logger.error(f"Error generating CRM entity link: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Error generating CRM entity link: {str(e)}")


@app.get("/contacts({contact_id})/generate-link")
async def generate_contact_link(
    contact_id: str,
    app_id: str = Query(..., alias="app_id")
):
    # Validate the app_id if needed
    if app_id != os.getenv("APP_ID"):
        raise HTTPException(status_code=403, detail="Invalid app ID")
    
    # Generate the CRM link
    crm_link = (
        f"{os.getenv('CRM_MAIN_URL')}main.aspx?"
        f"appid={app_id}&"
        f"pagetype=entityrecord&"
        f"etn=contact&"
        f"id={contact_id}"
    )
    
    return {"link": crm_link}

# API Endpoints
@app.post("/contacts")
async def create_contact(contact: Contact):
    access_token = get_access_token()
    if not access_token:
        raise HTTPException(status_code=401, detail="Failed to authenticate with CRM")
    
    headers = {
        "Authorization": f"Bearer {access_token}",
        "Content-Type": "application/json",
        "OData-MaxVersion": "4.0",
        "OData-Version": "4.0"
    }
    
    contact_data = {
        "salutation": contact.title,
        "firstname": contact.firstName,
        "middlename": contact.middleName,
        "lastname": contact.lastName,
        "birthdate": contact.birthDate,
        "gendercode": 1 if contact.gender == "Male" else 2 if contact.gender == "Female" else 3,
        "new_nationality": contact.nationality,
        "emailaddress1": contact.email,
        "mobilephone": contact.phone,
        "address1_line1": contact.address1,
        "address1_line2": contact.address2,
        "address1_city": contact.city,
        "address1_stateorprovince": contact.state,
        "address1_postalcode": contact.postalCode,
        "address1_country": contact.country
    }
    
    response = requests.post(
        f"{CRM_URL}contacts",
        headers=headers,
        json=contact_data
    )
    
    if response.status_code not in [200, 204]:
        raise HTTPException(
            status_code=response.status_code,
            detail=f"CRM API error: {response.text}"
        )
    
    return {"message": "Contact created successfully", "id": response.json().get("contactid")}

@app.get("/")
async def health_check():
    return {"status": "ok"}