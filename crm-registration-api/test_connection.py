from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
import os
from dotenv import load_dotenv

load_dotenv()

# Connection URL with SSL and disabled GSSAPI
DATABASE_URL = (
    f"postgresql://{os.getenv('USER')}:{os.getenv('PASSWORD')}"
    f"@{os.getenv('HOST')}:{os.getenv('PORT')}/{os.getenv('DBNAME')}"
    "?sslmode=require&gssencmode=disable"
)

engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)