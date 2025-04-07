import os
from fastapi import FastAPI, HTTPException, Header, Query
import requests
from msal import ConfidentialClientApplication
import logging
from dotenv import load_dotenv
from app.models import *
import qrcode
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse, HTMLResponse
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker
import io
import base64
from pydantic import EmailStr
from fastapi import status


logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)
load_dotenv()
app = FastAPI()


# CRM API Configuration
CRM_URL = os.getenv("CRM_URL")
CRM_MAIN_URL = os.getenv("CRM_MAIN_URL")
TENANT_ID = os.getenv("TENANT_ID")
CLIENT_ID = os.getenv("CLIENT_ID")
CLIENT_SECRET = os.getenv("CLIENT_SECRET")
RESOURCE = os.getenv("RESOURCE")
APP_ID = os.getenv("APP_ID")


# Add this to your configuration
CUSTOMIZATION_PREFIX = os.getenv("CUSTOMIZATION_PREFIX", "new_")  # Default to 'new_' if not set

# Update your CUSTOM_FIELDS to use the prefix
CUSTOM_FIELDS = [
    {
        "SchemaName": f"{CUSTOMIZATION_PREFIX}bankaccount",  # Note: removed underscore for camelCase
        "DisplayName": {"LocalizedLabels": [{"Label": "Bank Account", "LanguageCode": 1033}]},
        "Description": {"LocalizedLabels": [{"Label": "Bank account number", "LanguageCode": 1033}]},
        "AttributeType": "String",
        "MaxLength": 100
    },
    {
        "SchemaName": f"{CUSTOMIZATION_PREFIX}passportid",
        "DisplayName": {"LocalizedLabels": [{"Label": "Passport ID", "LanguageCode": 1033}]},
        "Description": {"LocalizedLabels": [{"Label": "Passport identification", "LanguageCode": 1033}]},
        "AttributeType": "String",
        "MaxLength": 50
    },
    {
        "SchemaName": f"{CUSTOMIZATION_PREFIX}appartmentid",
        "DisplayName": {"LocalizedLabels": [{"Label": "Appartment ID", "LanguageCode": 1033}]},
        "Description": {"LocalizedLabels": [{"Label": "ID of the appartment", "LanguageCode": 1033}]},
        "AttributeType": "String",
        "MaxLength": 50
    },
        {
        "SchemaName": f"{CUSTOMIZATION_PREFIX}buildingname",
        "DisplayName": {"LocalizedLabels": [{"Label": "Building Name", "LanguageCode": 1033}]},
        "Description": {"LocalizedLabels": [{"Label": "Name of Building", "LanguageCode": 1033}]},
        "AttributeType": "String",
        "MaxLength": 50
    }
]

def get_access_token():
    authority = f"https://login.microsoftonline.com/{TENANT_ID}"
    app = ConfidentialClientApplication(
        CLIENT_ID,
        authority=authority,
        client_credential=CLIENT_SECRET
    )
    # Explicitly add "/.default" to scopes
    result = app.acquire_token_for_client(scopes=[f"{CRM_MAIN_URL}/.default"])
    
    if "access_token" not in result:
        logger.error(f"Token error: {result.get('error_description')}")
        raise HTTPException(status_code=401, detail="CRM authentication failed")
    
    logger.info("Token acquired successfully")
    return result["access_token"]


def get_existing_contact_attributes(access_token: str):
    headers = {
        "Authorization": f"Bearer {access_token}",
        "Accept": "application/json"
    }
    url = f"{CRM_MAIN_URL}/api/data/v9.0/EntityDefinitions(LogicalName='contact')/Attributes?$select=LogicalName"
    response = requests.get(url, headers=headers)
    if response.status_code != 200:
        logger.error(f"Error fetching contact attributes: {response.text}")
        raise HTTPException(status_code=500, detail="Failed to fetch contact attributes")
    return [attr["LogicalName"] for attr in response.json()["value"]]


