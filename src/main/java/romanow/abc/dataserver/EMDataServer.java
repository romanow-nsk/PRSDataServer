package romanow.abc.dataserver;

import romanow.abc.core.UniException;
import romanow.abc.core.constants.ValuesBase;
import romanow.abc.core.entity.subjectarea.WorkSettings;
import romanow.abc.core.utils.OwnDateTime;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import romanow.abc.core.utils.Pair;

import java.util.concurrent.TimeUnit;

// AJAX посылает post, а браузер - get
public class EMDataServer extends DataServer {
    public APIEM EMAPI = null;                       // API предметной области
    public ErrorCounter deviceErrors = new ErrorCounter();  // Счетчик повторных ошибок
    protected boolean shutdown=false;                       // Признак завершения работы
    private OwnDateTime lastDay = new OwnDateTime(false);// Время для фиксации смены дня
    //--------------------------------------------------------------------------------------------------------------
    public EMDataServer(){}
    //---------------------------------------------------------------------------------------------------------
    public Pair<APIEM,String> startSecondClient(String ip, String port, String token) throws Exception {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .readTimeout(ValuesBase.HTTPTimeOut, TimeUnit.SECONDS)
                .connectTimeout(ValuesBase.HTTPTimeOut, TimeUnit.SECONDS)
                .build();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://"+ip+":"+port)
                .addConverterFactory(GsonConverterFactory.create())
                .client(okHttpClient)
                .build();
        APIEM service = (APIEM)retrofit.create(APIEM.class);
        return new Pair<>(service,token);
        }
    //----------------------------  Счетчик ошибок ПЛК ----------------------------------
    public void onDeviceError(String mes){
        ErrorCounter rez =deviceErrors.onError(mes);
        }
    public void onDeviceSuccess(){
        ErrorCounter rez =deviceErrors.onSuccess();
        }
    public static void main(String argv[]){
        new EMDataServer();
        }
    //-----------------------------------------------------------------------------------------------------------------
    @Override
    public long createEvent(int type, int level, String title, String comment, long artId) {
        return 0;
        }

    @Override
    public void onClock() {
        WorkSettings ws;
        try {
            ws = (WorkSettings) common.getWorkSettings();
            } catch (UniException e) { return; }
        }

    @Override
    public void onStart() {
        EMAPI = new APIEM(this);
        }

    @Override
    public void onShutdown() {
        }
    }

