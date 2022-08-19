package romanow.abc.dataserver.statemashine;

import romanow.abc.core.constants.Values;
import romanow.abc.core.entity.StateEntity;
import romanow.abc.core.entity.subjectarea.SAAnswer;
import romanow.abc.core.entity.subjectarea.SAExamTaking;
import romanow.abc.core.entity.subjectarea.SAExamRating;
import romanow.abc.dataserver.DataServer;

public class SaExamTakingClose implements I_ServerTransition{
    @Override
    public String onTransition(DataServer db, StateEntity entity) {
        try {
            SAExamTaking taking = (SAExamTaking) entity;
            SAExamTaking full = new SAExamTaking();
            if (!db.mongoDB().getById(full,taking.getOid(),2)){
                return "Ошибка чтения данных приема экзамена id="+taking.getTitle();
                }
            for(SAExamRating rating : full.getRatings()){
                int stateStud = rating.getState();
                if (stateStud == Values.StudRatingConfirmation){
                    rating.setState(Values.StudRatingNoConfirmation);
                    db.mongoDB().update(rating);
                    continue;
                    }
                boolean onExam = stateStud==Values.StudRatingOnExam || stateStud==Values.StudRatingPassedExam;
                if (!onExam)
                    continue;
                rating.setState(Values.StudRatingGotRating);
                db.mongoDB().update(rating);
                for(SAAnswer answer : rating.getAnswers()){
                    int state = answer.getState();
                    if (!(state== Values.AnswerNoAck || state==Values.AnswerRatingIsSet)){
                        answer.setState(Values.AnswerRatingNotSet);
                        db.mongoDB().update(answer);
                        }
                    }
                }
            return "";
            } catch (Exception ee){
                return "Ошибка БД: "+ee.toString();
                }
        }
}
