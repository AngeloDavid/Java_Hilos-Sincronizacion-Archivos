package filesync;

import filesync.BlockUnavailableException;
import filesync.CopyBlockInstruction;
import filesync.Instruction;
import filesync.InstructionFactory;
import filesync.NewBlockInstruction;
import filesync.SynchronisedFile;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;

/*
 * This thread provides comments to explain how the Client/Server 
 * architecture could be implemented that uses the file synchronisation protocol.
 */

public class SyncClientThread implements Runnable {

	/**
	 * Variables necesarias para la sincronización
	 */
	private static String host;
	private static String filename;
	private static String action;
	private static int blockSize;
	private static SynchronisedFile file;
	private static Socket s = null;
        private static DataInputStream in;
        private static DataOutputStream out;
        private static boolean newSincornacion;
        int serverPort;
	/**
	 * Declarar variables necesarias para la comunicación con 
	 * el servidor
	 * 
	 */
		
	SyncClientThread(String h, String fn, String a, int bs){
		host = h;
		filename = fn;
		action = a;
		blockSize = bs;
                serverPort= 7899;
                newSincornacion=true;
                try{
                s = new Socket(host, serverPort);
                in = new DataInputStream(s.getInputStream());
                out = new DataOutputStream(s.getOutputStream());				
                } catch (IOException e) {
//			System.out.println(ex.getMessage());
			e.printStackTrace();
			System.exit(-1);
		}
		/**
		 * Inicializar varibales para establecer comunicación con 
		 * el servidor
		 */
	}
	
	@Override
	public void run() {	
            try{
                // Instanciar objetos necesarios para leer y escribir en el stream
                String rest="FAIL";
                
                
                //Enviar Acccion y respuesta                
                System.out.println("Accion Enviada:"+action);
                while( !rest.equals( "OK" ) ) {

                out.writeUTF(action);
                System.out.println(action);
                rest= in.readUTF();
                System.out.println("Servidor:"+rest);
                if(rest.equals("FAIL")){
                    System.out.println("Accion incorrecta: ("+action+")");
                    System.exit(-1);
                }
                }
                System.out.println("Accion Confirmada.");

                //Enviar Tamanio
                System.out.println("BlockSize:"+blockSize);
                rest="Fail";
                while( !rest.equals( "OK" ) ) {
                out.writeUTF(""+blockSize);
                rest= in.readUTF();
                System.out.println("Servidor:"+rest);                            
                }
                
                System.out.println("BlockSize Confirmada.");

                /*
                * Initialise the SynchronisedFiles.
                */
                file = new SynchronisedFile( filename, blockSize );

                switch( action ){
                        case "commit":
                                actAsSender();                               
                                break;
                        case "update":
                            while(newSincornacion){
                                String msg = in.readUTF();
                                actAsReceiver(msg);
                            }                               
                                break;
                        default:
                                System.out.println( "Invalid action. Usage: java -jar syncclient.jar hostname filename (commit | update) blocksize" );
                                System.exit(-1);
                }
                } catch (IOException e) {
//			System.out.println(ex.getMessage());
			e.printStackTrace();
			System.exit(-1);
		}
		
	}
	
	private static void actAsSender() {
		Instruction inst;
		String reply = "";
		long startTime = System.currentTimeMillis();
		try {
			System.out.println("SyncClient: calling fromFile.CheckFileState()");
			file.CheckFileState();
		} catch (IOException e) {
//			System.out.println(ex.getMessage());
			e.printStackTrace();
			System.exit(-1);
		} catch (InterruptedException e) {
//			System.out.println(ex.getMessage());
			e.printStackTrace();
			System.exit(-1);
		}
		
		// The Client reads instructions to send to the Server
		while( (inst = file.NextInstruction()) != null ){
			
			/**
			 * El cliente envia las instrucciones de sincronización 
			 * hacia el servidor
			 * 
			 * Los mensajes deben ser empaquetados utilizando el 
			 * método ToJSON() dentro de la clase Instruction
			 */                        
			 try {                            
                            String msg = inst.ToJSON();
                            out.writeUTF(msg);
                            System.out.println( "Sending msg: " + msg );                            
                            reply= in.readUTF();			 	
                            
			  
			  
			    System.out.println( "Received: "+ reply );
             
				/** Si el cliente envia como respuesta "NEW", quiere
				 * decir que existe un cambio en el archivo y por lo
				 * tanto el cliente debe cambiar el CopyBlock por un 
				 * NewBlock
				 */ 
			 
				if( reply.equals( "NEW" ) ) {
					/*
					 * El cliente cambia la instrucción CopyBlock por 
					 * una NewBlock y lo envia.
					 * El mensaje debe ser empaquetado antes de 
					 * enviarse.
					 */
					Instruction upgraded = new NewBlockInstruction( ( CopyBlockInstruction ) inst );
					
					
					/**
					 * Enviar la la nueva instrucción al servidor 
					 * y recibir el acuso de recibo
					 */ 
                                        String msg2 = upgraded.ToJSON();
                                        out.writeUTF(msg2);
                                        System.out.println( "Sendingmg2: " + msg2 );                            
                                        reply= in.readUTF();			 						
                                        System.out.println( "Sending resp: " + reply );       
				}
			} catch (UnknownHostException e) {
				System.out.println("Socket:"+e.getMessage());
				e.printStackTrace();
				System.exit(-1);
			} catch (EOFException e){
				System.out.println("EOF:"+e.getMessage());
				e.printStackTrace();
				System.exit(-1);
			} catch (IOException e){
				System.out.println("readline: " + e.getMessage());
				e.printStackTrace();
				System.exit(-1);
			}
			

			/**
			 * Verificar que el acuso de recibo es OK y moverse a 
			 * la siguiente instrucción
			 */
			while( !reply.equals( "OK" ) ) {
                            System.out.println("while");
			}
			System.out.println("OK received. Move to the next instruction.");
                        System.out.println("tipo inst"+inst.Type());
			//finalise sync
			if( inst.Type().equals("EndUpdate")  ) {
                                newSincornacion=false;
				System.out.println("Sync finalised.");
				long finishTime = System.currentTimeMillis();
				System.out.println("Total time of Synchrohisation: " + (finishTime - startTime));
				System.exit(0);
			}
		}
	}
	
