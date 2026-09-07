import datetime
import re
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

def parse_tzinfo(tz_string: str) -> datetime.tzinfo:
    """
    Parses a timezone string which can be either a standard UTC offset like "+05:30"
    or an IANA timezone identifier like "America/New_York".
    """
    match = re.match(r'^([+-])(\d{2}):?(\d{2})?$', tz_string)
    if match:
        sign = 1 if match.group(1) == '+' else -1
        hours = int(match.group(2))
        minutes = int(match.group(3)) if match.group(3) else 0
        return datetime.timezone(datetime.timedelta(hours=hours * sign, minutes=minutes * sign))
    
    try:
        return ZoneInfo(tz_string)
    except ZoneInfoNotFoundError:
        pass
        
    raise ValueError(f"Invalid timezone or offset string: {tz_string}")

def resolve_local_day(utc_datetime: datetime.datetime, utc_offset_str: str) -> datetime.date:
    """
    Resolves a UTC datetime to the user's local day based on their UTC offset/timezone.
    The day rolls over at 04:00 local time.
    """
    tz = parse_tzinfo(utc_offset_str)
    local_dt = utc_datetime.astimezone(tz)
    
    # If the time is before 04:00, it counts as the previous day.
    if local_dt.hour < 4:
        return (local_dt - datetime.timedelta(days=1)).date()
    
    return local_dt.date()
