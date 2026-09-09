import datetime

from apps.diary.utils import parse_tzinfo, resolve_local_day


def test_parse_tzinfo_offset():
    tz = parse_tzinfo("+05:30")
    assert tz.utcoffset(None) == datetime.timedelta(hours=5, minutes=30)

    tz = parse_tzinfo("-04:00")
    assert tz.utcoffset(None) == datetime.timedelta(hours=-4)

    tz = parse_tzinfo("-04")
    assert tz.utcoffset(None) == datetime.timedelta(hours=-4)


def test_parse_tzinfo_zoneinfo():
    tz = parse_tzinfo("America/New_York")
    assert tz.tzname(datetime.datetime(2024, 1, 1)) == "EST"


def test_resolve_local_day_boundary():
    # Offset +05:30
    # 03:59 local is 22:29 UTC previous day
    utc_dt = datetime.datetime(2024, 3, 1, 22, 29, tzinfo=datetime.UTC)
    day = resolve_local_day(utc_dt, "+05:30")
    # Local time is 2024-03-02 03:59. Since it's < 04:00, it rolls back to 2024-03-01.
    assert day == datetime.date(2024, 3, 1)

    # 04:00 local is 22:30 UTC previous day
    utc_dt = datetime.datetime(2024, 3, 1, 22, 30, tzinfo=datetime.UTC)
    day = resolve_local_day(utc_dt, "+05:30")
    # Local time is 2024-03-02 04:00. It stays 2024-03-02.
    assert day == datetime.date(2024, 3, 2)


def test_resolve_local_day_dst_boundary():
    # In New York, DST starts second Sunday in March (March 10, 2024).
    # Clocks go forward at 2am (which becomes 3am).
    # So 04:00 local time is 08:00 UTC (since offset is -4 instead of -5).
    # Let's test 03:59 local time (which is 07:59 UTC).
    utc_dt = datetime.datetime(2024, 3, 10, 7, 59, tzinfo=datetime.UTC)
    day = resolve_local_day(utc_dt, "America/New_York")
    # It should map to previous day 2024-03-09 because it's before 04:00
    assert day == datetime.date(2024, 3, 9)

    # 04:00 local time (which is 08:00 UTC)
    utc_dt = datetime.datetime(2024, 3, 10, 8, 00, tzinfo=datetime.UTC)
    day = resolve_local_day(utc_dt, "America/New_York")
    assert day == datetime.date(2024, 3, 10)
