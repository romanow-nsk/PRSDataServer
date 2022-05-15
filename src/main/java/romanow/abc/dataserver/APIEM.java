package romanow.abc.dataserver;

import com.google.gson.Gson;
import retrofit2.http.POST;
import romanow.abc.core.DBRequest;
import romanow.abc.core.Pair;
import romanow.abc.core.UniException;
import romanow.abc.core.constants.Values;
import romanow.abc.core.constants.ValuesBase;
import romanow.abc.core.entity.*;
import romanow.abc.core.entity.baseentityes.JEmpty;
import romanow.abc.core.entity.baseentityes.JInt;
import romanow.abc.core.entity.baseentityes.JString;
import romanow.abc.core.entity.subjectarea.*;
import romanow.abc.core.entity.subjectarea.statemashine.Transition;
import romanow.abc.core.entity.subjectarea.statemashine.TransitionsFactory;
import romanow.abc.core.mongo.RequestStatistic;
import romanow.abc.dataserver.statemashine.I_ServerTransition;
import spark.Request;
import spark.Response;

import java.util.ArrayList;

public class APIEM extends APIBase {
    private EMDataServer db;

    public APIEM(EMDataServer db0) {
        super(db0);
        db = db0;
        spark.Spark.post("/api/exam/group/add", apiAddGroupToExam);
        spark.Spark.post("/api/exam/group/remove", apiRemoveGroupFromExam);
        spark.Spark.get("/api/exam/ticket/list", apiGetTicketsForExam);
        spark.Spark.get("/api/taking/ticket/list", apiGetTicketsForTaking);
        spark.Spark.post("/api/state/change",apiStateChange);
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
    RouteWrap apiAddGroupToExam = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamLong groupId = new ParamLong(req, res, "groupId");
            if (!groupId.isValid()) return null;
            ParamLong examId = new ParamLong(req, res, "examId");
            if (!examId.isValid()) return null;
            EMExam exam = new EMExam();
            if (!db.mongoDB.getById(exam, examId.getValue())) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Экзамен id=" + examId.getValue() + " не найден");
                return null;
                }
            EMGroup group = new EMGroup();
            if (!db.mongoDB.getById(group, groupId.getValue(), 1)) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Группа id=" + groupId.getValue() + " не найдена");
                return null;
            }
            EntityLinkList<EMGroup> groups = exam.getGroups();
            groups.createMap();
            if (groups.getById(groupId.getValue()) != null) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Группа уже есть в экзамене");
                return null;
            }
            EMDiscipline discipline = new EMDiscipline();
            if (!db.mongoDB.getById(discipline, exam.getEMDiscipline().getOid())) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Дисциплина id=" + exam.getEMDiscipline().getOid() + " не найдена");
                return null;
            }
            groups.add(groupId.getValue());
            db.mongoDB.update(exam);
            discipline.getGroups().add(groupId.getValue());
            db.mongoDB.update(discipline);
            int count = 0;
            for (EMStudent student : group.getStudents()) {
                if (student.getState() != Values.StudentStateNormal)
                    continue;
                EMTicket ticket = new EMTicket();
                ticket.setState(Values.TicketNotAllowed);
                ticket.setExcerciceRating(0);
                ticket.setSemesterRating(0);
                ticket.setQuestionRating(0);
                ticket.getStudent().setOid(student.getOid());
                ticket.getEMExam().setOid(exam.getOid());
                count++;
                db.mongoDB.add(ticket);
            }
            return new JInt(count);
        }
    };
    //------------------------------------------------------------------------------------------------
    RouteWrap apiRemoveGroupFromExam = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamLong groupId = new ParamLong(req, res, "groupId");
            if (!groupId.isValid()) return null;
            ParamLong examId = new ParamLong(req, res, "examId");
            if (!examId.isValid()) return null;
            EMExam exam = new EMExam();
            if (!db.mongoDB.getById(exam, examId.getValue(), 1)) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Экзамен id=" + examId.getValue() + " не найден");
                return null;
            }
            EMGroup group = new EMGroup();
            if (!db.mongoDB.getById(group, groupId.getValue(), 1)) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Группа id=" + groupId.getValue() + " не найдена");
                return null;
            }
            EntityLinkList<EMGroup> groups = exam.getGroups();
            groups.createMap();
            if (groups.getById(groupId.getValue()) == null) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Группа не найдена в экзамене");
                return null;
            }
            EMDiscipline discipline = new EMDiscipline();
            if (!db.mongoDB.getById(discipline, exam.getEMDiscipline().getOid())) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Дисциплина id=" + exam.getEMDiscipline().getOid() + " не найдена");
                return null;
            }
            EntityRefList<EMStudent> students = group.getStudents();
            students.createMap();
            for (EMTicket ticket : exam.getTickets()) {
                if (students.getById(ticket.getStudent().getOid()) == null)
                    continue;
                if (!ticket.enableToRemove()) {
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Группа уже сдает экзамен (назначена)");
                    return null;
                }
            }
            int count = 0;
            for (EMTicket ticket : exam.getTickets()) {
                if (students.getById(ticket.getStudent().getOid()) == null)
                    continue;
                db.mongoDB.remove(ticket);
                count++;
            }
            discipline.getGroups().createMap();
            discipline.getGroups().removeById(groupId.getValue());
            db.mongoDB.update(discipline);
            groups.removeById(groupId.getValue());
            db.mongoDB.update(exam);
            return new JInt(count);
            }
        };
    //-------------------------------------------------------------------------------------------------------------------
    public EntityRefList<EMStudent> getStudentsForExam(long oid) throws UniException {
        EMExam exam = new EMExam();
        if (!db.mongoDB.getById(exam,oid))
            throw UniException.user("Не найден экзамен id="+oid);
        EntityRefList<EMStudent> out = new EntityRefList<>();
        for(EntityLink<EMGroup> group : exam.getGroups()){
            EMGroup group2 = new EMGroup();
            if (!db.mongoDB.getById(group2,group.getOid(),2))
                throw UniException.user("Не найдена группа id="+oid);
            for(Entity entity : group2.getStudents())
                out.add((EMStudent)entity);
            }
        out.createMap();
        return out;
        }
    //------------------------------------------------------------------------------------------------
    RouteWrap apiGetTicketsForExam = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamLong examId = new ParamLong(req, res, "examId");
            if (!examId.isValid()) return null;
            ParamLong groupId = new ParamLong(req, res, "groupId");
            if (!groupId.isValid()) return null;
            try {
                EntityRefList<EMStudent> students = getStudentsForExam(examId.getValue());
                EMExam exam = new EMExam();
                if (!db.mongoDB.getById(exam,examId.getValue(),1)){
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Экзамен id=" + examId.getValue() + " не найден");
                    return null;
                    }
                for(int i=0;i<exam.getTickets().size();i++){
                    EMTicket ticket = exam.getTickets().get(i);
                    EMStudent student = students.getById(ticket.getStudent().getOid());
                    if (student==null){
                        db.createHTTPError(res, ValuesBase.HTTPRequestError, "Экзамен id=" + ticket.getStudent().getOid() + " не найден");
                        return null;
                        }
                    if (groupId.getValue()!=0 && student.getEMGroup().getOid()!=groupId.getValue()){
                        exam.getTickets().remove(i);
                        i--;
                        }
                    else
                        ticket.getStudent().setOidRef(student);
                        }
                return exam;
                } catch (UniException ee){
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, ee.toString());
                    return null;
                    }
            }
        };
    //------------------------------------------------------------------------------------------------
    RouteWrap apiGetTicketsForTaking = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamLong takingId = new ParamLong(req, res, "takingId");
            if (!takingId.isValid()) return null;
            try {
                EMExamTaking examTaking = new EMExamTaking();
                if (!db.mongoDB.getById(examTaking,takingId.getValue(),1)){
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Прием экзамена id=" + takingId.getValue() + " не найден");
                    return null;
                    }
                EntityRefList<EMStudent> students = getStudentsForExam(examTaking.getExam().getOid());
                for(EMTicket ticket : examTaking.getTickets()){
                    EMStudent student = students.getById(ticket.getStudent().getOid());
                    if (student==null){
                        db.createHTTPError(res, ValuesBase.HTTPRequestError, "Экзамен id=" + ticket.getStudent().getOid() + " не найден");
                        return null;
                        }
                    ticket.getStudent().setOidRef(student);
                    }
                return examTaking;
            } catch (UniException ee){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, ee.toString());
                return null;
            }
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