	private static void actAsReceiver(String msg) {
		long startTime = System.currentTimeMillis();
		//while(true) {
			try{
				/**
				 * La acción es "update" por lo tanto es el cliente
				 * quien recibirá los datos desde el servidor
				 */
				 System.out.println("Client reading data");
				
				/*
				 * El ciente recibe la instrucción aqui, la cual
				 * debe ser desempaquetada antes de ser procesada.
				 * Utilizar el metodo fromJSON de la clase
				 * InstrucionFactory
				 */
				InstructionFactory instFact = new InstructionFactory();
				Instruction receivedInst = instFact.FromJSON(msg);
                                System.out.println("String new: "+msg);
				try {
					// The client processes the instruction
					file.ProcessInstruction( receivedInst );
				} catch ( IOException e ) {
//					System.out.println( e.getMessage() );
					e.printStackTrace();
					System.exit(-1); // just die at the first sign of trouble
				} catch ( BlockUnavailableException e ) {
					// The client does not have the bytes referred to
					// by the block hash.
					try {
						/**
						 * Si se lanza esta excepción quiere decir que
						 * el cliente no tiene los bytes a los que 
						 * hace referencia el bloque hash recibido.
						 * Por lo tanto el cliente debe enviar una 
						 * petición al servidor para que le sean 
						 * enviados los bytes reales contenidos en el
						 * bloque.
						 */
                                                out.writeUTF("NEW");
						 System.out.println( "Client requesting NEW" );
                                                  

						/*
						 * El cliente recibe el nuevo bloque de bytes
						 * los cuales deben ser desempaquetados antes
						 * de ser procesados.
						 * Utilizar el metodo fromJSON de la clase
						 * InstructionFactory
						 */
						 System.out.println("Client reading NEW");                                                
						String msg2=in.readUTF();
                                                System.out.println("String new: "+msg2);
						Instruction receivedInst2 =instFact.FromJSON(msg2);
						file.ProcessInstruction( receivedInst2 );
					} catch (IOException e1) {
						System.out.println( e1.getMessage() );
						e.printStackTrace();
						System.exit(-1);
					} catch (BlockUnavailableException e1) {
						assert(false); // a NewBlockInstruction can never throw this exception
					}
				}
				/*
				 * Como estamos usando un protocolo 
				 * peticion-respuesta, el cliente debe enviar un
				 * acuso de recibo al servidor para indicar que 
				 * el bloque fue recibido correctamente y que la 
				 * siguiente instrucción puede ser enviada.
				 */
                                out.writeUTF("OK");
				System.out.println( "Client sending OK" );
				
				//finalise sync
				if( receivedInst.Type().equals("EndUpdate")  ) {
					System.out.println("Sync finalised.");
					long finishTime = System.currentTimeMillis();
					System.out.println("Total time of Synchrohisation: " + (finishTime - startTime));
					System.exit(0);
				}
			}catch (EOFException e){
				System.out.println( "EOF: " + e.getMessage() );
				e.printStackTrace();
				System.exit(-1);
			} catch(IOException e) {
				System.out.println( "readline: " + e.getMessage() );
				e.printStackTrace();
				System.exit(-1);
			}
//		}
	}
}
