package com.dawnrise.academic.common.integration.school;

import java.time.ZoneId;

public interface SchoolTimeZoneClient {

    ZoneId getTimeZone(long organizationId);
}
