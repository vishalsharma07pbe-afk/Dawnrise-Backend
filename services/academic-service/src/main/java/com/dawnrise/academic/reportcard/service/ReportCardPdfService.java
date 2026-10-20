package com.dawnrise.academic.reportcard.service;

import com.dawnrise.academic.reportcard.dto.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;
import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.util.Locale;

@Service
public class ReportCardPdfService {
    private static final float M=38,W=PDRectangle.A4.getWidth()-M*2;
    private static final Color NAVY=new Color(17,42,78),BLUE=new Color(20,104,225),CYAN=new Color(53,198,221),PALE=new Color(236,245,255),INK=new Color(28,43,62),MUTED=new Color(94,110,130),BORDER=new Color(202,216,232),GREEN=new Color(21,139,91),RED=new Color(198,55,65);
    private static final PDFont BOLD=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),NORMAL=new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    public byte[] create(ReportCardResponse card){
        try(PDDocument doc=new PDDocument();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            Writer w=new Writer(doc);w.newPage();w.header();w.details(card);w.summary(card);w.subjects(card);w.notice(card);w.close();w.footers();doc.save(out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Unable to generate report-card PDF",e);}
    }
    public String filename(ReportCardResponse c){return String.format(Locale.ROOT,"report-card-%d-%s.pdf",c.id(),safe(c.rollNumber()).replaceAll("[^A-Za-z0-9_-]","-"));}

    private static final class Writer{
        final PDDocument doc;PDPage page;PDPageContentStream s;float y;
        Writer(PDDocument doc){this.doc=doc;}
        void newPage()throws IOException{close();page=new PDPage(PDRectangle.A4);doc.addPage(page);s=new PDPageContentStream(doc,page);y=page.getMediaBox().getHeight()-M;}
        void header()throws IOException{
            fill(NAVY,M,y-92,W,92);fill(CYAN,M,y-5,W,5);fill(BLUE,M+18,y-67,48,48);text(BOLD,21,Color.WHITE,M+32,y-51,"D");
            text(BOLD,16,Color.WHITE,M+80,y-31,"DAWNRISE SCHOOL");text(NORMAL,8,new Color(190,214,242),M+80,y-47,"LEARNING. GROWTH. EXCELLENCE.");
            text(BOLD,14,Color.WHITE,M+350,y-38,"REPORT CARD");text(NORMAL,8,new Color(190,214,242),M+350,y-54,"OFFICIAL ACADEMIC RECORD");y-=111;
        }
        void details(ReportCardResponse c)throws IOException{
            title("STUDENT INFORMATION");fill(PALE,M,y-76,W,76);stroke(BORDER,M,y-76,W,76);
            field("Student",display(c.studentDisplayName()),M+14,y-23);field("Roll number",display(c.rollNumber()),M+270,y-23);
            field("Class",join(c.gradeName(),c.sectionName()),M+14,y-57);field("Academic year",display(c.academicYearName()),M+270,y-57);y-=92;
            field("Examination",display(c.examinationName()),M,y);field("Report status",display(c.status()),M+270,y);y-=28;
        }
        void summary(ReportCardResponse c)throws IOException{
            title("RESULT SUMMARY");float gap=8,bw=(W-gap*3)/4;
            box(M,bw,"TOTAL",number(c.totalObtainedMarks())+" / "+number(c.totalMaximumMarks()),BLUE);
            box(M+bw+gap,bw,"AGGREGATE",number(c.aggregatePercentage())+"%",CYAN.darker());
            box(M+(bw+gap)*2,bw,"BAND",display(c.overallGrade()),new Color(126,78,187));
            box(M+(bw+gap)*3,bw,"RESULT",c.passed()?"PASS":"FAIL",c.passed()?GREEN:RED);y-=72;
        }
        void subjects(ReportCardResponse c)throws IOException{
            title("SUBJECT PERFORMANCE");tableHead();
            for(ReportCardSubjectResponse r:c.subjects()){
                if(y<88){newPage();text(BOLD,12,NAVY,M,y-8,"SUBJECT PERFORMANCE (CONTINUED)");y-=24;tableHead();}
                row(r);
            }y-=14;
        }
        void tableHead()throws IOException{fill(NAVY,M,y-25,W,25);cells(BOLD,8,Color.WHITE,y-16,"SUBJECT","OBTAINED","OUT OF","PERCENTAGE","BAND","OUTCOME");y-=25;}
        void row(ReportCardSubjectResponse r)throws IOException{
            fill(Color.WHITE,M,y-31,W,31);stroke(BORDER,M,y-31,W,31);String subject=display(r.subjectName());if(subject.length()>27)subject=subject.substring(0,26)+"...";
            cells(NORMAL,8,INK,y-19,subject,r.absent()?"Absent":number(r.obtainedMarks()),number(r.maximumMarks()),r.percentage()==null?"-":number(r.percentage())+"%",display(r.gradeLabel()),r.absent()?"ABSENT":r.passed()?"PASS":"FAIL");y-=31;
        }
        void cells(PDFont f,float z,Color c,float base,String...v)throws IOException{float[]x={M+9,M+218,M+292,M+350,M+432,M+473};for(int i=0;i<v.length;i++)text(f,z,c,x[i],base,v[i]);}
        void notice(ReportCardResponse c)throws IOException{if(!c.partial())return;fill(new Color(255,246,221),M,y-38,W,38);stroke(new Color(229,170,43),M,y-38,W,38);text(BOLD,8,new Color(139,91,0),M+12,y-15,"PARTIAL RESULT");text(NORMAL,8,new Color(111,83,27),M+12,y-29,"This report was published with incomplete subject data.");}
        void title(String v)throws IOException{fill(BLUE,M,y-17,4,17);text(BOLD,10,NAVY,M+11,y-12,v);y-=24;}
        void field(String l,String v,float x,float b)throws IOException{text(BOLD,7,MUTED,x,b+9,l.toUpperCase(Locale.ROOT));text(BOLD,10,INK,x,b-5,v);}
        void box(float x,float w,String l,String v,Color a)throws IOException{fill(new Color(247,250,254),x,y-56,w,56);stroke(BORDER,x,y-56,w,56);fill(a,x,y-56,4,56);text(BOLD,7,MUTED,x+13,y-17,l);text(BOLD,13,a,x+13,y-39,v);}
        void footers()throws IOException{int n=doc.getNumberOfPages();for(int i=0;i<n;i++)try(PDPageContentStream f=new PDPageContentStream(doc,doc.getPage(i),PDPageContentStream.AppendMode.APPEND,true,true)){f.setStrokingColor(BORDER);f.moveTo(M,42);f.lineTo(M+W,42);f.stroke();draw(f,BOLD,8,NAVY,M,27,"DAWNRISE");draw(f,NORMAL,7,MUTED,M+55,27,"Academic report card");draw(f,NORMAL,7,MUTED,M+W-54,27,"Page "+(i+1)+" of "+n);}}
        void fill(Color c,float x,float b,float w,float h)throws IOException{s.setNonStrokingColor(c);s.addRect(x,b,w,h);s.fill();}
        void stroke(Color c,float x,float b,float w,float h)throws IOException{s.setStrokingColor(c);s.setLineWidth(.6f);s.addRect(x,b,w,h);s.stroke();}
        void text(PDFont f,float z,Color c,float x,float b,Object v)throws IOException{draw(s,f,z,c,x,b,display(v));}
        void close()throws IOException{if(s!=null){s.close();s=null;}}
    }
    private static void draw(PDPageContentStream s,PDFont f,float z,Color c,float x,float y,String v)throws IOException{s.beginText();s.setFont(f,z);s.setNonStrokingColor(c);s.newLineAtOffset(x,y);s.showText(safe(v));s.endText();}
    private static String display(Object v){return v==null||v.toString().isBlank()?"-":v.toString();}
    private static String number(BigDecimal v){return v==null?"-":v.stripTrailingZeros().toPlainString();}
    private static String join(String a,String b){return "-".equals(display(b))?display(a):display(a)+" - "+display(b);}
    private static String safe(String v){return display(v).replaceAll("[^\\x20-\\x7E]","-");}
}
