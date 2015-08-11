package es.us.isa.ideas.controller.R;


import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.math.R.RserverConf;
import org.math.R.Rsession;
import org.rosuda.REngine.REXPGenericVector;
import org.rosuda.REngine.REXPMismatchException;

import es.us.isa.ideas.common.AppAnnotations;
import es.us.isa.ideas.common.AppResponse;
import es.us.isa.ideas.common.AppResponse.Status;


public class RDelegate {
	public final static String EXECUTE_SCRIPT = "executeScript";
	public final static String EXECUTE_SCRIPT2 = "executeScript2";
	public final static String LINT = "lint";
	public final static String END_SESSION= "endsession";
	public static final Object DELETE_TEMP = "deleteTemp";
	public  String tempD;
	String host="R://localhost:6311";
	//String Khost="R://localhost:6312";
	public Rsession s;
	public static Rsession copy;
	public PrintStream ps;
	public ByteArrayOutputStream baos;
	public Boolean isConnected=false;
	public Integer PID;
	
	
	
	public RDelegate(Rsession s, PrintStream ps, ByteArrayOutputStream baos) {
		super();
	if(s!=null){
		this.s = s;
		this.ps = ps;
		this.baos = baos;
	}else{
		try{
                RserverConf c= RserverConf.parse(host);
                this.baos = new ByteArrayOutputStream();
                 this.ps = new PrintStream(this.baos);
                this.s= Rsession.newInstanceTry(this.ps, c);
                 
       }catch(Exception e){
                System.out.println(e.getMessage());
                //TODO: hay que poner gestón de excepciones.
          }
	
		}
	copy=this.s;
	}
       public AppResponse endSession(){
            AppResponse res;
            
            if(PID.equals(-1)){
                res=endSession2();
            }else{
            	try{
              Rsession killer= Rsession.newInstanceTry(System.out,null);
              killer.eval("tools::pskill("+ this.PID + ")");
              killer.eval("tools::pskill("+ this.PID + ", tools::SIGKILL)");
//              Integer suicide=s.eval("Sys.getpid()").asInteger();
//             killer.eval("tools::pskill("+ suicide+ ", tools::SIGKILL)");
            
              killer.end();
              killer.connection.shutdown();
            
            	}catch(Exception e){
            		e.printStackTrace();
            	}
              res=endSession2();
              PID=-1;
            }
            return res;
        }
	public AppResponse endSession2(){
		AppResponse res= new AppResponse();
                                
		try{	
		this.s.rmAll();
		this.baos.reset();
		this.s.close();
		this.s.end();
		res.setMessage("Session correctly ended.");
		res.setStatus(Status.OK);
			
		}catch(Exception e){
			res.setMessage("Session couldn't be ended.");
			res.setStatus(Status.ERROR);
			e.printStackTrace();
			
		}
              
		return res;
	}
	public AppResponse executeScript(String content, String fileUri){
		AppResponse response= constructBaseResponse(fileUri);
		//Create the Temporary Directory
		WorkspaceSync ws= new WorkspaceSync(this.s);
		  tempD=ws.setTempDirectory();
		 if(tempD!=null){
		response.setMessage("executing R script");	
		 response.setContext(tempD);
		 }else{
			 response.setMessage("No se pudo crear el directorio temporal.");
			 response.setStatus(Status.ERROR);
		 }
	
						
		return response; 
	}
	public AppResponse executeScript2(String content, String fileUri){
		AppResponse response= constructBaseResponse(fileUri);
		try {
			//Execute Script 	
			this.PID= this.s.eval("Sys.getpid()").asInteger();
			baos.reset();
			s.eval(content);
			
			String f = baos.toString("UTF-8");
			
			String htmlMessage= "<pre>"+cleanMessage(f,content)+"</pre>";
			response.setHtmlMessage(htmlMessage);	
			
			//WorkspaceSync.endExecution(tempD, fileUri);
		} catch (Exception e) {
			System.out.println(e.getMessage());
			response.setMessage(e.getMessage());
			response.setStatus(Status.ERROR);
		}
		
		return response;
	}
        

		public AppResponse lintScript(String content, String fileUri){
		AppResponse response= constructBaseResponse(fileUri);
		
		try {
                    
                        File f=savecontentToTempFile(content);
			
                        if(s.isPackageInstalled("lintr","0.2.0")){
                            if(!s.isPackageLoaded("lintr"))
                               s.loadPackage("lintr");
                            String command="lintr::lint(\""+f.getAbsolutePath().replace("\\","\\\\\\\\")+"\")";
                            REXPGenericVector result=(REXPGenericVector)s.eval(command);                                                
                            response.setAnnotations(ErrorBuilder.buildErrorStructure(result.asList()));
                            if(result.length()==0){
                                response.setStatus(Status.OK);
                                response.setMessage("Everything is Ok!");
                            }else{
                                response.setStatus(Status.OK_PROBLEMS);
                                response.setMessage(String.valueOf(result.length())+" issues were found!");
                            }
                        }else{
                            response.setStatus(Status.OK_PROBLEMS);
                            response.setMessage("The required library 'lintr' is not installed in the backend R.");
                        }
                            
			
		} catch (Exception e) {
			System.out.println(e.getMessage());
			response.setMessage(e.getMessage());
			response.setStatus(Status.ERROR);
		}
		
		return response;
	}
	
  
	private AppResponse constructBaseResponse(String fileUri) {
		AppResponse appResponse = new AppResponse();
		appResponse.setFileUri(fileUri);
		appResponse.setStatus(Status.OK);
		return appResponse;
	}
	private File savecontentToTempFile(String content) throws IOException {        
        UUID uuid=UUID.randomUUID();
    //create a temp file
    File temp = File.createTempFile(uuid.toString(), ".tmp");

        try ( //write it
                BufferedWriter bw = new BufferedWriter(new FileWriter(temp))) {
            bw.write(content);
        }
        return temp;            
}
	/*private File savecontentToTempFile2(String content) throws IOException {        
        UUID uuid=UUID.randomUUID();
    //create a temp file
    File temp = File.createTempFile(uuid.toString(), ".R");

        try ( //write it
                BufferedWriter bw = new BufferedWriter(new FileWriter(temp))) {
            bw.write(content);
        }
        return temp;            
}*/
	private String cleanMessage(String f, String content) {
    	String f2=f.replace("[eval] "+content, "");
		f2=f2.replaceAll("(.eval).{1,}", "");
		String f3= f2.replaceFirst("(org).{1,}", "");
		f3=f3.replace("(!!) Rserve R://localhost:6311 is not accessible.\n ! null\r\nTrying to spawn R://localhost:6311\r\nEnvironment variables:\n  R_HOME=C:\\Program Files\\R\\R-3.2.1\\\r\nchecking Rserve is available... \r\n  ok\r\nstarting R daemon... R://localhost:6311\r\n  ok\r\nLocal Rserve started. (Version 103)\r\n", "");
		String f4=f3.replaceAll(".{1,}(org.rosuda).{1,}","" );
		f4=f4.replaceAll("(org.).{1,}", "");
	return f4;
}
	public AppResponse deleteTemp() {
		AppResponse response = new AppResponse(); 
		WorkspaceSync.deleteTemp(this.tempD);
		response.setStatus(Status.OK);
		response.setMessage("Execution finished");
		return response;
	}
	public static Rsession getSession(){
		//return this.s;
		return copy;
	}
	public static String[] getEnvironmentVariables(){
//		return this.s.ls();
		return copy.ls();
	}
	

}
