package filesync;

import java.net.*;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

class SyncServerThread extends Thread {
	
	/**
	 * Declarar las variable necesarias para establecer
	 * la comunicación con el cliente. Será necesario un socket
	 * de servidor y uno de cliente para escuchar y recibir los 
	 * mensajes.
	 */
	
	private static InstructionFactory instFact = new InstructionFactory();
	private static SynchronisedFile file;
	private static String filename;
	private static Boolean newNegotiation = true;
        private static Boolean newSincroniz;
	private static String action = "";
	private static int blockSize;
        private static Socket clientSocket;
        private static DataInputStream in;
        private static DataOutputStream out;
	
	public SyncServerThread ( String fn,Socket cS) {
                filename = fn;
		clientSocket=cS;           
                newSincroniz=true;
               
            try {
                in = new DataInputStream(clientSocket.getInputStream());
                out = new DataOutputStream(clientSocket.getOutputStream());
                
            } catch (IOException ex) {
                Logger.getLogger(SyncServerThread.class.getName()).log(Level.SEVERE, null, ex);
            }  
	}
	
	public void run() {
		int i = 0;
                String data;
		while (newSincroniz){
			try { // an echo server
                            
				if( newNegotiation ) {
					newNegotiation = false;
                                        
					/**
					 * Para una nueva negociación el servidor debe 
					 * escuchar peticiones y aceptar las peticiones 
					 * entrantes.
					 * Luego debe instanciar los objetos para poder 
					 * leer y escribir en el stream.
					 */
					System.out.println( "Server listening for data" );
										
					System.out.println( "Received data " + ++i );
					String resp="FAIL";                                        
                                        while(!resp.equals("OK")){
                                            System.out.println("rest"+resp);
                                            action = in.readUTF(); // read a line of data from the stream
                                            System.out.println("Recibido: " + action);                                          
                                            if(action.equals("commit") || action.equals("update")){
                                                resp="OK";
                                            }
                                            out.writeUTF(resp);
                                        }
                                        
                                        resp="FAIL";
                                        while(!resp.equals("OK")){
                                            try {   
                                                blockSize = Integer.parseInt( in.readUTF()); // read a line of data from the stream
                                                System.out.println("Recibido: " + blockSize);                                          
                                                resp="OK";
                                                out.writeUTF(resp);
                                            } catch (IOException e){
                                                System.out.println("Could not receive action confirmation from server: " + e.getMessage());
                                                e.printStackTrace();
                                               System.exit(-1);
                                            }
                                            
                                        }
                                        
                                        
                                            
                                        //inicio del archivo a sincronizar
                                        file = new SynchronisedFile( filename, blockSize );
					
					/**
					 * El primer mesaje que debe recibir es la accion 
					 * a realizar "commit" o "update"
					 * Si la accion es válida entonces debe enviar un
					 * acuso de recibo "OK"
					 * Y esperar por el siguiente mensaje que es el 
					 * tamaño del bloque y enviar el correspondiente
					 * acuso de recibo. 
					 * Luego debe inicializar el objeto 
					 * SynchronisedFile con los datos recibidos:					 
					 * 
					 * Si la acción no es válida el servidor debe 
					 * mostrar el mensaje respectivo y terminar la 
					 * conexión.
					 * 
					 */
                                        System.out.println("Action received: " + action);
                                        System.out.println("Server reading data");	
                                         switch( action ){
                                                case "commit":    
                                                        while(!newNegotiation){
                                                            String msg = in.readUTF(); // read a line of data from the stream
                                                            System.out.println("Recibido: " + msg);                                          
                                                            actAsReceiver(msg);        
                                                        }                                                            
                                                        
                                                        break;
                                                case "update":
                                                    actAsSender();
                                                    break;
                                                default:
                                                       System.out.println( "Invalid action. Usage: java -jar syncclient.jar hostname filename (commit | update) blocksize" );
                                                        System.exit(-1);
                                        }
						
					
					
				}
				
				/**
				 * Contrario al cliente, si la acción es 
				 * "commit" el servidor actua como Receptor
				 * "update" el servidor actua como Emisor
				 */
				
			} catch (EOFException e){
				System.out.println("EOF: " + e.getMessage());
				System.exit(-1);
			} catch(IOException e) {
				System.out.println("readline: " + e.getMessage());
				System.exit(-1);
			}
		}
	}
	
