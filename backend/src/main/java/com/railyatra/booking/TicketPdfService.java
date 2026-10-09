package com.railyatra.booking;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.railyatra.auth.AuthPrincipal;
import com.railyatra.railway.domain.TrainStop;
import com.railyatra.railway.repository.TrainStopRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketPdfService {
    private final BookingRepository bookings; private final BookingPassengerRepository passengers; private final TrainStopRepository stops; private final FareCalculatorService fares; private final String verificationBaseUrl;
    public TicketPdfService(BookingRepository bookings, BookingPassengerRepository passengers, TrainStopRepository stops, FareCalculatorService fares, @Value("${railyatra.ticket-verification-base-url:http://localhost:5173/pnr?reference=}") String verificationBaseUrl) { this.bookings=bookings;this.passengers=passengers;this.stops=stops;this.fares=fares;this.verificationBaseUrl=verificationBaseUrl; }

    @Transactional(readOnly = true)
    public byte[] generate(AuthPrincipal principal, String reference) {
        Booking booking=bookings.findByInternalBookingReference(reference.trim().toUpperCase()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found"));
        if(!booking.getUserId().equals(principal.id())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found");
        List<BookingPassenger> travellers=passengers.findByBookingIdOrderByPassengerIndexAsc(booking.getId());
        TrainStop boarding=stops.findByTrainIdAndStationStationCode(booking.getTrain().getId(),booking.getOriginStation().getStationCode()).orElseThrow(()->new IllegalStateException("Boarding stop missing"));
        TrainStop arrival=stops.findByTrainIdAndStationStationCode(booking.getTrain().getId(),booking.getDestinationStation().getStationCode()).orElseThrow(()->new IllegalStateException("Arrival stop missing"));
        FareResponse fare=fares.calculate(booking.getTrain(),booking.getTrainClass(),booking.getOriginStation().getStationCode(),booking.getDestinationStation().getStationCode(),travellers.size());
        try(PDDocument pdf=new PDDocument(); ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            PDPage page=new PDPage(PDRectangle.A4); pdf.addPage(page);
            try(PDPageContentStream out=new PDPageContentStream(pdf,page)) {
                float y=800;
                y=title(out,"RailYatra",y); y=line(out,"E-TICKET - Internal reservation reference",y);
                if("LOCAL".equalsIgnoreCase(booking.getProviderType())) { y-=4; y=small(out,"This is a RailYatra development/internal reservation and is not an official Indian Railways/IRCTC ticket.",y); }
                y-=8; y=label(out,"RailYatra Reference",booking.getInternalBookingReference(),y); y=label(out,"Booking timestamp",booking.getCreatedAt().toString(),y);
                y-=6; y=section(out,"Journey",y); y=label(out,"Train",booking.getTrain().getTrainNumber()+" - "+booking.getTrain().getTrainName(),y); y=label(out,"Origin / destination",booking.getOriginStation().getStationCode()+" -> "+booking.getDestinationStation().getStationCode(),y); y=label(out,"Boarding station",booking.getOriginStation().getStationName(),y); y=label(out,"Journey date",booking.getJourneyDate().format(DateTimeFormatter.ISO_DATE),y); y=label(out,"Departure / arrival",time(boarding.getDepartureTime())+" -> "+time(arrival.getArrivalTime()),y); y=label(out,"Class / quota",booking.getTrainClass().getClassCode()+" - "+booking.getQuota(),y);
                y-=6; y=section(out,"Passengers",y); y=small(out,"Name                         Age/Gender       Coach / Seat / Berth       Booking / Current",y);
                for(BookingPassenger passenger:travellers){ y=small(out,fit(passenger.getPassengerName(),26)+"  "+passenger.getAge()+"/"+passenger.getGender()+"       "+(passenger.isCancelled()?"Cancelled":seat(passenger))+"       "+status(booking.getBookingStatus(),booking.getBookingStatusPosition())+" / "+(passenger.isCancelled()?"CANCELLED":status(booking.getCurrentStatus(),booking.getCurrentStatusPosition())),y); }
                y-=6; y=section(out,"Fare and payment",y); y=label(out,"Base fare",money(fare.baseFare()),y); y=label(out,"Reservation charge",money(fare.reservationCharge()),y); y=label(out,"Tax",money(fare.tax()),y); y=label(out,"Service charge",money(fare.serviceCharge()),y); y=label(out,"Total",money(fare.total()),y); y=label(out,"Payment reference",booking.getPaymentReference(),y);
                String verification=verificationBaseUrl+booking.getInternalBookingReference(); BufferedImage image=MatrixToImageWriter.toBufferedImage(new QRCodeWriter().encode(verification, BarcodeFormat.QR_CODE,110,110)); out.drawImage(LosslessFactory.createFromImage(pdf,image),440,48,110,110); smallAt(out,"Internal ticket verification",440,40);
            }
            pdf.save(bytes); return bytes.toByteArray();
        } catch(Exception error) { throw new IllegalStateException("Could not generate ticket PDF",error); }
    }
    private float title(PDPageContentStream out,String value,float y)throws java.io.IOException{out.beginText();out.setFont(PDType1Font.HELVETICA_BOLD,23);out.newLineAtOffset(45,y);out.showText(value);out.endText();return y-31;}
    private float section(PDPageContentStream out,String value,float y)throws java.io.IOException{out.beginText();out.setFont(PDType1Font.HELVETICA_BOLD,12);out.newLineAtOffset(45,y);out.showText(value);out.endText();return y-18;}
    private float label(PDPageContentStream out,String label,String value,float y)throws java.io.IOException{out.beginText();out.setFont(PDType1Font.HELVETICA_BOLD,9);out.newLineAtOffset(45,y);out.showText(label+": ");out.setFont(PDType1Font.HELVETICA,9);out.showText(fit(value,90));out.endText();return y-14;}
    private float line(PDPageContentStream out,String value,float y)throws java.io.IOException{return small(out,value,y);}
    private float small(PDPageContentStream out,String value,float y)throws java.io.IOException{out.beginText();out.setFont(PDType1Font.HELVETICA,8);out.newLineAtOffset(45,y);out.showText(fit(value,110));out.endText();return y-12;}
    private void smallAt(PDPageContentStream out,String value,float x,float y)throws java.io.IOException{out.beginText();out.setFont(PDType1Font.HELVETICA,7);out.newLineAtOffset(x,y);out.showText(value);out.endText();}
    private String time(java.time.LocalTime value){return value==null?"-":value.toString();} private String money(java.math.BigDecimal value){return "INR "+value;} private String fit(String value,int max){String safe=(value==null?"-":value).replaceAll("[^\\x20-\\x7E]", "?");return safe.length()<=max?safe:safe.substring(0,Math.max(0,max-3))+"...";} private String seat(BookingPassenger p){return p.getCoachCode()==null?"Pending":p.getCoachCode()+" / "+p.getSeatNumber()+" / "+p.getBerthType();} private String status(BookingStatus value,Integer pos){return pos==null?value.name():(value==BookingStatus.WAITLISTED?"WL":value.name())+" "+pos;}
}