def create_custom_field(field: dict, access_token: str):
    # Ensure URL ends with slash
    crm_url = CRM_MAIN_URL if CRM_MAIN_URL.endswith('/') else CRM_MAIN_URL + '/'
    url = f"{crm_url}api/data/v9.0/EntityDefinitions(LogicalName='contact')/Attributes"
    
    headers = {
        "Authorization": f"Bearer {access_token}",
        "Content-Type": "application/json"
    }
    payload = {
        "@odata.type": "Microsoft.Dynamics.CRM.StringAttributeMetadata",
        "SchemaName": field["SchemaName"],
        "DisplayName": field["DisplayName"],
        "Description": field["Description"],
        "RequiredLevel": {"Value": "None"},
        "MaxLength": field["MaxLength"],
        "FormatName": {"Value": "Text"}
    }

    logger.info(f"Request URL: {url}")
    logger.info(f"Request Payload: {payload}")

    try:
        response = requests.post(url, headers=headers, json=payload)
        logger.info(f"Response: {response.status_code} - {response.text}")

        if response.status_code not in [200, 204]:
            raise HTTPException(
                status_code=500,
                detail=f"Failed to create field {field['SchemaName']}: {response.text}"
            )
    except requests.exceptions.RequestException as e:
        logger.error(f"Request failed: {str(e)}")
        raise HTTPException(
            status_code=500,
            detail=f"Network error while creating field {field['SchemaName']}"
        )

def publish_customizations(access_token: str):
    url = f"{CRM_MAIN_URL}/api/data/v9.2/PublishAllXml"
    headers = {
        "Authorization": f"Bearer {access_token}",
        "Content-Type": "application/json"
    }

    response = requests.post(url, headers=headers)
    if response.status_code not in [200, 204]:
        logger.error(f"Failed to publish customizations: {response.text}")
        raise HTTPException(status_code=500, detail="Failed to publish customizations")
    logger.info("Customizations published successfully.")


@app.get("/customize-contact-entity/", status_code=status.HTTP_200_OK)
def check_custom_fields():
    """Check which custom fields exist in the contact entity"""
    try:
        access_token = get_access_token()
        existing_attrs = get_existing_contact_attributes(access_token)
        
        # Prepare response showing which fields exist
        field_status = []
        for field in CUSTOM_FIELDS:
            exists = field["SchemaName"].lower() in existing_attrs
            field_status.append({
                "field_name": field["SchemaName"],
                "display_name": field["DisplayName"]["LocalizedLabels"][0]["Label"],
                "exists": exists
            })
        
        return {
            "message": "Current status of custom fields",
            "fields": field_status,
            "needs_publishing": False  # We can't determine this from just checking attributes
        }
    except Exception as e:
        logger.error(f"Error checking custom fields: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to check custom fields status"
        )

@app.post("/customize-contact-entity/", status_code=status.HTTP_200_OK)
def customize_contact_entity():
    """Create missing custom fields and publish customizations"""
    try:
        access_token = get_access_token()
        existing_attrs = get_existing_contact_attributes(access_token)
        
        created_fields = []
        existing_fields = []
        
        for field in CUSTOM_FIELDS:
            field_name_lower = field["SchemaName"].lower()
            if field_name_lower not in existing_attrs:
                try:
                    create_custom_field(field, access_token)
                    created_fields.append(field["SchemaName"])
                except HTTPException as e:
                    # If creation fails but field exists (race condition), log and continue
                    if "already exists" in str(e.detail):
                        existing_fields.append(field["SchemaName"])
                        logger.info(f"Field {field['SchemaName']} already exists")
                    else:
                        raise
            else:
                existing_fields.append(field["SchemaName"])
        
        if created_fields:
            publish_customizations(access_token)
            message = f"Created fields: {', '.join(created_fields)} and published customizations"
        else:
            message = "All custom fields already exist - no changes needed"
        
        return {
            "message": message,
            "created_fields": created_fields,
            "existing_fields": existing_fields,
            "all_custom_fields": [f["SchemaName"] for f in CUSTOM_FIELDS]
        }
    except Exception as e:
        logger.error(f"Error customizing contact entity: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to customize contact entity"
        )
        
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

