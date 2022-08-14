package romanow.abc.dataserver;

import romanow.abc.core.API.RestAPIBase;
import romanow.abc.core.I_EmptyEvent;
import romanow.abc.core.I_String;
import romanow.abc.core.LogStream;
import romanow.abc.core.ServerState;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import romanow.abc.core.constants.Values;
import romanow.abc.core.constants.ValuesBase;

import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

public class PRSConsoleServer {
    protected I_DBTarget dbTarget;
    protected Class apiFace;
    private int lineCount=0;
    private String gblEncoding="";
    private boolean utf8;
    private PRSDataServer dataServer = new PRSDataServer();
    private I_ServerState serverBack = new I_ServerState() {
        @Override
        public void onStateChanged(ServerState serverState) {
            dataServer.delayInGUI(1,new Runnable() {
                public void run() {
                    System.out.println(serverState.toString());
                }
            });
        }
    };
    //---------------------------------------------------------------------
    private I_EmptyEvent asteriskBack = new I_EmptyEvent() {
        @Override
        public void onEvent() {
                System.out.println(""+dataServer.getServerState().getLastMailNumber());
        }
    };
    public PRSConsoleServer(){
        Values.init();
        dbTarget = new DBExample();
        apiFace = RestAPIBase.class;
        }
    public PRSConsoleServer(I_DBTarget target, Class apiFace0){
        apiFace = apiFace0;
        dbTarget = target;
        }
    private int port;
    public void setTarget(){
        Retrofit retrofit=null;
        RestAPIBase service=null;
        final OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .readTimeout(Values.HTTPTimeOut, TimeUnit.SECONDS)
                .connectTimeout(Values.HTTPTimeOut, TimeUnit.SECONDS)
                .build();
        retrofit = new Retrofit.Builder()
                .baseUrl("http://localhost:"+port)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        service = (RestAPIBase) retrofit.create(apiFace);
        dbTarget.createAll(service, Values.DebugTokenPass);
        }
    public void startServer(int port0,String init){
        port = port0;
        dataServer.startServer(port, Values.MongoDBType36, serverBack,(init.equals("target")));
        gblEncoding = System.getProperty("file.encoding");
        utf8 = gblEncoding.equals("UTF-8");
        asteriskBack.onEvent();
        final LogStream log = new LogStream(utf8, dataServer.getConsoleLog(),new I_String() {
            @Override
            public void onEvent(String ss) {
                dataServer.addToLog(ss);
                }
            });
        if (init.equals("target")){
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(10);
                        } catch (InterruptedException e) {}
                    setTarget();
                    System.setOut(new PrintStream(log));
                    System.setErr(new PrintStream(log));
                    }
                }).start();
            }
        else {
            System.setOut(new PrintStream(log));
            //System.setErr(new PrintStream(log));
            }
        }

    public static void main(String args[]) {
        Values.init();
         //----------------------------------------------------------------------------------
        if (args.length>4){
            System.out.println("Слишком много параметров -"+args.length);
            return;
            }
         String port = "4567";
         String init = "none";
         if (args.length>0)
            port = args[0];
         if (args.length>=2)
            init = args[1];
         System.out.println("Порт="+port);
         PRSConsoleServer server = new PRSConsoleServer();
         server.startServer(Integer.parseInt(port),init);
        if(args.length==4){
            ValuesBase.env().superUser().setLoginPhone(args[2]);
            ValuesBase.env().superUser().setPassword(args[3]);
            System.out.println(ValuesBase.env().superUser().getLoginPhone());
            }
         }
    }
