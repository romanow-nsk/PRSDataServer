package romanow.abc.dataserver;

import com.google.gson.Gson;
import romanow.abc.core.DBRequest;
import romanow.abc.core.UniException;
import romanow.abc.core.constants.Values;
import romanow.abc.core.constants.ValuesBase;
import romanow.abc.core.entity.*;
import romanow.abc.core.entity.baseentityes.JEmpty;
import romanow.abc.core.entity.baseentityes.JInt;
import romanow.abc.core.entity.baseentityes.JLong;
import romanow.abc.core.entity.subjectarea.*;
import romanow.abc.core.entity.subjectarea.statemashine.Transition;
import romanow.abc.core.entity.subjectarea.statemashine.TransitionsFactory;
import romanow.abc.core.mongo.RequestStatistic;
import romanow.abc.dataserver.statemashine.I_ServerTransition;
import spark.Request;
import spark.Response;

public class APIEM extends APIBase {
    private EMDataServer db;

    public APIEM(EMDataServer db0) {
        super(db0);
        db = db0;
        spark.Spark.post("/api/rating/group/add", apiAddGroupRating);
        spark.Spark.post("/api/rating/group/remove", apiRemoveGroupRating);
        spark.Spark.get("/api/rating/group/get", apiGetGroupRatings);
        spark.Spark.get("/api/rating/taking/get", apiGetTakingRatings);
        spark.Spark.post("/api/state/change",apiStateChange);
        spark.Spark.post("/api/rating/takingforall", apiSetTakingForAll);
        }
    RouteWrap apiStateChange = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamBody dbReq = new ParamBody(req, res, DBRequest.class);
            if (!dbReq.isValid()) return null;
            StateEntity entity = null;
            DBRequest dbRequest = (DBRequest) dbReq.getValue();
            try {
                entity = (StateEntity) dbRequest.get(new Gson());
                } catch (Exception ee){
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Ошибка создания объекта для автомата "+dbRequest.getClassName()+"\n"+ee.toString());
                    return null;
                    }
            String stateClass = dbRequest.getClassName();
            long oid = entity.getOid();
            TransitionsFactory factory = Values.stateFactoryMap.get(stateClass);
            if (factory==null){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Не найден автомат "+stateClass);
                return null;
                }
            Class clazz = Values.EntityFactory().getClassForSimpleName(stateClass);
            if (clazz==null){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Не найден класс для автомата  "+stateClass);
                return null;
                }
            StateEntity oldEntity = (StateEntity)clazz.newInstance();
            if (!db.mongoDB.getById(oldEntity,oid)){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Не найден объект "+stateClass+" id="+oid);
                return null;
                }
            Transition transition = factory.getByState(oldEntity.getState(),entity.getState());
            if (transition==null){
                if (transition==null){
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Не найдена функция перехода  "+oldEntity.getState()+"->"+entity.getState());
                    return null;
                    }
                }
            try {
                Class cls = Class.forName("romanow.abc.dataserver.statemashine."+factory.name+transition.transName);
                I_ServerTransition transition1 = (I_ServerTransition) cls.newInstance();
                String rez = transition1.onTransition(db,entity);
                if (rez.length()!=0){
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, rez);
                    return null;
                    }
                db.mongoDB.update(entity);
                }catch (Exception ee){
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Ошибка создания класса для автомата "+factory.name+transition.transName+"n"+ee.toString());
                    return null;
                    }
            return new JEmpty();
            }
        };
    //------------------------------------------------------------------------------------------------
    RouteWrap apiAddGroupRating = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamBody groupRating = new ParamBody(req, res, EMGroupRating.class);
            if (!groupRating.isValid()) return null;
            EMGroupRating rating = (EMGroupRating)groupRating.getValue();
            long groupId = rating.getGroup().getOid();
            long ruleId = rating.getRule().getOid();
            long discId = rating.getEMDiscipline().getOid();
            EMGroup group = new EMGroup();
            if (!db.mongoDB.getById(group,groupId,2)){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Группа id=" + groupId + " не найдена");
                return null;
                }
            EMExamRule rule = new EMExamRule();
            if (!db.mongoDB.getById(rule,ruleId)){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Регламент id=" + ruleId + " не найден");
                return null;
                }
            EMDiscipline discipline = new EMDiscipline();
            if (!db.mongoDB.getById(discipline,discId,1)){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Дисциплина id=" + discId + " не найдена");
                return null;
                }
            for(EMGroupRating groupRating1 : discipline.getRatings()){
                if (groupRating1.getGroup().getOid()==groupId){
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Повторное добавление рейтинга дисциплина-группа");
                    return null;
                    }
                }
            if (rule.getEMDiscipline().getOid()!=discId){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Регламент "+rule.getName()+" не от дисциплины " + discipline.getName());
                return null;
                }
            rating.setName(discipline.getName()+"-"+group.getName());
            long oid = db.mongoDB.add(rating);
            int count = 0;
            for (EMStudent student : group.getStudents()) {
                if (student.getState() != Values.StudentStateNormal)
                    continue;
                EMStudRating ticket = new EMStudRating();
                ticket.setState(Values.StudRatingNotAllowed);
                ticket.setExcerciceRating(0);
                ticket.setSemesterRating(0);
                ticket.setQuestionRating(0);
                ticket.getStudent().setOid(student.getOid());
                ticket.getEMGroupRating().setOid(oid);
                count++;
                db.mongoDB.add(ticket);
                }
            return new JLong(oid);
        }
    };
    //------------------------------------------------------------------------------------------------
    RouteWrap apiRemoveGroupRating = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamLong ratingId = new ParamLong(req, res, "ratingId");
            if (!ratingId.isValid()) return null;
            EMGroupRating rating = new EMGroupRating();
            if (!db.mongoDB.getById(rating, ratingId.getValue(), 1)) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Рейтинг группы  id=" + ratingId.getValue() + " не найден");
                return null;
                }
            EMGroup group = new EMGroup();
            if (!db.mongoDB.getById(group, rating.getGroup().getOid(), 1)) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Группа id=" + rating.getGroup().getOid() + " не найдена");
                return null;
                }
            EMDiscipline discipline = new EMDiscipline();
            if (!db.mongoDB.getById(discipline, rating.getEMDiscipline().getOid(), 1)) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Дисциплина id=" + rating.getEMDiscipline().getOid() + " не найдена");
                return null;
                }
            for(EMExamTaking taking : discipline.getTakings()){
                if (taking.isOneGroup() && taking.getGroup().getOid()==group.getOid()){
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Группа " + group.getName() + " назначена на экзамен");
                    return null;
                    }
                }
            EntityRefList<EMStudent> students = group.getStudents();
            students.createMap();
            for (EMStudRating ticket : rating.getRatings()) {
                if (students.getById(ticket.getStudent().getOid()) == null)
                    continue;
                if (!ticket.enableToRemove()) {
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Группа уже сдает экзамен (назначена)");
                    return null;
                    }
                }
            int count = 0;
            for (EMStudRating ticket : rating.getRatings()) {
                if (students.getById(ticket.getStudent().getOid()) == null)
                    continue;
                db.mongoDB.remove(ticket);
                count++;
                }
            db.mongoDB.remove(rating);
            return new JEmpty();
                }
        };
    //------------------------------------------------------------------------------------------------
    RouteWrap apiSetTakingForAll = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamLong takingId = new ParamLong(req, res, "takingId");
            if (!takingId.isValid()) return null;
            EMExamTaking taking = new EMExamTaking();
            if (!db.mongoDB.getById(taking, takingId.getValue(), 1)) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Прием экзамена  id=" + takingId.getValue() + " не найден");
                return null;
                }
            EMDiscipline discipline = new EMDiscipline();
            if (!db.mongoDB.getById(discipline, taking.getEMDiscipline().getOid(),1)) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Дисциплина id=" + taking.getEMDiscipline().getOid() + " не найдена");
                return null;
                }
            discipline.createMaps();
            int count=0;
            for(EMGroupRating rating : discipline.getRatings()){
                if (taking.isOneGroup() && taking.getGroup().getOid()!=rating.getGroup().getOid())
                    continue;
                db.mongoDB.getById(rating,rating.getOid(),1);
                for(EMStudRating studRating : rating.getRatings())
                    if (studRating.getState()==Values.StudRatingAllowed){
                        studRating.setState(Values.StudRatingTakingSet);
                        studRating.getEMExamTaking().setOid(taking.getOid());
                        db.mongoDB.update(studRating);
                        count++;
                    }
                }
            return new JInt(count);
            }
        };
    //------------------------------------------------------------------------------------------------
    RouteWrap apiGetGroupRatings = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamLong ratingId = new ParamLong(req, res, "ratingId");
            if (!ratingId.isValid()) return null;
            EMGroupRating rating = new EMGroupRating();
            if (!db.mongoDB.getById(rating, ratingId.getValue(), 1)) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Рейтинг группы  id=" + ratingId.getValue() + " не найден");
                return null;
                }
            EMGroup group = new EMGroup();
            if (!db.mongoDB.getById(group, rating.getGroup().getOid(), 2)) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Группа id=" + rating.getGroup().getOid() + " не найдена");
                return null;
                }
            EntityRefList<EMStudent> students = group.getStudents();
            students.createMap();
            for (EMStudRating ticket : rating.getRatings()) {
                EMStudent student = students.getById(ticket.getStudent().getOid());
                if (student == null) {
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Студент id=" + ticket.getStudent().getOid() + " не найден");
                    return null;
                    }
                ticket.getStudent().setOidRef(student);
                }
            return rating;
            }
        };
    //------------------------------------------------------------------------------------------------
    RouteWrap apiGetTakingRatings = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamLong takingId = new ParamLong(req, res, "takingId");
            if (!takingId.isValid()) return null;
            EMExamTaking examTaking = new EMExamTaking();
            if (!db.mongoDB.getById(examTaking,takingId.getValue(),1)){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Прием экзамена id=" + takingId.getValue() + " не найден");
                return null;
                }
            for (EMStudRating ticket : examTaking.getRatings()) {
                EMStudent student = new EMStudent();
                if (!db.mongoDB.getById(student,ticket.getStudent().getOid(),1))
                if (student == null) {
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Студент id=" + ticket.getStudent().getOid() + " не найден");
                    return null;
                    }
                ticket.getStudent().setOidRef(student);
                }
            return examTaking;
        }
    };
    /*
    public EntityList<Entity> getByCondition(Entity entity, String fname, String value, int level) throws UniException {
    List<BasicDBObject> obj = new ArrayList<BasicDBObject>();
        obj.add(new BasicDBObject(fname, value));
        obj.add(new BasicDBObject("valid",true));
        BasicDBObject query = new BasicDBObject();
        query.put("$and", obj);
        EntityList<Entity> zz = db.mongoDB.getAllByQuery(entity,query,level);
        return zz;
        }
    //------------------------------------------------------------------------------------------------
    public EntityList<Entity> getByCondition(Entity entity, String fname, long value, int level) throws UniException {
        List<BasicDBObject> obj = new ArrayList<BasicDBObject>();
        obj.add(new BasicDBObject(fname, value));
        obj.add(new BasicDBObject("valid",true));
        BasicDBObject query = new BasicDBObject();
        query.put("$and", obj);
        EntityList<Entity> zz = db.mongoDB.getAllByQuery(entity,query,level);
        return zz;
        }
    //------------------------------------------------------------------------------------------------
    public EntityList<Entity> getByCondition(Entity entity, String fname, int value, int level) throws UniException {
        List<BasicDBObject> obj = new ArrayList<BasicDBObject>();
        obj.add(new BasicDBObject(fname, value));
        obj.add(new BasicDBObject("valid",true));
        BasicDBObject query = new BasicDBObject();
        query.put("$and", obj);
        EntityList<Entity> zz = db.mongoDB.getAllByQuery(entity,query,level);
        return zz;
        }
    public EntityList<Entity> getByName(Entity entity, String value, int level) throws UniException {
        return getByCondition(entity,"name",value,level);
        }
    RouteWrap apiSelectMeasures = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            List<BasicDBObject> obj = new ArrayList<BasicDBObject>();
            ParamInt note = new ParamInt(req, res, "note",0);
            if (!note.isValid())
                return null;
            if (note.getValue()!=0)
                obj.add(new BasicDBObject("expertResult", note.getValue()));
            ParamLong userId = new ParamLong(req, res, "userId",0);
            if (!userId.isValid())
                return null;
            if (userId.getValue()!=0)
                obj.add(new BasicDBObject("userId", userId.getValue()));
            ParamString line = new ParamString(req,res,"line","");
            if (!line.isValid())
                return null;
            if (line.getValue().length()!=0)
                obj.add(new BasicDBObject("powerLineName",line.getValue()));
            ParamString support = new ParamString(req,res,"support","");
            if (!support.isValid())
                return null;
            if (support.getValue().length()!=0)
                obj.add(new BasicDBObject("supportName",support.getValue()));
            obj.add(new BasicDBObject("valid",true));
            BasicDBObject query = new BasicDBObject();
            query.put("$and", obj);
            EntityList<Entity> zz = db.mongoDB.getAllByQuery(new MeasureFile(),query,1);
            return zz;
        }
    };

     */
}


