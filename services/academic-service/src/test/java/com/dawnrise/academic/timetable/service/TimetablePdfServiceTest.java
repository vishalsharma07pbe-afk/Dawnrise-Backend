package com.dawnrise.academic.timetable.service;

import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.IntervalType;
import com.dawnrise.academic.timetable.dto.TimetableWorkflowDtos.PublishedEntry;
import com.dawnrise.academic.timetable.dto.TimetableWorkflowDtos.PublishedTimetable;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TimetablePdfServiceTest {
    @Test
    void rendersRequiredPublishedTimetableDetails() throws Exception {
        PublishedEntry lesson=new PublishedEntry(1,"Grade 1",2,"Section A",3,"Junior",4,1,
                LocalTime.of(9,0),LocalTime.of(9,40),IntervalType.TEACHING,"Mathematics",5L,
                "Mathematics",6L,"Anita Sharma",7L,"Room 101");
        PublishedEntry recess=new PublishedEntry(1,"Grade 1",2,"Section A",3,"Junior",8,1,
                LocalTime.of(9,40),LocalTime.of(10,0),IntervalType.RECESS,"Recess",null,null,null,null,null,null);
        PublishedTimetable timetable=new PublishedTimetable(9,10,"Dawnrise School","AY 2026",LocalDate.of(2026,4,1),null,
                OffsetDateTime.parse("2026-03-01T10:00:00+05:30"),OffsetDateTime.parse("2026-03-01T10:01:00+05:30"),
                "Asia/Kolkata",List.of(lesson,recess));
        byte[] bytes=new TimetablePdfService().published(timetable,"SECTION TIMETABLE");
        try(var document=Loader.loadPDF(bytes)){
            String text=new PDFTextStripper().getText(document);
            assertTrue(text.contains("Dawnrise School"));
            assertTrue(text.contains("AY 2026"));
            assertTrue(text.contains("Mathematics"));
            assertTrue(text.contains("Anita Sharma"));
            assertTrue(text.contains("Room 101"));
            assertTrue(text.contains("Recess"));
            assertTrue(text.contains("Asia/Kolkata"));
        }
    }

    @Test
    void rendersSnapshottedTeacherNameInTeacherView() throws Exception {
        PublishedEntry lesson=new PublishedEntry(1,"Grade 1",2,"Section A",3,"Junior",4,1,
                LocalTime.of(9,0),LocalTime.of(9,40),IntervalType.TEACHING,"Science",5L,
                "Science",6L,"Anita Sharma",7L,"Science Lab");
        PublishedTimetable timetable=new PublishedTimetable(9,10,"Dawnrise School","AY 2026",
                LocalDate.of(2026,4,1),null,OffsetDateTime.parse("2026-03-01T10:00:00+05:30"),
                OffsetDateTime.parse("2026-03-01T10:01:00+05:30"),"Asia/Kolkata",List.of(lesson));
        try(var document=Loader.loadPDF(new TimetablePdfService().published(timetable,"TEACHER TIMETABLE"))){
            String text=new PDFTextStripper().getText(document);
            assertTrue(text.contains("TEACHER TIMETABLE"));
            assertTrue(text.contains("Anita Sharma"));
            assertTrue(text.contains("Science Lab"));
        }
    }
}
