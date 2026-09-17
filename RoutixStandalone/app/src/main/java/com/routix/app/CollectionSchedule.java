package com.routix.app;

import java.time.LocalDate;
import java.time.temporal.IsoFields;

final class CollectionSchedule {
    static boolean occurs(LocalDate date,int weekdays,int parity){
        return (weekdays&(1<<(date.getDayOfWeek().getValue()-1)))!=0 && (parity==0||date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)%2==(parity==1?0:1));
    }
}
