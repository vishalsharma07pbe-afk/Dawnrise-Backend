package com.dawnrise.school.announcement;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component public class AnnouncementLifecycleWorker {private final SchoolAnnouncementService service;private final DawnriseReleaseService releases;public AnnouncementLifecycleWorker(SchoolAnnouncementService service,DawnriseReleaseService releases){this.service=service;this.releases=releases;}@Scheduled(fixedDelayString="${dawnrise.announcements.lifecycle-delay:PT1M}")public void advance(){service.advance();releases.advance();}}
