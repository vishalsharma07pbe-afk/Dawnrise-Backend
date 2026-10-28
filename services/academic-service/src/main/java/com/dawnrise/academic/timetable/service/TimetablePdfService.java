package com.dawnrise.academic.timetable.service;

import com.dawnrise.academic.timetable.dto.TimetableWorkflowDtos.PublishedEntry;
import com.dawnrise.academic.timetable.dto.TimetableWorkflowDtos.PublishedTimetable;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class TimetablePdfService {
    private static final PDType1Font BOLD=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font NORMAL=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float M=34;

    public byte[] published(PublishedTimetable timetable,String title){
        try(PDDocument document=new PDDocument();ByteArrayOutputStream output=new ByteArrayOutputStream()){
            Writer writer=new Writer(document,title,timetable);writer.start();
            for(PublishedEntry entry:timetable.entries())writer.entry(entry);
            writer.finish();document.save(output);return output.toByteArray();
        }catch(IOException exception){throw new IllegalStateException("Unable to generate timetable PDF",exception);}
    }

    private static final class Writer {
        private final PDDocument document;private final String title;private final PublishedTimetable timetable;
        private PDPage page;private PDPageContentStream stream;private float y;
        Writer(PDDocument document,String title,PublishedTimetable timetable){this.document=document;this.title=title;this.timetable=timetable;}
        void start()throws IOException{newPage();}
        void newPage()throws IOException{close();page=new PDPage(new PDRectangle(PDRectangle.A4.getHeight(),PDRectangle.A4.getWidth()));document.addPage(page);stream=new PDPageContentStream(document,page);y=page.getMediaBox().getHeight()-M;
            fill(new Color(17,42,78),M,y-54,page.getMediaBox().getWidth()-2*M,54);text(BOLD,17,Color.WHITE,M+16,y-23,timetable.schoolName());text(BOLD,13,Color.WHITE,M+16,y-42,title);
            y-=70;text(BOLD,9,new Color(17,42,78),M,y,"Academic year: "+safe(timetable.academicYearName())+"   Effective: "+timetable.effectiveFrom()+" to "+(timetable.effectiveToExclusive()==null?"ongoing":timetable.effectiveToExclusive()+" (exclusive)"));y-=18;header();}
        void header()throws IOException{fill(new Color(20,104,225),M,y-22,PDRectangle.A4.getHeight()-2*M,22);columns(BOLD,7,Color.WHITE,y-15,"Day / Time","Class","Activity / Subject","Teacher","Room");y-=22;}
        void entry(PublishedEntry e)throws IOException{if(y<58)newPage();fill(Color.WHITE,M,y-27,PDRectangle.A4.getHeight()-2*M,27);stroke(new Color(202,216,232),M,y-27,PDRectangle.A4.getHeight()-2*M,27);
            String activity=e.subjectName()==null?e.activityName():e.subjectName()+" - "+e.activityName();
            columns(NORMAL,7,new Color(28,43,62),y-16,DayOfWeek.of(e.dayOfWeek())+" "+e.startTime()+"-"+e.endTime(),e.gradeName()+" / "+e.sectionName(),activity,e.teacherDisplayName()==null?"-":e.teacherDisplayName(),e.roomName()==null?"-":e.roomName());y-=27;}
        void finish()throws IOException{close();int pages=document.getNumberOfPages();for(int i=0;i<pages;i++)try(PDPageContentStream footer=new PDPageContentStream(document,document.getPage(i),PDPageContentStream.AppendMode.APPEND,true,true)){draw(footer,NORMAL,7,new Color(94,110,130),M,18,"Generated "+timetable.generatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)+" ("+timetable.timeZoneId()+")   Page "+(i+1)+" of "+pages);}}
        void columns(PDType1Font font,float size,Color color,float baseline,String... values)throws IOException{float[] x={M+7,M+150,M+280,M+535,M+665};for(int i=0;i<values.length;i++)text(font,size,color,x[i],baseline,truncate(values[i],i==2?42:22));}
        void text(PDType1Font font,float size,Color color,float x,float baseline,String value)throws IOException{draw(stream,font,size,color,x,baseline,safe(value));}
        void fill(Color color,float x,float bottom,float width,float height)throws IOException{stream.setNonStrokingColor(color);stream.addRect(x,bottom,width,height);stream.fill();}
        void stroke(Color color,float x,float bottom,float width,float height)throws IOException{stream.setStrokingColor(color);stream.setLineWidth(.5f);stream.addRect(x,bottom,width,height);stream.stroke();}
        void close()throws IOException{if(stream!=null){stream.close();stream=null;}}
    }
    private static void draw(PDPageContentStream stream,PDType1Font font,float size,Color color,float x,float y,String value)throws IOException{stream.beginText();stream.setFont(font,size);stream.setNonStrokingColor(color);stream.newLineAtOffset(x,y);stream.showText(safe(value));stream.endText();}
    private static String safe(Object value){return value==null?"-":value.toString().replaceAll("[^\\x20-\\x7E]","-");}
    private static String truncate(String value,int length){String safe=safe(value);return safe.length()<=length?safe:safe.substring(0,length-1)+"...";}
}
