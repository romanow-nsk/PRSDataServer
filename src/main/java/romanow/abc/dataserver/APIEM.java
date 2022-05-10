package romanow.abc.dataserver;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


public class APIEM extends APIBase {
    private EMDataServer db;

    public APIEM(EMDataServer db0) {
        super(db0);
        db = db0;
        //spark.Spark.post("/api/EM/measure/add", apiAddMeasure);
        //spark.Spark.post("/api/EM/analyse", apiAnalyse);
        //spark.Spark.post("/api/EM/measure/expertnote/set",apiSetExpertNote);
        //spark.Spark.get("/api/EM/measure/select",apiSelectMeasures);
        //spark.Spark.post("/api/EM/measure/split", apiSplitMeasure);
        }

    //------------------------------------------------------------------------------------------------
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
    //------------------------------------------------------------------------------------------------
    RouteWrap apiAddMeasure = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamLong artId = new ParamLong(req, res, "artId");
            if (!artId.isValid()) return null;
            Artifact art = new Artifact();
            if (!db.mongoDB.getById(art,artId.getValue())){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Артефакт id="+artId.getValue()+" не найден");
                return null;
                }
            MeasureFile measure = new MeasureFile();
            Pair<String, FileDescription> pair = measure.loadMetaData(art,db.dataServerFileDir());
            if (pair.o1!=null){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, pair.o1);
                return null;
                }
            measure.setPowerLineName(pair.o2.getPowerLine());
            measure.setSupportName(pair.o2.getSupport());
            EntityList<Entity> list = getByName(new PowerLine(),pair.o2.getPowerLine(),0);
            PowerLine powerLine;
            if (list.size()==0){
                powerLine = new PowerLine();
                powerLine.setName(pair.o2.getPowerLine());
                db.mongoDB.add(powerLine);
                }
            else
                powerLine = (PowerLine) list.get(0);
            Support group=null;
            EntityList<Entity> list2 = getByName(new Support(),pair.o2.getSupport(),0);
            if (list2.size()==0){
                group = new Support();
                group.setName(pair.o2.getSupport());
                group.getPowerLine().setOid(powerLine.getOid());
                db.mongoDB.add(group);
                }
            else{
                for(Entity ent2 : list2){
                    Support pp = (Support) ent2;
                    if (pp.getPowerLine().getOid()==powerLine.getOid()){    // Найдена опопра с той же линии и с именем
                        group = pp;
                        break;
                        }
                    }
                }
            if (group==null){       // Не найдена опора в группе
                group = new Support();
                group.setName(pair.o2.getSupport());
                group.getPowerLine().setOid(powerLine.getOid());
                db.mongoDB.add(group);
                }
            measure.getSupport().setOid(group.getOid());
            System.out.println(pair.o2.measureMetaData());
            UserContext uu = db.getSession(req,res,false);
            measure.setUserID(uu==null ? 0 : uu.getUser().getOid());
            long oid = db.mongoDB.add(measure);
            return measure;
            }
        };
    //-------------------------------------------------------------------------------------------------------
    RouteWrap apiSetExpertNote = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamLong paramId = new ParamLong(req, res, "measureId");
            if (!paramId.isValid()) return null;
            ParamInt note = new ParamInt(req, res, "note");
            if (!note.isValid()) return null;
            MeasureFile file = new MeasureFile();
            if (!db.mongoDB.getById(file,paramId.getValue(),1)){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Измерение id="+paramId.getValue()+" не найдено");
                return null;
                }
                file.setExpertResult(note.getValue());
                db.mongoDB.update(file);
                Artifact art = file.getArtifact().getRef();
                String ss = art.createArtifactServerPath();
                FileDescription fd = new FileDescription("");
                fd.setExpertNote(note.getValue());
                fd.setOriginalFileName(ss);
                String cc = fd.refreshExpertNoteInFile(db.dataServerFileDir());
                if (cc!=null) {
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, cc);
                    return null;
                    }
            return new JEmpty();
            }
        };
    //-------------------------------------------------------------------------------------------------------
    RouteWrap apiSplitMeasure = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamBoolean  size32768 = new ParamBoolean(req, res, "size32768");
            if (!size32768.isValid()) return null;
            ParamInt  startOver = new ParamInt(req, res, "startOver");
            if (!startOver.isValid()) return null;
            ParamInt  startLevelProc = new ParamInt(req, res, "startLevelProc");
            if (!startLevelProc.isValid()) return null;
            ParamInt  skipTimeMS = new ParamInt(req, res, "skipTimeMS");
            if (!skipTimeMS.isValid()) return null;
            ParamLong paramId = new ParamLong(req, res, "measureId");
            if (!paramId.isValid()) return null;
            MeasureFile measure = new MeasureFile();
            if (!db.mongoDB.getById(measure, paramId.getValue(), 1)) {
                db.createHTTPError(res, ValuesBase.HTTPNotFound, "Файл измерений id=" + paramId.getValue() + " не найден");
                return null;
                }
            Artifact art = measure.getArtifact().getRef();
            FileDescription fd = new FileDescription(art.getOriginalName());
            String error = fd.getFormatError();
            String ss = db.dataServerFileDir()+"/"+art.createArtifactServerPath();
            BufferedReader reader = null;
            FFTAudioTextFile file = new FFTAudioTextFile();
            try {
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(ss), "Windows-1251"));
                } catch (IOException ex){
                    db.createHTTPError(res, ValuesBase.HTTPNotFound, "Ошибка чтения файла для измерения id="+paramId.getValue());
                    return null;
                    }
            file.readData(fd, reader,true);
            ArrayList<FFTAudioTextFile> list = file.splitMeasure(size32768.getValue(),startOver.getValue(),startLevelProc.getValue(),skipTimeMS.getValue(),fd.getFileFreq());
            if (list.size()==0){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Отсутствуют (короткие) интервалы возбуждения");
                return null;
                }
            MFSelection selection = new MFSelection();
            selection.setName("Нарезка: "+fd.toString());
            ArrayList<MeasureFile> out = new ArrayList<>();
            String origName = art.getOriginal().getName();
            int origCounter = measure.getMeasureCounter();
            for(int i=0;i<list.size();i++){
                FFTAudioTextFile file2 = list.get(i);
                fd.setMeasureCounter(origCounter*1000+i+1);
                FileNameExt fileNameExt = art.getOriginal();
                fileNameExt.setName(origName+"+"+(i+1));
                long artId = db.mongoDB.add(art);
                file2.save(true,db.dataServerFileDir()+"/"+art.createArtifactServerPath(),fd,new I_EventListener() {
                    @Override
                    public void onEvent(String ss) {
                        System.out.println(ss);
                    }
                    });
                measure.getArtifact().setOid(artId);
                measure.setMeasureCounter(origCounter*1000+i+1);
                long measOid = db.mongoDB.add(measure);
                MeasureFile mFile = new MeasureFile();
                db.mongoDB.getById(mFile,measOid);
                selection.getFiles().addOidRef(mFile);
                }
            UserContext uu = db.getSession(req,res,false);
            selection.getUser().setRef(uu==null ? null : uu.getUser());
            selection.getUser().setOid(uu==null ? 0 : uu.getUser().getOid());
            db.mongoDB.add(selection);
            return selection;
            }
        };
    //-------------------------------------------------------------------------------------------------------
    RouteWrap apiAnalyse = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamBody qq = new ParamBody(req, res, OidList.class);
            if (!qq.isValid())
                return null;
            EMParams params = new EMParams();
            ParamLong paramId = new ParamLong(req, res, "paramId");
            if (!paramId.isValid()) return null;
            if (!db.mongoDB.getById(params, paramId.getValue())) {
                db.createHTTPError(res, ValuesBase.HTTPNotFound, "Набор параметров id=" + paramId.getValue() + " не найден");
                return null;
                }
            OidList dd = (OidList) qq.getValue();
            ArrayList<AnalyseResult> out = new ArrayList<>();
            ArrayList<FFTStatistic> list = new ArrayList<>();
            ArrayList<MeasureFile> measureFiles = new ArrayList<>();
            for (long oid : dd.oids) {
                MeasureFile measure = new MeasureFile();
                if (!db.mongoDB.getById(measure, oid, 1)) {
                    db.createHTTPError(res, ValuesBase.HTTPNotFound, "Файл измерений id=" + oid + " не найден");
                    return null;
                    }
                FFTStatistic pair = processInputStream(true,params,measure.getArtifact().getRef(),measure.getArtifact().getTitle());
                list.add(pair);
                measureFiles.add(measure);
                }
            double max=0;
            for(FFTStatistic stat : list){
                double max2 = stat.normalizeStart(params);
                //System.out.println(String.format("max before=%6.3f",max2));
                if (max2 > max)
                    max = max2;
                }
            for(FFTStatistic stat : list) {
                stat.normalizeFinish((float) max);
                //System.out.println(String.format("max after=%6.3f",stat.normalizeGet()));
                }
            for(int i=0;i<list.size();i++) {
                FFTStatistic stat = list.get(i);
                AnalyseResult result = new AnalyseResult(stat,measureFiles.get(i));
                result.setTitle(measureFiles.get(i).getTitle());
                if (stat.isValid()){
                    for (int mode = 0; mode < Values.extremeFacade.length; mode++) {
                        ExtremeList elist = stat.createExtrems(mode, params);
                        elist.testAlarm2(params, stat.getFreqStep());
                        result.data.add(elist);
                        }
                    }
                out.add(result);
                }
            return out;
        }
    };
    //--------------------------------------------------------------------------------------------------
    public FFTStatistic processInputStream(boolean fullInfo, EMParams set, Artifact art, String title){
        return processInputStream(fullInfo,set,art,title,0,0,0);
        }
    public FFTStatistic processInputStream(boolean fullInfo, EMParams set, Artifact art, String title, double startDiff, double startLevel, int skipPeaks){
        final String[] mes = {""};
        FileDescription fd = new FileDescription(art.getOriginalName());
        String error = fd.getFormatError();
        if (error.length()!=0)
            return new FFTStatistic(title,false,error);
        String ss = db.dataServerFileDir()+"/"+art.createArtifactServerPath();
        BufferedReader reader = null;
        FFTAudioTextFile file = new FFTAudioTextFile();
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(ss), "Windows-1251"));
            } catch (IOException ex){
                return new FFTStatistic(title,false,"Ошибка: "+ex.toString());
                }
            file.readData(fd, reader,false);
            if (startDiff!=0)
                file.squeezy(startDiff,startLevel,skipPeaks);
            error = fd.getFormatError();
            if (error.length() != 0) {
                return new FFTStatistic(title,false,error);
                }
        file.removeTrend(set.nTrendPoints);
        long lnt = file.getFrameLength();
        FFTParams params = new FFTParams().W(set.p_BlockSize* FFT.Size0).procOver(set.p_OverProc).
                compressMode(false).winMode(set.winFun).freqHZ(fd.getFileFreq());
        FFT fft = new FFT();
        fft.setFFTParams(params);
        fft.calcFFTParams();
        if (fullInfo){
            mes[0] +="Отсчетов: "+file.getFrameLength()+"\n";
            mes[0] +="Кадр: "+set.p_BlockSize*FFT.Size0+"\n";
            mes[0] +="Перекрытие: "+set.p_OverProc+"\n";
            mes[0] +="Дискретность: "+String.format("%5.4f",fft.getStepHZLinear())+" гц\n";
            }
        //-----------------------------------------------------------
        FFTStatistic inputStat = new FFTStatistic(title);
        inputStat.setFreq(fd.getFileFreq());
        final boolean[] error2 = {false};
        FFTCallBack adapter = new FFTCallBack() {
            @Override
            public void onStart(double stepMS) {}
            @Override
            public void onFinish() {
                if (inputStat.getCount()==0){
                    mes[0] +=("Настройки: короткий период измерений/много блоков");
                    error2[0] =true;
                    return;
                    }
                inputStat.smooth(set.kSmooth);
                }
            @Override
            public boolean onStep(int nBlock, int calcMS, double totalMS, FFT fft) {
                inputStat.setFreqStep(fft.getStepHZLinear());
                long tt = System.currentTimeMillis();
                double lineSpectrum[] = fft.getSpectrum();
                boolean xx;
                try {
                    inputStat.addStatistic(lineSpectrum);
                    } catch (Exception ex) {
                        mes[0] +=createFatalMessage(ex,10);
                        error2[0] =true;
                        return false;
                        }
                return true;
                }
            @Override
            public void onMessage(String mes0) {
                mes[0] +=mes0+"\n";
                }
            @Override
            public void onError(Exception ee) {
                mes[0] +=createFatalMessage(ee,10);
                error2[0] =true;
                }
            };
        fft.fftDirect(file,adapter);
        inputStat.setValid(!error2[0]);
        inputStat.setMessage(mes[0]);
        return inputStat;
        }
     */
}


