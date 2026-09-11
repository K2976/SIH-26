import base64
import json
import os
import urllib.error
import urllib.request


class GeminiConfigurationError(RuntimeError):
    pass


class GeminiLabelError(RuntimeError):
    pass


def detect_food_labels(image: bytes, mime_type: str, allowed_names: list[str]) -> list[str]:
    """Ask Gemini for catalog labels only; never nutrition or portion data."""
    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        raise GeminiConfigurationError(
            "GEMINI_API_KEY is not configured. Add it to backend/.env and restart the server."
        )
    if not allowed_names:
        return []

    model = os.environ.get("GEMINI_MODEL", "gemini-3.8-flash")
    endpoint = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
    prompt = (
        "Identify food visible in this image. Return only names from the supplied catalog. "
        "Return an empty candidates array when no food is visible or the identity is uncertain. "
        "Do not estimate portion, calories, ingredients, or nutrition. Catalog names: "
        + json.dumps(allowed_names)
    )
    payload = {
        "contents": [
            {
                "parts": [
                    {
                        "inline_data": {
                            "mime_type": mime_type,
                            "data": base64.b64encode(image).decode(),
                        }
                    },
                    {"text": prompt},
                ]
            }
        ],
        "generationConfig": {
            "temperature": 0,
            "maxOutputTokens": 128,
            "responseMimeType": "application/json",
            "responseJsonSchema": {
                "type": "object",
                "properties": {
                    "candidates": {
                        "type": "array",
                        "maxItems": 5,
                        "items": {"type": "string", "enum": allowed_names},
                    }
                },
                "required": ["candidates"],
                "additionalProperties": False,
            },
        },
    }
    request = urllib.request.Request(
        endpoint,
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            body = json.load(response)
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as exc:
        raise GeminiLabelError("Gemini food-label request failed.") from exc

    try:
        text = body["candidates"][0]["content"]["parts"][0]["text"]
        proposed = json.loads(text)["candidates"]
    except (KeyError, IndexError, TypeError, json.JSONDecodeError) as exc:
        raise GeminiLabelError("Gemini returned an invalid food-label response.") from exc

    catalog = {name.casefold(): name for name in allowed_names}
    return list(
        dict.fromkeys(
            catalog[name.casefold()]
            for name in proposed
            if isinstance(name, str) and name.casefold() in catalog
        )
    )