@app.get("/crm-contacts-link")
async def get_crm_contacts_link(authorization: Optional[str] = Header(None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Unauthorized")

    try:
        crm_contacts_link = (
            f"{os.getenv('CRM_MAIN_URL')}main.aspx?"
            f"appid={os.getenv('APP_ID')}&"
            f"pagetype=entitylist&"
            f"etn=contact"
        )
        return {"crm_contacts_link": crm_contacts_link}
    except Exception as e:
        logger.error(f"Error generating CRM contacts link: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Error generating CRM contacts link: {str(e)}")


@app.get("/")
async def health_check():
    return {"status": "ok"}

# Configure connection with all required parameters

USER = os.getenv('USER')
PASSWORD = os.getenv('PASSWORD')
HOST = os.getenv('HOST')
PORT = os.getenv('PORT')
DBNAME = os.getenv('DBNAME')
DATABASE_URL = (
    f"postgresql://{USER}:{PASSWORD}@{HOST}:{PORT}/{DBNAME}"
    "?sslmode=require"
    "&gssencmode=disable"
    "&options=-c%20search_path=public"
)


DATABASE_URL = (
    "postgresql://postgres.fjidkcmtdtpjinkriocx:nsYzhcDihdjs2W74@"
    "aws-0-eu-central-1.pooler.supabase.com:6543/postgres"
    "?sslmode=require"
    "&gssencmode=disable"
    "&options=-c%20search_path=public"
)
engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

@app.get("/test-db-connection")
async def test_db_connection():
    try:
        with SessionLocal() as session:
            result = session.execute(text("SELECT 1")).scalar()
            return {"status": "success", "result": result}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

def get_contact(contact_id: int):
    with SessionLocal() as session:
        try:
            # Get all columns from contacts plus apartment and building info
            result = session.execute(
                text("""
                SELECT c.*, a.apartment_number, b.building_name
                FROM contacts c
                JOIN apartments a ON c.apartment_id = a.apartment_id
                JOIN buildings b ON a.building_id = b.building_id
                WHERE c.contact_id = :contact_id
                """),
                {"contact_id": contact_id}
            )
            
            if not result:
                return None
                
            # Convert to dictionary and remove SQLAlchemy internal keys
            contact_data = dict(result.mappings().first())
            return {k: v for k, v in contact_data.items() if not k.startswith('_')}
            
        except Exception as e:
            print(f"Database error: {e}")
            return None

def generate_qr_code_image(contact_data: dict) -> bytes:
    """Generate QR code with all contact data dynamically"""
    qr = qrcode.QRCode(
        version=None,  # Auto-select version based on data size
        error_correction=qrcode.constants.ERROR_CORRECT_L,
        box_size=10,
        border=4,
    )
    
    # Format all data as key-value pairs
    contact_text = "CONTACT INFORMATION:\n"
    for key, value in contact_data.items():
        if value is not None:  # Skip null values
            formatted_key = ' '.join([word.capitalize() for word in key.split('_')])
            contact_text += f"{formatted_key}: {value}\n"
    
    qr.add_data(contact_text)
    qr.make(fit=True)
    img = qr.make_image(fill_color="black", back_color="white")
    
    img_bytes = io.BytesIO()
    img.save(img_bytes, format="PNG")
    return img_bytes.getvalue()

@app.get("/contact-qr/{contact_id}", response_class=HTMLResponse)
async def display_qr(contact_id: int):
    contact_data = get_contact(contact_id)
    if not contact_data:
        raise HTTPException(status_code=404, detail="Contact not found")
    
    try:
        qr_image = generate_qr_code_image(contact_data)
        qr_base64 = base64.b64encode(qr_image).decode("utf-8")
        
        # Generate HTML dynamically
        html_content = f"""
        <html>
            <head>
                <title>Contact QR Code</title>
                <style>
                    body {{ font-family: Arial, sans-serif; max-width: 1000px; margin: 0 auto; padding: 20px; }}
                    .container {{ display: flex; gap: 40px; }}
                    .qr-section {{ flex: 1; text-align: center; }}
                    .info-section {{ flex: 2; }}
                    .qr-code {{ margin: 20px 0; max-width: 300px; }}
                    .contact-info {{ background: #f8f9fa; padding: 20px; border-radius: 8px; }}
                    .info-group {{ margin-bottom: 10px; }}
                    .info-label {{ font-weight: bold; color: #495057; display: inline-block; width: 150px; }}
                    h1 {{ color: #2c3e50; border-bottom: 2px solid #eee; padding-bottom: 10px; }}
                    h2 {{ color: #3498db; margin-top: 20px; }}
                </style>
            </head>
            <body>
                <h1>Contact Details</h1>
                <div class="container">
                    <div class="qr-section">
                        <h2>QR Code</h2>
                        <img src="data:image/png;base64,{qr_base64}" class="qr-code">
                        <p>Scan to save all contact information</p>
                    </div>
                    
                    <div class="info-section">
                        <div class="contact-info">
                            <h2>Complete Contact Information</h2>
                            {"".join(
                                f'<div class="info-group"><span class="info-label">'
                                f'{" ".join([word.capitalize() for word in key.split("_")])}:</span>'
                                f' {value if value is not None else "N/A"}</div>'
                                for key, value in contact_data.items()
                            )}
                        </div>
                    </div>
                </div>
            </body>
        </html>
        """
        
        return HTMLResponse(content=html_content)
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"QR generation failed: {str(e)}")
    
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)