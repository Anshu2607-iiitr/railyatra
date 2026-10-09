package com.railyatra.railway.importer;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class SyntheticRailwayDataGenerator {
  private static final int TRAIN_COUNT=5100, STATION_COUNT=650; private static final long SEED=20261002L;
  public RailwayDataSet generate() {
    Random random=new Random(SEED); var stations=new ArrayList<RailwayDataSet.StationRow>(); var trains=new ArrayList<RailwayDataSet.TrainRow>(); var stops=new ArrayList<RailwayDataSet.StopRow>(); var schedules=new ArrayList<RailwayDataSet.ScheduleRow>(); var classes=new ArrayList<RailwayDataSet.ClassRow>(); var coaches=new ArrayList<RailwayDataSet.CoachRow>(); var seats=new ArrayList<RailwayDataSet.SeatRow>(); var fares=new ArrayList<RailwayDataSet.FareRow>();
    for(int i=0;i<STATION_COUNT;i++){String code=String.format("S%04d",i);stations.add(new RailwayDataSet.StationRow(code,"Synthetic Junction "+i,"Synthetic City "+(i%100),"Synthetic State "+(i%30),"SYNTHETIC"));}
    for(int i=0;i<TRAIN_COUNT;i++){String number=String.format("%05d",12000+i);trains.add(new RailwayDataSet.TrainRow(number,"Synthetic Development Express "+number,"SYNTHETIC"));int stopCount=8+random.nextInt(5);int start=random.nextInt(STATION_COUNT-stopCount);int distance=0;for(int s=0;s<stopCount;s++){int increment=80+random.nextInt(35);if(s>0)distance+=increment;String time=String.format("%02d:%02d:00",(5+s*2)%24,(s*7)%60);stops.add(new RailwayDataSet.StopRow(number,String.format("S%04d",start+s),s+1,s==0?null:time,s==stopCount-1?null:time,s/10,distance));}schedules.add(new RailwayDataSet.ScheduleRow(number,"1111111","2026-01-01"));classes.add(new RailwayDataSet.ClassRow(number,"SL","Synthetic Sleeper"));classes.add(new RailwayDataSet.ClassRow(number,"3A","Synthetic AC 3 Tier"));for(String code:List.of("SL","3A")){String coach=code.equals("SL")?"S1":"B1";coaches.add(new RailwayDataSet.CoachRow(number,code,coach,1));for(int seat=1;seat<=8;seat++)seats.add(new RailwayDataSet.SeatRow(number,code,coach,String.valueOf(seat),seat%3==0?"UPPER":"LOWER"));fares.add(new RailwayDataSet.FareRow(number,code,String.format("S%04d",start),String.format("S%04d",start+stopCount-1),BigDecimal.valueOf(80),BigDecimal.valueOf(1.25),BigDecimal.valueOf(120)));}}
    return new RailwayDataSet(stations,trains,stops,schedules,classes,coaches,seats,fares);
  }
}
