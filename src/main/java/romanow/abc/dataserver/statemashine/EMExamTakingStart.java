package romanow.abc.dataserver.statemashine;

import romanow.abc.core.constants.Values;
import romanow.abc.core.entity.StateEntity;
import romanow.abc.core.entity.subjectarea.*;
import romanow.abc.dataserver.DataServer;

public class EMExamTakingStart implements I_ServerTransition {
    @Override
    public String onTransition(DataServer db, StateEntity entity) {
        SAExamTaking taking = (SAExamTaking) entity;
        try {
            SAExamTaking taking2 = new SAExamTaking();
            db.mongoDB().getById(taking2, taking.getOid(), 1);
            for(SAStudRating rating : taking2.getRatings()){
                if (rating.getState()== Values.StudRatingTakingSet){
                    rating.setState(Values.StudRatingConfirmation);
                    db.mongoDB().update(rating);
                    }
                }
            }catch (Exception ee){
                return "Ошибка БД: "+ee.toString();
                }
        return "";
    }
}
