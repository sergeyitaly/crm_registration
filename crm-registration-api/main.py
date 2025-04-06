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