package romanow.abc.dataserver.statemashine;

import romanow.abc.core.constants.Values;
import romanow.abc.core.entity.StateEntity;
import romanow.abc.core.entity.subjectarea.*;
import romanow.abc.dataserver.DataServer;

import java.util.HashMap;

public class EMStudRatingConfirmation implements I_ServerTransition {
    @Override
    public String onTransition(DataServer db, StateEntity entity) {
        EMStudRating rating = (EMStudRating) entity;
        try {
            EMExamTaking taking = new EMExamTaking();
            if (!db.mongoDB().getById(taking, rating.getEMExamTaking().getOid())){
                return "Не найден прием экзамена id="+rating.getEMExamTaking().getOid();
                }
            EMDiscipline discipline = new EMDiscipline();
            if (!db.mongoDB().getById(discipline,taking.getEMDiscipline().getOid(),2))
                return "Не найдена дисциплина id="+taking.getEMDiscipline().getOid();
            discipline.createMaps();
            EMGroupRating groupRating = discipline.getRatings().getById(rating.getEMGroupRating().getOid());    // Рейтинг группы
            if (groupRating==null){
                return "Не найден рейтинг группы id="+rating.getEMGroupRating().getOid();
                }
            EMExamRule rule = discipline.getRules().getById(groupRating.getRule().getOid());
            if (rule==null){
                return "Не найден регламент id="+groupRating.getRule().getOid();
                }
            rating.getAnswers().clear();                        // TODO - надо бы утилизовать
            HashMap<Long,Long> taskMap = new HashMap<>();
            int total=rule.getExamOwnRating();
            int sum = rule.getExcerciceRating();
            int defBall = rule.getOneExcerciceDefBall();
            while(sum>0){
                int idx = (int)(Math.random()*rule.getThemes().size());
                EMTheme theme = discipline.getThemes().getById(rule.getThemes().get(idx).getOid());
                int idx2 = (int)(Math.random()*theme.getTasks().size());
                EMTask task = theme.getTasks().get(idx2);
                if (task.getType()!= Values.TaskExercise)
                    continue;
                if (taskMap.get(task.getOid())!=null)
                    continue;
                EMAnswer answer = new EMAnswer();
                answer.getEMStudRating().setOid(rating.getOid());
                answer.getTask().setOid(task.getOid());
                taskMap.put(task.getOid(),task.getOid());
                long oid = db.mongoDB().add(answer);
                sum-=defBall;
                }
            sum = rule.getQuestionRating();
            defBall = rule.getOneQuestionDefBall();
            while(sum>0){
                int idx = (int)(Math.random()*rule.getThemes().size());
                EMTheme theme = discipline.getThemes().getById(rule.getThemes().get(idx).getOid());
                int idx2 = (int)(Math.random()*theme.getTasks().size());
                EMTask task = theme.getTasks().get(idx2);
                if (task.getType()!= Values.TaskQuestion)
                    continue;
                if (taskMap.get(task.getOid())!=null)
                    continue;
                EMAnswer answer = new EMAnswer();
                answer.getEMStudRating().setOid(rating.getOid());
                answer.getTask().setOid(task.getOid());
                taskMap.put(task.getOid(),task.getOid());
                long oid = db.mongoDB().add(answer);
                sum-=defBall;
                }
            return "";
            }catch (Exception ee){
                return "Ошибка БД: "+ee.toString();
                }
    }
}
