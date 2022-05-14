package romanow.abc.dataserver;

import romanow.abc.core.Pair;
import romanow.abc.core.UniException;
import romanow.abc.core.constants.Values;
import romanow.abc.core.constants.ValuesBase;
import romanow.abc.core.entity.*;
import romanow.abc.core.entity.baseentityes.JInt;
import romanow.abc.core.entity.subjectarea.*;
import romanow.abc.core.mongo.RequestStatistic;
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
        //spark.Spark.post("/api/EM/analyse", apiAnalyse);
        //spark.Spark.post("/api/EM/measure/expertnote/set",apiSetExpertNote);
        //spark.Spark.get("/api/EM/measure/select",apiSelectMeasures);
        //spark.Spark.post("/api/EM/measure/split", apiSplitMeasure);
    }

    //------------------------------------------------------------------------------------------------
    RouteWrap apiAddGroupToExam = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamLong groupId = new ParamLong(req, res, "groupId");
            if (!groupId.isValid()) return null;
            ParamLong examId = new ParamLong(req, res, "examId");
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
        for(EntityLink<EMGroup> groupId : exam.getGroups()){
            EMGroup group = new EMGroup();
            if (!db.mongoDB.getById(group,groupId.getOid(),2))
                throw UniException.user("Не найдена группа id="+oid);
            for(Entity entity : group.getStudents())
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
            try {
                EntityRefList<EMStudent> students = getStudentsForExam(examId.getValue());
                EMExam exam = new EMExam();
                if (!db.mongoDB.getById(exam,examId.getValue(),1)){
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Экзамен id=" + examId.getValue() + " не найден");
                    return null;
                    }
                for(EMTicket ticket : exam.getTickets()){
                    EMStudent student = students.getById(ticket.getStudent().getOid());
                    if (student==null){
                        db.createHTTPError(res, ValuesBase.HTTPRequestError, "Экзамен id=" + ticket.getStudent().getOid() + " не найден");
                        return null;
                        }
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