	private static void actAsReceiver(String msg){
		try {
			/*
			 * El servidor recibe la instrucción la cual debe ser
			 * desempaquetada antes de ser procesada.
			 * metodo FromJSON de la clase InstructionFactory
			 */                        
			Instruction receivedInst ;                        
                        InstructionFactory instFact=new InstructionFactory();                          
                        receivedInst = instFact.FromJSON(msg);                            
                        System.out.println("pakREc"+receivedInst);
			try {
                            file.ProcessInstruction(receivedInst);                                
                            System.out.println("servidor cambaiandotexto");
			} catch ( IOException e ) {
				System.out.println( e.getMessage() );
				System.exit( -1 ); // just die at the first sign of trouble
			} catch ( BlockUnavailableException e ) {
				// The server does not have the bytes referred to by the block hash.
				try {
					/**
					 * Si se lanza esta excepción quiere decir que
					 * el servidor no tiene los bytes a los que 
					 * hace referencia el bloque hash recibido.
					 * Por lo tanto el servidor debe enviar una 
					 * petición al cliente para que le sean 
					 * enviados los bytes reales contenidos en el
					 * bloque.
					 */
                                        out.writeUTF("NEW");
					System.out.println( "Server requesting NEW" );

					/*
					 * El servidor recibe el nuevo bloque de bytes
					 * los cuales deben ser desempaquetados antes
					 * de ser procesados.
					 * Utilizar el metodo fromJSON de la clase
					 * InstructionFactory
					 */
					
					String msg2=in.readUTF();
					Instruction receivedInst2 =instFact.FromJSON(msg2);
					file.ProcessInstruction( receivedInst2 );
                                       // System.out.println("srn:"+receivedInst2.ToJSON());
                                        System.out.println("Server reading NEW luego");
                                        
				} catch (IOException e1) {
					System.out.println( e1.getMessage() );
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
			System.out.println( "Server writing OK" );
			
                        System.out.println("intruccion servidor:"+ receivedInst.Type());
			//finalise sync
			if( receivedInst.Type().equals("EndUpdate")  ) {
				System.out.println("Sync finalised.");
				newNegotiation = true; // la sincronización se ha finalizado, entonces la siguiente será una nueva negociación de parametros
				action = "";
				blockSize = 0;
                                newSincroniz=false;
			}
		} catch (IOException ex) {
			System.out.println(ex.getMessage());
			System.exit(-1);
		}
	}
	
	private static void actAsSender() {
		// arguments supply hostname filename
                String reply = "";
		Instruction inst;
		
		try {
			System.out.println("SyncServer: calling fromFile.CheckFileState()");
			file.CheckFileState();
		} catch (IOException ex) {
			System.out.println(ex.getMessage());
			System.exit(-1);
		} catch (InterruptedException ex) {
			System.out.println(ex.getMessage());
			System.exit(-1);
		}
		
		// The server reads instructions to send to the client
		while( ( inst = file.NextInstruction() ) != null ){
			/**
			 * El servidor envia las instrucciones de sincronización 
			 * hacia el cliente
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

				/** Si el servidor envia como respuesta "NEW", quiere
				 * decir que existe un cambio en el archivo y por lo
				 * tanto el cliente debe cambiar el CopyBlock por un 
				 * NewBlock
				 */ 
				if( reply.equals( "NEW" ) ) {
					/*
					 * El servidor cambia la instrucción CopyBlock por 
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
			}catch (UnknownHostException e) {
				System.out.println( "Socket: " + e.getMessage() );
				System.exit(-1);
			}catch (EOFException e){
				System.out.println( "EOF: " + e.getMessage() );
				System.exit(-1);
			}catch (IOException e){
				System.out.println( "readline: " + e.getMessage() );
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
			
			//finalise sync
			if( inst.Type().equals("EndUpdate")  ) {
				System.out.println("Sync finalised.");
				newNegotiation = true;// se puede empezar una nueva negociación de parámetros.
                                newSincroniz=false;
				action = "";
				blockSize = 0;
				break;
			}
		}
	}
}