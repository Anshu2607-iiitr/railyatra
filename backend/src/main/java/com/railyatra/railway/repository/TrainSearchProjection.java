package com.railyatra.railway.repository;
import java.time.LocalTime;
public interface TrainSearchProjection { String getTrainNumber(); String getTrainName(); String getTrainType(); String getRunningDays(); String getDataSource(); String getOriginCode(); String getOriginName(); String getDestinationCode(); String getDestinationName(); LocalTime getDepartureTime(); LocalTime getArrivalTime(); int getOriginSequence(); int getDestinationSequence(); }
